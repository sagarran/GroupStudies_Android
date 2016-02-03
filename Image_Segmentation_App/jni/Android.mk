LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE    := Grabcut
LOCAL_SRC_FILES := Grabcut.cpp

include $(BUILD_SHARED_LIBRARY)
