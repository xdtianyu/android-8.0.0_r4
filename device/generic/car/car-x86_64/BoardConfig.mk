# BoardConfig.mk
#
# Product-specific compile-time definitions.
#

# same as x86 except HAL
include device/generic/x86_64/BoardConfig.mk

# Build OpenGLES emulation libraries
BUILD_EMULATOR_OPENGL := true
BUILD_EMULATOR_OPENGL_DRIVER := true

TARGET_USERIMAGES_USE_EXT4 := true
BOARD_SYSTEMIMAGE_PARTITION_SIZE := 3221225472
BOARD_USERDATAIMAGE_PARTITION_SIZE := 576716800
BOARD_CACHEIMAGE_PARTITION_SIZE := 69206016
BOARD_CACHEIMAGE_FILE_SYSTEM_TYPE := ext4
BOARD_FLASH_BLOCK_SIZE := 512
TARGET_USERIMAGES_SPARSE_EXT_DISABLED := true

BOARD_SEPOLICY_DIRS += \
     packages/services/Car/car_product/sepolicy \
     device/generic/car/common/sepolicy
