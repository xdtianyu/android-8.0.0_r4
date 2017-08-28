BASE_PATH := $(call my-dir)

include $(BASE_PATH)/pdfiumfdrm.mk
include $(BASE_PATH)/pdfiumfpdfapi.mk
include $(BASE_PATH)/pdfiumfpdfdoc.mk
include $(BASE_PATH)/pdfiumfpdftext.mk
include $(BASE_PATH)/pdfiumfxcodec.mk
include $(BASE_PATH)/pdfiumfxcrt.mk
include $(BASE_PATH)/pdfiumfxge.mk
include $(BASE_PATH)/pdfiumjavascript.mk
include $(BASE_PATH)/pdfiumformfiller.mk
include $(BASE_PATH)/pdfiumfxedit.mk
include $(BASE_PATH)/pdfiumpdfwindow.mk
include $(BASE_PATH)/pdfium.mk

include $(BASE_PATH)/third_party/Android.mk
