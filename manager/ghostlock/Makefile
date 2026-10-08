API ?= 35

# Auto-detect NDK
ifeq ($(OS),Windows_NT)
  NDK_ROOT ?= $(subst \,/,$(firstword $(wildcard     $(subst \,/,$(LOCALAPPDATA))/Android/Sdk/ndk/*     $(subst \,/,$(ANDROID_HOME))/ndk/*     D:/AndroidSDK/ndk/*)))
  override NDK_ROOT := $(subst \,/,$(NDK_ROOT))
  PREBUILT := windows-x86_64
  CLANG_BASE := aarch64-linux-android$(API)-clang
  NDK_CC := $(NDK_ROOT)/toolchains/llvm/prebuilt/$(PREBUILT)/bin/$(CLANG_BASE).cmd
else
  NDK_ROOT ?= $(or $(ANDROID_NDK_HOME),$(ANDROID_NDK_ROOT))
  UNAME_S := $(shell uname -s)
  PREBUILT := $(if $(filter Darwin,$(UNAME_S)),darwin-x86_64,linux-x86_64)
  NDK_CC := $(NDK_ROOT)/toolchains/llvm/prebuilt/$(PREBUILT)/bin/aarch64-linux-android$(API)-clang
endif

SRCS := \
  src/core/main.c \
  src/core/address_space.c \
  src/core/heap_context.c \
  src/core/pi_race.c \
  src/core/route_controller.c \
  src/core/tcp_zerocopy_route.c \
  src/core/select_stack_route.c \
  src/core/multicast_waiter_route.c \
  src/core/payload_builder.c \
  src/core/runtime_config.c \
  src/core/offsets_json.c \
  src/core/util.c \
  src/core/fops.c

# Native interface and target headers also trigger a rebuild.
HDRS := $(wildcard src/core/*.h src/core/*/*.h)

# Device offsets are selected at runtime from uname -r.
TARGET_CONFIG ?= target.h

CFLAGS = -O2 -flto -Wall -Wno-unused-parameter -Wno-sign-compare -Wno-unused-function \
  -Isrc/core -DTARGET_CONFIG_H=\"$(TARGET_CONFIG)\"
LDFLAGS := -fPIE -pie -pthread -flto
HOST_CC ?= cc
HOST_TEST_DIR ?= /tmp/zsu-ghostlock-host-test

.PHONY: all clean product address-space-host-test profile-catalog-host-test

all: ghostlock

ghostlock: $(SRCS) $(HDRS)
	@echo "Using NDK compiler: $(NDK_CC)"
	@echo "Target config: $(TARGET_CONFIG)"
	$(NDK_CC) $(CFLAGS) $(LDFLAGS) $(filter %.c,$^) -o ghostlock

product: ghostlock
	@echo "=== ghostlock binary ready: ./ghostlock ==="
	@echo "构建 APK: .\gradlew.bat :app:assembleDebug"

address-space-host-test: $(HOST_TEST_DIR)/address_space_test
	$(HOST_TEST_DIR)/address_space_test

$(HOST_TEST_DIR)/address_space_test: src/core/tests/address_space_test.c src/core/address_space.c src/core/address_space.h src/core/profile.h src/core/target.h src/core/tests/stubs/sys/system_properties.h
	mkdir -p $(HOST_TEST_DIR)
	$(HOST_CC) -std=c11 -Wall -Wextra -Werror -Isrc/core/tests/stubs -Isrc/core $< src/core/address_space.c -o $@

profile-catalog-host-test: $(HOST_TEST_DIR)/profile_json_test
	python3 tools/validate_profile_catalog.py --assets ../app/src/main/assets/kernel_profiles --native-test $(HOST_TEST_DIR)/profile_json_test

$(HOST_TEST_DIR)/profile_json_test: src/core/tests/profile_json_test.c src/core/offsets_json.c src/core/offsets_json.h src/core/profile.h
	mkdir -p $(HOST_TEST_DIR)
	$(HOST_CC) -D_GNU_SOURCE -std=c11 -Wall -Wextra -Werror -Isrc/core src/core/tests/profile_json_test.c src/core/offsets_json.c -o $@

clean:
	rm -f ghostlock
