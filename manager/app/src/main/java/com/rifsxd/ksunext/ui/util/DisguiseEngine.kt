package com.rifsxd.ksunext.ui.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import com.rifsxd.ksunext.signing.ApkSignerV2
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.zip.CRC32
import java.util.zip.Deflater

/**
 * ZSU on-device APK disguise engine (Magisk "Hide the app" style).
 *
 * Repackages the currently installed manager APK with a user-chosen package
 * name, version name/code and launcher icon, then re-signs it with the
 * embedded zsu.keystore (v2-only) so the kernel still accepts it as manager.
 *
 * The binary-XML patch and zip rebuild algorithms were validated end-to-end
 * in the build pipeline against the exact production APK format.
 */
object DisguiseEngine {

    private const val CHUNK_STRING_POOL = 0x0001
    private const val CHUNK_START_TAG = 0x0102
    private const val CHUNK_END_DOC = 0x0003
    private const val UTF8_FLAG = 0x100

    data class Params(
        val packageName: String,
        val versionName: String,
        val versionCode: Long,
        val iconPng: ByteArray? // decoded user-picked image, null = keep current icon
    )

    class DisguiseException(message: String) : Exception(message)

    // ---------------------------------------------------------------------
    // Binary AndroidManifest.xml patcher (UTF-16 and UTF-8 string pools)
    // ---------------------------------------------------------------------

    private class Reader(val b: ByteArray) {
        var p = 0
        fun u16(): Int { val v = (b[p].toInt() and 0xFF) or ((b[p + 1].toInt() and 0xFF) shl 8); p += 2; return v }
        fun u32(): Long {
            val v = (b[p].toLong() and 0xFF) or
                    ((b[p + 1].toLong() and 0xFF) shl 8) or
                    ((b[p + 2].toLong() and 0xFF) shl 16) or
                    ((b[p + 3].toLong() and 0xFF) shl 24)
            p += 4; return v
        }
        fun u32i(): Int = u32().toInt()
        fun seek(pos: Int) { p = pos }
    }

    private fun patchAxml(xml: ByteArray, strMap: Map<String, String>, newVersionCode: Long?): ByteArray {
        val r = Reader(xml)
        r.seek(8)
        val ctype = r.u16(); r.u16(); val csize = r.u32i()
        if (ctype != CHUNK_STRING_POOL) throw DisguiseException("manifest: no string pool")
        val sc = r.u32i(); val sty = r.u32i(); val flags = r.u32i()
        val sstart = r.u32i(); val stystart = r.u32i()
        val utf8 = flags and UTF8_FLAG != 0
        val offsets = IntArray(sc) { r.u32i() }
        val dataBase = 8 + sstart

        fun readStr(i: Int): String {
            var off = dataBase + offsets[i]
            return if (utf8) {
                var u16len = xml[off].toInt() and 0xFF; off++
                if (u16len and 0x80 != 0) { u16len = ((u16len and 0x7F) shl 8) or (xml[off].toInt() and 0xFF); off++ }
                var u8len = xml[off].toInt() and 0xFF; off++
                if (u8len and 0x80 != 0) { u8len = ((u8len and 0x7F) shl 8) or (xml[off].toInt() and 0xFF); off++ }
                String(xml, off, u8len, Charsets.UTF_8)
            } else {
                var ln = (xml[off].toInt() and 0xFF) or ((xml[off + 1].toInt() and 0xFF) shl 8); off += 2
                if (ln and 0x8000 != 0) {
                    ln = ((ln and 0x7FFF) shl 16) or ((xml[off].toInt() and 0xFF) or ((xml[off + 1].toInt() and 0xFF) shl 8)); off += 2
                }
                String(xml, off, ln * 2, Charsets.UTF_16LE)
            }
        }

        val pool = Array(sc) { readStr(it) }
        val patched = xml.copyOf()

        // ---- patch versionCode typed int in <manifest> start tag ----
        if (newVersionCode != null) {
            val vcIdx = pool.indexOf("versionCode")
            if (vcIdx < 0) throw DisguiseException("manifest: versionCode name missing")
            var off = 8 + csize
            var done = false
            while (off + 8 <= patched.size && !done) {
                val rr = Reader(patched); rr.seek(off)
                val t = rr.u16(); rr.u16(); val cs = rr.u32i()
                if (t == CHUNK_END_DOC || cs <= 0) break
                if (t == CHUNK_START_TAG) {
                    val nameIdx = Reader(patched).apply { seek(off + 20) }.u32i()
                    if (pool[nameIdx] == "manifest") {
                        val ah = Reader(patched).apply { seek(off + 24) }
                        val astart = ah.u16(); val asize = ah.u16(); val acount = ah.u16()
                        val abase = off + 16 + astart
                        for (ai in 0 until acount) {
                            val aoff = abase + ai * asize
                            val aname = Reader(patched).apply { seek(aoff + 4) }.u32i()
                            if (aname == vcIdx) {
                                val wr = Reader(patched); wr.seek(aoff + 16)
                                val bb = ByteBuffer.allocate(4).order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(newVersionCode.toInt()).array()
                                System.arraycopy(bb, 0, patched, aoff + 16, 4)
                                done = true
                                break
                            }
                        }
                        if (!done) throw DisguiseException("manifest: versionCode attr missing")
                    }
                }
                off += cs
            }
            if (!done) throw DisguiseException("manifest: <manifest> tag missing")
        }

        // ---- rebuild string pool with replacements ----
        val newPool = pool.map { s ->
            var ns = s
            strMap.forEach { (old, new) -> if (old.isNotEmpty() && ns.contains(old)) ns = ns.replace(old, new) }
            ns
        }

        val strData = ByteArrayOutputStream()
        val newOffsets = IntArray(sc)
        for (i in 0 until sc) {
            newOffsets[i] = strData.size()
            if (utf8) {
                val enc = newPool[i].toByteArray(Charsets.UTF_8)
                writeU16Var(strData, newPool[i].length) // utf-16 length
                writeU16Var(strData, enc.size)          // utf-8 byte length
                strData.write(enc); strData.write(0)
            } else {
                val enc = newPool[i].toByteArray(Charsets.UTF_16LE)
                val ln = newPool[i].length
                if (ln > 0x7FFF) {
                    strData.write(byteArrayOf(((ln shr 16) or 0x8000).toByte(), ((ln shr 16) or 0x8000).ushr(8).toByte(),
                        (ln and 0xFFFF).toByte(), ((ln and 0xFFFF) ushr 8).toByte()))
                } else {
                    strData.write(byteArrayOf(ln.toByte(), (ln ushr 8).toByte()))
                }
                strData.write(enc); strData.write(0); strData.write(0)
            }
        }
        while (strData.size() % 4 != 0) strData.write(0)

        val headerSize = 28 + sc * 4
        val newPoolSize = headerSize + strData.size()
        val sizeDiff = newPoolSize - csize

        val out = ByteArrayOutputStream()
        out.write(patched, 0, 16) // xml header + pool type/hsize/csize (patched below)
        val hdr = ByteBuffer.allocate(20).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        hdr.putInt(sc); hdr.putInt(sty); hdr.putInt(flags); hdr.putInt(headerSize); hdr.putInt(if (sty > 0) stystart else 0)
        out.write(hdr.array())
        val offBuf = ByteBuffer.allocate(sc * 4).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        newOffsets.forEach { offBuf.putInt(it) }
        out.write(offBuf.array())
        out.write(strData.toByteArray())
        // fix chunk sizes
        val outArr = out.toByteArray()
        ByteBuffer.wrap(outArr).order(java.nio.ByteOrder.LITTLE_ENDIAN).apply {
            putInt(4, (Reader(patched).apply { seek(4) }.u32i()) + sizeDiff) // xml total size
            putInt(12, newPoolSize) // pool chunk size
        }
        val rest = ByteArrayOutputStream()
        rest.write(outArr)
        rest.write(patched, 8 + csize, patched.size - (8 + csize))
        return rest.toByteArray()
    }

    private fun writeU16Var(os: ByteArrayOutputStream, v: Int) {
        if (v > 0x7F) {
            os.write((v ushr 8) or 0x80); os.write(v and 0xFF)
        } else os.write(v)
    }

    // ---------------------------------------------------------------------
    // Zip rebuild preserving compression + 4-byte alignment of stored entries
    // ---------------------------------------------------------------------

    private class Entry(
        val name: String, val method: Int, val crc: Long, val csize: Long,
        val usize: Long, val lho: Long
    )

    private fun readCentralDirectory(apk: ByteArray): Pair<List<Entry>, ByteArray> {
        // find EOCD
        var i = apk.size - 22
        while (i >= 0 && !(apk[i] == 0x50.toByte() && apk[i + 1] == 0x4B.toByte() && apk[i + 2] == 0x05.toByte() && apk[i + 3] == 0x06.toByte())) i--
        if (i < 0) throw DisguiseException("zip: EOCD not found")
        val count = (apk[i + 10].toInt() and 0xFF) or ((apk[i + 11].toInt() and 0xFF) shl 8)
        val cdOff = ByteBuffer.wrap(apk, i + 16, 4).order(java.nio.ByteOrder.LITTLE_ENDIAN).int
        val list = ArrayList<Entry>(count)
        var p = cdOff
        repeat(count) {
            val bb = ByteBuffer.wrap(apk, p, 46).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            bb.int // sig
            bb.short; bb.short; bb.short
            val method = bb.short.toInt()
            bb.short; bb.short
            val crc = bb.int.toLong() and 0xFFFFFFFFL
            val csize = bb.int.toLong() and 0xFFFFFFFFL
            val usize = bb.int.toLong() and 0xFFFFFFFFL
            val nlen = bb.short.toInt(); val elen = bb.short.toInt(); val clen = bb.short.toInt()
            bb.short; bb.short; bb.int
            val lho = bb.int.toLong() and 0xFFFFFFFFL
            val name = String(apk, p + 46, nlen, Charsets.UTF_8)
            list.add(Entry(name, method, crc, csize, usize, lho))
            p += 46 + nlen + elen + clen
        }
        return list to apk
    }

    private fun rawData(apk: ByteArray, e: Entry): ByteArray {
        val bb = ByteBuffer.wrap(apk, e.lho.toInt(), 30).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        bb.position(26)
        val nlen = bb.short.toInt(); val elen = bb.short.toInt()
        val start = e.lho.toInt() + 30 + nlen + elen
        return apk.copyOfRange(start, start + e.csize.toInt())
    }

    private fun inflate(raw: ByteArray): ByteArray {
        val inf = java.util.zip.Inflater(true)
        inf.setInput(raw)
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (!inf.finished()) {
            val n = inf.inflate(buf)
            if (n == 0 && inf.needsInput()) break
            out.write(buf, 0, n)
        }
        inf.end()
        return out.toByteArray()
    }

    private fun deflateRaw(data: ByteArray): ByteArray {
        val def = Deflater(9, true)
        def.setInput(data); def.finish()
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (!def.finished()) out.write(buf, 0, def.deflate(buf))
        def.end()
        return out.toByteArray()
    }

    private const val METHOD_STORED = 0
    private const val METHOD_DEFLATED = 8

    private fun isIconEntry(name: String, data: ByteArray, iconInfo: IconPlan): ByteArray? {
        if (!name.startsWith("res/") || !name.endsWith(".png")) return null
        val bmp = try { BitmapFactory.decodeByteArray(data, 0, data.size) } catch (e: Exception) { null } ?: return null
        val w = bmp.width; val h = bmp.height
        if (w != h) return null
        return when (w) {
            48, 72, 96, 144, 192 -> iconInfo.sized(w)          // legacy launcher icon
            108, 162, 216, 324, 432 -> {                        // adaptive foreground / monochrome
                if (isColorful(bmp)) iconInfo.sized(w) else iconInfo.mono(w)
            }
            else -> null
        }
    }

    private fun isColorful(bmp: Bitmap): Boolean {
        // sample pixels: colorful icons (foreground) have saturated content, monochrome is white
        var sat = 0; var n = 0
        val step = maxOf(1, bmp.width / 24)
        var x = 0
        while (x < bmp.width) {
            var y = 0
            while (y < bmp.height) {
                val c = bmp.getPixel(x, y)
                if (Color.alpha(c) > 40) {
                    val mx = maxOf(Color.red(c), maxOf(Color.green(c), Color.blue(c)))
                    val mn = minOf(Color.red(c), minOf(Color.green(c), Color.blue(c)))
                    sat += mx - mn; n++
                }
                y += step
            }
            x += step
        }
        return n > 0 && sat / n > 20
    }

    private class IconPlan(png: ByteArray) {
        private val src = BitmapFactory.decodeByteArray(png, 0, png.size)
        fun sized(px: Int): ByteArray {
            val b = Bitmap.createScaledBitmap(src, px, px, true)
            val out = ByteArrayOutputStream(); b.compress(Bitmap.CompressFormat.PNG, 100, out)
            return out.toByteArray()
        }
        fun mono(px: Int): ByteArray {
            val b = Bitmap.createScaledBitmap(src, px, px, true)
            val white = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            val c = Canvas(white)
            c.drawColor(Color.TRANSPARENT)
            c.drawBitmap(b, 0f, 0f, null)
            // keep alpha, force white
            val pix = IntArray(px * px)
            white.getPixels(pix, 0, px, 0, 0, px, px)
            for (i in pix.indices) {
                val a = pix[i] ushr 24
                pix[i] = (a shl 24) or 0xFFFFFF
            }
            white.setPixels(pix, 0, px, 0, 0, px, px)
            val out = ByteArrayOutputStream(); white.compress(Bitmap.CompressFormat.PNG, 100, out)
            return out.toByteArray()
        }
    }

    // ---------------------------------------------------------------------
    // v2 signing (Magisk's ApkSignerV2, production-proven on-device)
    // ---------------------------------------------------------------------

    private fun signV2(context: Context, apk: ByteArray): ByteArray {
        val ks = KeyStore.getInstance("PKCS12")
        val pass = charArrayOf('F', 'm', 'Y', 'W', 'L', 'w', 'd', 'w', '3', 'D', 'd', 'J', 'y', 'G', 'i', 'E', 'd', '5', 'U', 'm', 'F', 'J', 'l', 'Q')
        context.assets.open("zsu.keystore").use { ks.load(it, pass) }
        val key = ks.getKey("zsu", pass) as PrivateKey
        val cert = ks.getCertificate("zsu") as X509Certificate
        val cfg = ApkSignerV2.SignerConfig()
        cfg.privateKey = key
        cfg.certificates = listOf(cert)
        cfg.signatureAlgorithms = listOf(0x0103) // RSA PKCS#1 v1.5 with SHA-256 content digest
        val chunks = ApkSignerV2.sign(ByteBuffer.wrap(apk), listOf(cfg))
        val out = ByteArrayOutputStream()
        for (c in chunks) out.write(c.array(), c.arrayOffset() + c.position(), c.remaining())
        return out.toByteArray()
    }

    // ---------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------

    fun disguise(context: Context, params: Params): File {
        val selfApk = File(context.applicationInfo.sourceDir)
        val apkBytes = selfApk.readBytes()

        val (entries, _) = readCentralDirectory(apkBytes)

        // current identity (runtime -> also correct for spoofed builds)
        val pm = context.packageManager
        val info = pm.getPackageInfo(context.packageName, 0)
        val curPkg = info.packageName
        @Suppress("DEPRECATION") val curVn = info.versionName ?: "15.0.000.2504111533"

        val strMap = mutableMapOf<String, String>()
        if (params.packageName != curPkg) strMap[curPkg] = params.packageName
        if (params.versionName.isNotBlank() && params.versionName != curVn) strMap[curVn] = params.versionName

        val iconPlan = params.iconPng?.let { IconPlan(it) }

        val out = ByteArrayOutputStream(apkBytes.size + 65536)
        val central = ArrayList<Long>() // parallel to written entries: lho
        val written = ArrayList<Entry>()

        for (e in entries) {
            var data = if (e.method == METHOD_STORED) rawData(apkBytes, e) else inflate(rawData(apkBytes, e))
            if (e.name == "AndroidManifest.xml") {
                data = patchAxml(data, strMap, params.versionCode)
            } else if (iconPlan != null) {
                isIconEntry(e.name, data, iconPlan)?.let { data = it }
            }

            val crc = CRC32().apply { update(data) }.value
            val method = e.method
            val cdata = if (method == METHOD_DEFLATED) deflateRaw(data) else data

            var extra = ByteArray(0)
            if (method == METHOD_STORED) {
                val mis = (out.size() + 30 + e.name.toByteArray(Charsets.UTF_8).size) % 4
                val pad = (4 - mis) % 4
                if (pad != 0) {
                    val total = pad + 4
                    val bb = ByteBuffer.allocate(4).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                    bb.putShort(0xFFFF.toShort()); bb.putShort((total - 4).toShort())
                    extra = bb.array() + ByteArray(total - 4)
                }
            }

            val nameB = e.name.toByteArray(Charsets.UTF_8)
            val lho = out.size().toLong()
            val lh = ByteBuffer.allocate(30).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            lh.putInt(0x04034B50); lh.putShort(20); lh.putShort(0); lh.putShort(method.toShort())
            lh.putShort(0); lh.putShort(0)
            lh.putInt(crc.toInt()); lh.putInt(cdata.size); lh.putInt(data.size)
            lh.putShort(nameB.size.toShort()); lh.putShort(extra.size.toShort())
            out.write(lh.array()); out.write(nameB); out.write(extra); out.write(cdata)
            written.add(Entry(e.name, method, crc, cdata.size.toLong(), data.size.toLong(), lho))
            central.add(lho)
        }

        val cdOff = out.size()
        for (e in written) {
            val nameB = e.name.toByteArray(Charsets.UTF_8)
            val ch = ByteBuffer.allocate(46).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            ch.putInt(0x02014B50); ch.putShort(20); ch.putShort(20); ch.putShort(0)
            ch.putShort(e.method.toShort()); ch.putShort(0); ch.putShort(0)
            ch.putInt(e.crc.toInt()); ch.putInt(e.csize.toInt()); ch.putInt(e.usize.toInt())
            ch.putShort(nameB.size.toShort()); ch.putShort(0); ch.putShort(0)
            ch.putShort(0); ch.putShort(0); ch.putInt(0); ch.putInt(e.lho.toInt())
            out.write(ch.array()); out.write(nameB)
        }
        val cdSize = out.size() - cdOff
        val eocd = ByteBuffer.allocate(22).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        eocd.putInt(0x06054B50); eocd.putShort(0); eocd.putShort(0)
        eocd.putShort(written.size.toShort()); eocd.putShort(written.size.toShort())
        eocd.putInt(cdSize); eocd.putInt(cdOff); eocd.putShort(0)
        out.write(eocd.array())

        val signed = signV2(context, out.toByteArray())
        val f = File(context.cacheDir, "disguised_${params.packageName}.apk")
        f.writeBytes(signed)
        return f
    }

    fun installViaRoot(apk: File): Boolean {
        val target = "/data/local/tmp/zsu_disguise.apk"
        val cmd = "cp '${apk.absolutePath}' $target && chmod 644 $target && pm install -r $target; rm -f $target"
        return try {
            com.topjohnwu.superuser.Shell.cmd(cmd).exec().isSuccess
        } catch (e: Exception) {
            false
        }
    }
}
