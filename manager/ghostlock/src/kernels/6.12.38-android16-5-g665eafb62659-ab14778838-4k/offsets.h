/* UNVERIFIED TEST ALIAS: 6.12.38-android16-5-g665eafb62659-ab14778838-4k */
/* Reuses the existing 6.12.38 offsets from g844001fb8721. Validate on-device. */

OFFSETS_ENTRY(
    "6.12.38-android16-5-g665eafb62659-ab14778838-4k",
    STRUCT_OFFSETS_6_12,
    .pselect_waiter_shift = 0,
    .off_init_task = 0x0240cf00,
    .off_init_cred = 0x02422c70,
    .off_root_task_group = 0x0263d580,
    .off_selinux_enforcing = 0x026894d0,
    .off_selinux_blob_sizes = 0x018494e8,
    .off_security_hook_heads = 0x00000000,
    .off_slide_nfulnl_logger = 0x024021a0,
    .off_slide_boot_id = 0x026aa868,
    .off_slide_loggers_0_1 = 0x024020e8,
),

/* These offsets are copied from the existing 6.12.38 entry and are not
 * verified for this build hash. Replace with device-derived values if BL Root
 * fails or behaves unexpectedly on the target device. */
