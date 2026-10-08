#include "address_space.h"
#include "target.h"

#include <stdint.h>
#include <stdio.h>

int __system_property_get(const char *name, char *value) {
  (void)name;
  if (value) value[0] = '\0';
  return 0;
}

static int check_alias(uint64_t kernel_phys_load, uint64_t kernel_phys_offset,
                       uint64_t expected_delta) {
  struct kernel_offsets values = {
      .uname_r = "6.1.25-android14-11-test",
      .kernel_major = 6,
      .kernel_phys_load = kernel_phys_load,
      .kernel_phys_offset = kernel_phys_offset,
      .off_init_cred = 0x1234,
  };
  TargetProfile profile = target_profile_snapshot(&values);
  ResolvedAddresses addresses = {0};
  if (resolved_addresses_init_for_soc(&addresses, &profile, TARGET_SOC_QCOM)) {
    return 0;
  }
  if (addresses.kernel_phys_offset != (kernel_phys_offset ? kernel_phys_offset
                                                          : P0_PHYS_OFFSET)) {
    return 0;
  }
  uintptr_t image = (uintptr_t)(KIMAGE_TEXT_BASE + 0x5678ULL);
  uintptr_t expected = (uintptr_t)(P0_PAGE_OFFSET | expected_delta);
  return resolved_addresses_data_alias(&addresses, image) == expected;
}

int main(void) {
  if (!check_alias(0x40000000ULL, 0x40000000ULL, 0x5678ULL)) {
    fputs("profile-specific physical offset translation failed\n", stderr);
    return 1;
  }
  if (!check_alias(0xa8000000ULL, 0, 0x28005678ULL)) {
    fputs("legacy default physical offset translation failed\n", stderr);
    return 1;
  }
  struct kernel_offsets invalid_values = {
      .uname_r = "6.1.25-android14-11-test",
      .kernel_major = 6,
      .kernel_phys_load = 0x40000000ULL,
      .kernel_phys_offset = 0x80000000ULL,
      .off_init_cred = 0x1234,
  };
  TargetProfile invalid_profile = target_profile_snapshot(&invalid_values);
  ResolvedAddresses invalid_addresses = {0};
  if (resolved_addresses_init_for_soc(&invalid_addresses, &invalid_profile,
                                      TARGET_SOC_QCOM) == 0) {
    fputs("invalid physical mapping was accepted\n", stderr);
    return 1;
  }
  puts("address-space physical offset tests passed");
  return 0;
}
