LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE:= libpdfiumfxge

LOCAL_ARM_MODE := arm
LOCAL_NDK_STL_VARIANT := gnustl_static

LOCAL_CFLAGS += -O3 -fstrict-aliasing -fprefetch-loop-arrays -fexceptions
LOCAL_CFLAGS += -Wno-non-virtual-dtor -Wall -DOPJ_STATIC \
                -DV8_DEPRECATION_WARNINGS -D_CRT_SECURE_NO_WARNINGS

# Mask some warnings. These are benign, but we probably want to fix them
# upstream at some point.
LOCAL_CFLAGS += -Wno-sign-compare -Wno-unused-parameter
LOCAL_CLANG_CFLAGS += -Wno-sign-compare -Wno-switch

LOCAL_SRC_FILES := \
    core/fxge/android/cfpf_skiadevicemodule.cpp \
    core/fxge/android/cfpf_skiafont.cpp \
    core/fxge/android/cfpf_skiafontmgr.cpp \
    core/fxge/android/cfx_androidfontinfo.cpp \
    core/fxge/android/fx_android_imp.cpp \
    core/fxge/dib/fx_dib_composite.cpp \
    core/fxge/dib/fx_dib_convert.cpp \
    core/fxge/dib/fx_dib_engine.cpp \
    core/fxge/dib/fx_dib_main.cpp \
    core/fxge/dib/fx_dib_transform.cpp \
    core/fxge/fontdata/chromefontdata/FoxitDingbats.cpp \
    core/fxge/fontdata/chromefontdata/FoxitFixed.cpp \
    core/fxge/fontdata/chromefontdata/FoxitFixedBold.cpp \
    core/fxge/fontdata/chromefontdata/FoxitFixedBoldItalic.cpp \
    core/fxge/fontdata/chromefontdata/FoxitFixedItalic.cpp \
    core/fxge/fontdata/chromefontdata/FoxitSans.cpp \
    core/fxge/fontdata/chromefontdata/FoxitSansBold.cpp \
    core/fxge/fontdata/chromefontdata/FoxitSansBoldItalic.cpp \
    core/fxge/fontdata/chromefontdata/FoxitSansItalic.cpp \
    core/fxge/fontdata/chromefontdata/FoxitSansMM.cpp \
    core/fxge/fontdata/chromefontdata/FoxitSerif.cpp \
    core/fxge/fontdata/chromefontdata/FoxitSerifBold.cpp \
    core/fxge/fontdata/chromefontdata/FoxitSerifBoldItalic.cpp \
    core/fxge/fontdata/chromefontdata/FoxitSerifItalic.cpp \
    core/fxge/fontdata/chromefontdata/FoxitSerifMM.cpp \
    core/fxge/fontdata/chromefontdata/FoxitSymbol.cpp \
    core/fxge/freetype/fx_freetype.cpp \
    core/fxge/ge/cfx_cliprgn.cpp \
    core/fxge/ge/cfx_facecache.cpp \
    core/fxge/ge/cfx_folderfontinfo.cpp \
    core/fxge/ge/cfx_font.cpp \
    core/fxge/ge/cfx_fontcache.cpp \
    core/fxge/ge/cfx_fontmapper.cpp \
    core/fxge/ge/cfx_fontmgr.cpp \
    core/fxge/ge/cfx_gemodule.cpp \
    core/fxge/ge/cfx_graphstate.cpp \
    core/fxge/ge/cfx_graphstatedata.cpp \
    core/fxge/ge/cfx_pathdata.cpp \
    core/fxge/ge/cfx_renderdevice.cpp \
    core/fxge/ge/cfx_substfont.cpp \
    core/fxge/ge/cfx_unicodeencoding.cpp \
    core/fxge/ge/cttfontdesc.cpp \
    core/fxge/ge/fx_ge_fontmap.cpp \
    core/fxge/ge/fx_ge_linux.cpp \
    core/fxge/ge/fx_ge_text.cpp \
    core/fxge/ifx_renderdevicedriver.cpp \
    core/fxge/agg/fx_agg_driver.cpp \

LOCAL_C_INCLUDES := \
    external/pdfium \
    external/freetype/include \
    external/freetype/include/freetype

include $(BUILD_STATIC_LIBRARY)
