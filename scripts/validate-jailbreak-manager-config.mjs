import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

const root = resolve(import.meta.dirname, '..');
const read = (path) => readFileSync(resolve(root, path), 'utf8');
const requireText = (source, expected, message) =>
  assert.ok(source.includes(expected), `${message}: missing ${expected}`);

const manifest = read('manager/app/src/main/AndroidManifest.xml');
requireText(manifest, 'android:zygotePreloadName="com.rifsxd.ksunext.jailbreak.JailbreakZygotePreload"', 'app zygote preload');
requireText(manifest, 'android:name=".jailbreak.JailbreakService"', 'isolated late-load service');
requireText(manifest, 'android:isolatedProcess="true"', 'isolated late-load service');
requireText(manifest, 'android:useAppZygote="true"', 'app-zygote late-load service');
requireText(manifest, 'android:name=".jailbreak.JailbreakBootReceiver"', 'automatic late-load receiver');
requireText(manifest, 'android:enabled="false"', 'automatic late-load receiver opt-in default');

const nativeBridge = read('manager/app/src/main/cpp/jni.cc');
requireText(nativeBridge, '"late-load", "--allow-shell", "--package-name"', 'supported late-load command invocation');
requireText(nativeBridge, 'Java_com_rifsxd_ksunext_jailbreak_JailbreakZygotePreload_forkDontCareAndExecLateLoad', 'zygote JNI bridge');

const modeHelper = read('manager/app/src/main/java/com/rifsxd/ksunext/jailbreak/JailbreakMode.kt');
requireText(modeHelper, 'getSelinuxEnforce() != false', 'permissive SELinux guard');
requireText(modeHelper, 'COMPONENT_ENABLED_STATE_DISABLED', 'automatic late-load opt-out');
requireText(modeHelper, 'COMPONENT_ENABLED_STATE_ENABLED', 'automatic late-load opt-in');

const home = read('manager/app/src/main/java/com/rifsxd/ksunext/ui/screen/Home.kt');
requireText(home, 'showJailbreakAction = jailbreakSupported', 'Home action visibility wiring');
requireText(home, 'R.string.home_jailbreak', 'Home jailbreak label');
requireText(home, 'getSelinuxEnforce() == false', 'Home permissive-state guard');

const settings = read('manager/app/src/main/java/com/rifsxd/ksunext/ui/screen/Settings.kt');
requireText(settings, 'Natives.isLateLoadMode', 'conditional Auto Jailbreak visibility');
requireText(settings, 'JailbreakMode.setAutoEnabled(context, enabled)', 'Auto Jailbreak persistence action');

console.log('Validated guarded late-load jailbreak configuration.');
