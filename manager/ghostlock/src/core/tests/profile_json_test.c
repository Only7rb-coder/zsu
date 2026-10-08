#include "offsets_json.h"

#include <stdint.h>
#include <stdio.h>
#include <string.h>

static uint64_t expected_phys_offset(const char *release) {
  if (strcmp(release, "5.15.41-android13-8-g8dc4c75ab7d8-ab1673212412") == 0)
    return 0x80000000ULL;
  if (strcmp(release, "6.1.145-android14-11-g9b69cc399ae1-ab14819715") == 0 ||
      strcmp(release, "6.1.157-android14-11-ga8b0b542991e-ab15601211") == 0 ||
      strcmp(release, "6.6.127-android15-8-gb947b5758b2a-ab15580855-4k") == 0 ||
      strcmp(release, "6.6.89-android15-8-g5a0ffb447c1d-ab13771415-4k") == 0)
    return 0x40000000ULL;
  return 0;
}

int main(int argc, char **argv) {
  if (argc < 2) {
    fputs("usage: profile_json_test <resolved-profile.json>...\n", stderr);
    return 2;
  }
  for (int i = 1; i < argc; ++i) {
    struct kernel_offsets profile = {0};
    char release[256] = {0};
    if (load_resolved_profile_json(argv[i], &profile, release,
                                   sizeof(release)) != 0) {
      fprintf(stderr, "failed to parse resolved profile: %s\n", argv[i]);
      return 1;
    }
    if (!release[0] || !profile.kernel_major || !profile.off_init_task ||
        !profile.off_init_cred || !profile.execution.heap_prepare_timeout_ms) {
      fprintf(stderr, "profile missing required data: %s\n", release);
      return 1;
    }
    uint64_t expected = expected_phys_offset(release);
    if (expected && profile.kernel_phys_offset != expected) {
      fprintf(stderr,
              "physical offset mismatch for %s: got 0x%llx expected 0x%llx\n",
              release, (unsigned long long)profile.kernel_phys_offset,
              (unsigned long long)expected);
      return 1;
    }
  }
  printf("native JSON profile loader passed for %d profiles\n", argc - 1);
  return 0;
}
