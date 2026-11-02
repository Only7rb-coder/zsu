#ifndef __KSU_H_UTIL
#define __KSU_H_UTIL

#include "linux/fdtable.h" // IWYU pragma: keep
#include <linux/version.h>
#include <linux/syscalls.h>
#include <linux/fcntl.h>
#include <linux/namei.h>
#include <linux/fs.h>
#include <linux/err.h>
#include <linux/cred.h>

#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 11, 0)
#define ksu_close_fd close_fd
#else
#define ksu_close_fd ksys_close
#endif

/*
 * Open a path without raising fsnotify (inotify/fanotify) events.
 *
 * A plain filp_open() reports FS_OPEN to watchers, and setting
 * FMODE_NONOTIFY on the file afterwards still leaks the open event itself.
 * Passing __FMODE_NONOTIFY to dentry_open() (dentry_open_nonotify() on
 * 6.14+) keeps the handle invisible to watchers for its whole lifetime.
 * Watchers on /data/app package directories are exactly how root detectors
 * (DuckDetector "throne hunt" and similar probes) observe our manager
 * search, so every path we touch during the scan must go through here.
 */
static inline struct file *ksu_filp_open_nonotify(const char *path, int flags)
{
	struct path p;
	struct file *f;
	int ret;

	ret = kern_path(path, (flags & O_NOFOLLOW) ? 0 : LOOKUP_FOLLOW, &p);
	if (ret)
		return ERR_PTR(ret);

#if LINUX_VERSION_CODE >= KERNEL_VERSION(6, 14, 0)
	f = dentry_open_nonotify(&p, flags, current_cred());
#else
	f = dentry_open(&p, flags | __FMODE_NONOTIFY, current_cred());
#endif

	path_put(&p);
	return f;
}

#endif
