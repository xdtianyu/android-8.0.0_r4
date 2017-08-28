LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE:= libpdfiumformfiller

LOCAL_ARM_MODE := arm
LOCAL_NDK_STL_VARIANT := gnustl_static

LOCAL_CFLAGS += -O3 -fstrict-aliasing -fprefetch-loop-arrays -fexceptions
LOCAL_CFLAGS += -Wno-non-virtual-dtor -Wall -DOPJ_STATIC \
                -DV8_DEPRECATION_WARNINGS -D_CRT_SECURE_NO_WARNINGS

# Mask some warnings. These are benign, but we probably want to fix them
# upstream at some point.
LOCAL_CFLAGS += -Wno-sign-compare -Wno-unused-parameter
LOCAL_CLANG_CFLAGS += -Wno-sign-compare

LOCAL_SRC_FILES := \
    fpdfsdk/formfiller/cba_fontmap.cpp \
    fpdfsdk/formfiller/cffl_checkbox.cpp \
    fpdfsdk/formfiller/cffl_combobox.cpp \
    fpdfsdk/formfiller/cffl_formfiller.cpp \
    fpdfsdk/formfiller/cffl_interactiveformfiller.cpp \
    fpdfsdk/formfiller/cffl_listbox.cpp \
    fpdfsdk/formfiller/cffl_pushbutton.cpp \
    fpdfsdk/formfiller/cffl_radiobutton.cpp \
    fpdfsdk/formfiller/cffl_textfield.cpp \

LOCAL_C_INCLUDES := \
    external/pdfium \
    external/freetype/include \
    external/freetype/include/freetype

include $(BUILD_STATIC_LIBRARY)
