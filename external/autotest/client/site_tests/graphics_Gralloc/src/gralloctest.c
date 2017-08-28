/*
 * Copyright 2016 The Chromium OS Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

#define _GNU_SOURCE
#include <stdio.h>
#include <stdlib.h>

#include <hardware/gralloc.h>
#include <system/graphics.h>
#include <cutils/native_handle.h>
#include "sw_sync.h"

#define ARRAY_SIZE(A) (sizeof(A)/sizeof(*(A)))

#define CHECK(cond) do {\
	if (!(cond)) {\
		printf("CHECK failed in %s() %s:%d\n", __func__, __FILE__, __LINE__);\
		return 0;\
	}\
} while(0)

#define CHECK_NO_MSG(cond) do {\
	if (!(cond)) {\
		return 0;\
	}\
} while(0)

/* Private API enumeration -- see <gralloc_drm.h> */
enum {
	GRALLOC_DRM_GET_STRIDE,
	GRALLOC_DRM_GET_FORMAT,
	GRALLOC_DRM_GET_DIMENSIONS,
};

/* See <system/graphics.h> for definitions. */
static const uint32_t format_list[] = {
	HAL_PIXEL_FORMAT_BGRA_8888,
	HAL_PIXEL_FORMAT_BLOB,
	HAL_PIXEL_FORMAT_FLEX_RGB_888,
	HAL_PIXEL_FORMAT_FLEX_RGBA_8888,
	HAL_PIXEL_FORMAT_IMPLEMENTATION_DEFINED,
	HAL_PIXEL_FORMAT_RAW10,
	HAL_PIXEL_FORMAT_RAW12,
	HAL_PIXEL_FORMAT_RAW16,
	HAL_PIXEL_FORMAT_RAW_OPAQUE,
	HAL_PIXEL_FORMAT_RGB_565,
	HAL_PIXEL_FORMAT_RGB_888,
	HAL_PIXEL_FORMAT_RGBA_8888,
	HAL_PIXEL_FORMAT_RGBX_8888,
	HAL_PIXEL_FORMAT_Y16,
	HAL_PIXEL_FORMAT_Y8,
	HAL_PIXEL_FORMAT_YCbCr_420_888,
	HAL_PIXEL_FORMAT_YCbCr_422_888,
	HAL_PIXEL_FORMAT_YCbCr_422_I,
	HAL_PIXEL_FORMAT_YCbCr_422_SP,
	HAL_PIXEL_FORMAT_YCbCr_444_888,
	HAL_PIXEL_FORMAT_YCrCb_420_SP,
	HAL_PIXEL_FORMAT_YV12,
};

/* See <hardware/gralloc.h> for descriptions. */
static const uint32_t usage_list[] = {
	GRALLOC_USAGE_CURSOR,
	GRALLOC_USAGE_HW_RENDER,
	GRALLOC_USAGE_HW_TEXTURE,
	GRALLOC_USAGE_SW_READ_OFTEN,
	GRALLOC_USAGE_SW_WRITE_OFTEN,
	GRALLOC_USAGE_SW_READ_RARELY,
	GRALLOC_USAGE_SW_WRITE_RARELY,
};

struct gralloctest {
	buffer_handle_t handle;       /* handle to the buffer */
	int w;                        /* width  of buffer */
	int h;                        /* height of buffer */
	int format;                   /* format of the buffer */
	int usage;                    /* bitfield indicating usage */
	int fence_fd;                 /* fence file descriptor */
	void *vaddr;                  /* buffer virtual memory address */
	int stride;                   /* stride in pixels */
	struct android_ycbcr ycbcr;   /* sw access for yuv buffers */
};

/* This function is meant to initialize the test to commonly used defaults. */
void gralloctest_init(struct gralloctest* test, int w, int h, int format,
		      int usage)
{
	test->w = w;
	test->h = h;
	test->format = format;
	test->usage = usage;
	test->fence_fd = -1;
	test->vaddr = NULL;
	test->ycbcr.y = NULL;
	test->ycbcr.cb = NULL;
	test->ycbcr.cr = NULL;
	test->stride = 0;
}

static native_handle_t *duplicate_buffer_handle(buffer_handle_t handle)
{
	native_handle_t *hnd =
		native_handle_create(handle->numFds, handle->numInts);

	if (hnd == NULL)
		return NULL;

	const int *old_data = handle->data;
	int *new_data = hnd->data;

	int i;
	for (i = 0; i < handle->numFds; i++) {
		*new_data = dup(*old_data);
		old_data++;
		new_data++;
	}

	memcpy(new_data, old_data, sizeof(int) * handle->numInts);

	return hnd;
}

/****************************************************************
 * Wrappers around gralloc_module_t and alloc_device_t functions.
 * GraphicBufferMapper/GraphicBufferAllocator could replace this
 * in theory.
 ***************************************************************/

static int allocate(struct alloc_device_t* device, struct gralloctest* test)
{
	int ret;

	ret = device->alloc(device, test->w, test->h, test->format, test->usage,
			    &test->handle, &test->stride);

	CHECK_NO_MSG(ret == 0);
	CHECK_NO_MSG(test->handle->version > 0);
	CHECK_NO_MSG(test->handle->numInts >= 0);
	CHECK_NO_MSG(test->handle->numFds >= 0);
	CHECK_NO_MSG(test->stride >= 0);

	return 1;
}

static int deallocate(struct alloc_device_t* device, struct gralloctest* test)
{
	int ret;
	ret = device->free(device, test->handle);
	CHECK(ret == 0);
	return 1;
}

static int register_buffer(struct gralloc_module_t* module,
			   struct gralloctest* test)
{
	int ret;
	ret = module->registerBuffer(module, test->handle);
	return (ret == 0);
}

static int unregister_buffer(struct gralloc_module_t* module,
			     struct gralloctest* test)
{
	int ret;
	ret = module->unregisterBuffer(module, test->handle);
	return (ret == 0);
}

static int lock(struct gralloc_module_t* module, struct gralloctest* test)
{
	int ret;

	ret = module->lock(module, test->handle, test->usage, 0, 0, (test->w)/2,
			(test->h)/2, &test->vaddr);

	return (ret == 0);
}

static int unlock(struct gralloc_module_t* module, struct gralloctest* test)
{
	int ret;
	ret = module->unlock(module, test->handle);
	return (ret == 0);
}

static int lock_ycbcr(struct gralloc_module_t* module, struct gralloctest* test)
{
	int ret;

	ret = module->lock_ycbcr(module, test->handle, test->usage, 0, 0,
			(test->w)/2, (test->h)/2, &test->ycbcr);

	return (ret == 0);
}

static int lock_async(struct gralloc_module_t* module, struct gralloctest* test)
{
	int ret;

	ret = module->lockAsync(module, test->handle, test->usage, 0, 0,
			(test->w)/2, (test->h)/2, &test->vaddr, test->fence_fd);

return (ret == 0);
}

static int unlock_async(struct gralloc_module_t* module,
			struct gralloctest* test)
{
	int ret;

	ret = module->unlockAsync(module, test->handle, &test->fence_fd);

	return (ret == 0);
}

static int lock_async_ycbcr(struct gralloc_module_t* module,
			    struct gralloctest* test)
{
	int ret;

	ret = module->lockAsync_ycbcr(module, test->handle, test->usage,
		0, 0, (test->w)/2, (test->h)/2, &test->ycbcr, test->fence_fd);

	return (ret == 0);
}

/**************************************************************
 * END WRAPPERS                                               *
 **************************************************************/

/* This function tests initialization of gralloc module and allocator. */
static int test_init_gralloc(gralloc_module_t** module, alloc_device_t** device)
{
	hw_module_t const* hw_module;
	int err;

	err = hw_get_module(GRALLOC_HARDWARE_MODULE_ID, &hw_module);
	CHECK(err == 0);

	gralloc_open(hw_module, device);
	*module = (gralloc_module_t *) hw_module;

	CHECK(*module);
	CHECK(*device);

	return 1;
}

static int test_close_allocator(alloc_device_t* device)
{
	CHECK(gralloc_close(device) == 0);
	return 1;
}

/* This function tests allocation with varying buffer dimensions. */
static int test_alloc_varying_sizes(struct alloc_device_t* device)
{
	struct gralloctest test;
	int i;

	gralloctest_init(&test, 0, 0, HAL_PIXEL_FORMAT_BGRA_8888,
		GRALLOC_USAGE_SW_READ_OFTEN);

	for (i = 1; i < 1920; i++) {
		test.w = i;
		test.h = i;
		CHECK(allocate(device, &test));
		CHECK(deallocate(device, &test));
	}

	test.w = 1;
	for (i = 1; i < 1920; i++) {
		test.h = i;
		CHECK(allocate(device, &test));
		CHECK(deallocate(device, &test));
	}

	test.h = 1;
	for (i = 1; i < 1920; i++) {
		test.w = i;
		CHECK(allocate(device, &test));
		CHECK(deallocate(device, &test));
	}

	return 1;
}

/*
 * This function tests that we find at least one working format for each
 * usage which we consider important.
 */
static int test_alloc_usage(struct alloc_device_t* device)
{
	int i, j;

	struct gralloctest test;
	gralloctest_init(&test, 512, 512, HAL_PIXEL_FORMAT_BGRA_8888,
		GRALLOC_USAGE_SW_READ_OFTEN);

	for (i = 0; i < ARRAY_SIZE(usage_list); i++) {
		test.usage = usage_list[i];
		int found = 0;
		for (j = 0; j < ARRAY_SIZE(format_list); j++) {
			test.format = format_list[j];
			if (allocate(device, &test))
				if (deallocate(device, &test))
					found = 1;
		}
		CHECK(found);
	}

	return 1;
}

/*
 * This function tests the advertised API version.
 * Version_0_2 added (*lock_ycbcr)() method.
 * Version_0_3 added fence passing to/from lock/unlock.
 */
static int test_api(struct gralloc_module_t* module)
{

	CHECK(module->registerBuffer);
	CHECK(module->unregisterBuffer);
	CHECK(module->lock);
	CHECK(module->unlock);

	switch (module->common.module_api_version) {
	case GRALLOC_MODULE_API_VERSION_0_3:
		CHECK(module->lock_ycbcr);
		CHECK(module->lockAsync);
		CHECK(module->unlockAsync);
		CHECK(module->lockAsync_ycbcr);
		break;
	case GRALLOC_MODULE_API_VERSION_0_2:
		CHECK(module->lock_ycbcr);
		CHECK(module->lockAsync == NULL);
		CHECK(module->unlockAsync == NULL);
		CHECK(module->lockAsync_ycbcr == NULL);
		 break;
	case GRALLOC_MODULE_API_VERSION_0_1:
		CHECK(module->lockAsync == NULL);
		CHECK(module->unlockAsync == NULL);
		CHECK(module->lockAsync_ycbcr == NULL);
		CHECK(module->lock_ycbcr == NULL);
		break;
	default:
		return 0;
	}

	return 1;
}

/*
 * This function registers, unregisters, locks and unlocks the buffer in
 * various orders.
 */
static int test_gralloc_order(struct gralloc_module_t* module,
			      struct alloc_device_t* device)
{
	struct gralloctest test, duplicate;

	gralloctest_init(&test, 512, 512, HAL_PIXEL_FORMAT_BGRA_8888,
		GRALLOC_USAGE_SW_READ_OFTEN);

	gralloctest_init(&duplicate, 512, 512, HAL_PIXEL_FORMAT_BGRA_8888,
		GRALLOC_USAGE_SW_READ_OFTEN);

	CHECK(allocate(device, &test));

	/*
	 * Duplicate the buffer handle to simulate an additional reference
	 * in same process.
	 */
	native_handle_t *native_handle = duplicate_buffer_handle(test.handle);
	duplicate.handle = native_handle;

	CHECK(unregister_buffer(module, &duplicate) == 0);
	CHECK(register_buffer(module, &duplicate));

	/* This should be a no-op when the buffer wasn't previously locked. */
	CHECK(unlock(module, &duplicate));

	CHECK(lock(module, &duplicate));
	CHECK(duplicate.vaddr);
	CHECK(unlock(module, &duplicate));

	CHECK(unregister_buffer(module, &duplicate));

	CHECK(register_buffer(module, &duplicate));
	CHECK(unregister_buffer(module, &duplicate));
	CHECK(unregister_buffer(module, &duplicate) == 0);

	CHECK(register_buffer(module, &duplicate));
	CHECK(deallocate(device, &test));

	CHECK(lock(module, &duplicate));
	CHECK(unlock(module, &duplicate));
	CHECK(unregister_buffer(module, &duplicate));

	CHECK(native_handle_close(duplicate.handle) == 0);
	CHECK(native_handle_delete(native_handle) == 0);

	return 1;
}

/* This function tests uninitialized buffer handles. */
static int test_uninitialized_handle(struct gralloc_module_t* module)
{
	struct gralloctest test;
	buffer_handle_t handle = (buffer_handle_t)(intptr_t)0xdeadbeef;

	gralloctest_init(&test, 512, 512, HAL_PIXEL_FORMAT_BGRA_8888,
		GRALLOC_USAGE_SW_READ_OFTEN);

	test.handle = handle;

	CHECK(register_buffer(module, &test) == 0);
	CHECK(lock(module, &test) == 0);
	CHECK(unlock(module, &test) == 0);
	CHECK(unregister_buffer(module, &test) == 0);

	return 1;
}

/* This function tests that deallocated buffer handles are invalid. */
static int test_freed_handle(struct gralloc_module_t* module,
			     struct alloc_device_t* device)
{
	struct gralloctest test;

	gralloctest_init(&test, 512, 512, HAL_PIXEL_FORMAT_BGRA_8888,
		GRALLOC_USAGE_SW_READ_OFTEN);

	CHECK(allocate(device, &test));
	CHECK(deallocate(device, &test));

	CHECK(lock(module, &test) == 0);
	CHECK(unlock(module, &test) == 0);

	return 1;
}

/* This function tests CPU reads and writes. */
static int test_mapping(struct gralloc_module_t* module,
			struct alloc_device_t* device)
{
	struct gralloctest test;
	uint32_t* ptr = NULL;
	uint32_t magic_number = 0x000ABBA;

	gralloctest_init(&test, 512, 512, HAL_PIXEL_FORMAT_BGRA_8888,
		GRALLOC_USAGE_SW_READ_OFTEN | GRALLOC_USAGE_SW_WRITE_OFTEN);

	CHECK(allocate(device, &test));
	CHECK(lock(module, &test));

	ptr = (uint32_t *) test.vaddr;
	CHECK(ptr);
	ptr[(test.w)/2] = magic_number;

	CHECK(unlock(module, &test));
	test.vaddr = NULL;
	ptr = NULL;

	CHECK(lock(module, &test));
	ptr = (uint32_t *) test.vaddr;
	CHECK(ptr);
	CHECK(ptr[test.w/2] == magic_number);

	CHECK(unlock(module, &test));
	CHECK(deallocate(device, &test));

	return 1;
}

/* This function tests the private API we use in ARC++ -- not part of official gralloc. */
static int test_perform(struct gralloc_module_t* module,
			struct alloc_device_t* device)
{
	struct gralloctest test;
	uint32_t stride, width, height;
	int32_t format;

	gralloctest_init(&test, 650, 408, HAL_PIXEL_FORMAT_BGRA_8888,
		GRALLOC_USAGE_SW_READ_OFTEN);

	CHECK(allocate(device, &test));

	CHECK(module->perform(module, GRALLOC_DRM_GET_STRIDE, test.handle,
			      &stride) == 0);
	CHECK(stride == test.stride);

	CHECK(module->perform(module, GRALLOC_DRM_GET_FORMAT, test.handle,
			      &format) == 0);
	CHECK(format == test.format);

	CHECK(module->perform(module, GRALLOC_DRM_GET_DIMENSIONS, test.handle,
			      &width, &height)== 0);
	CHECK(width == test.w);
	CHECK(height == test.h);

	CHECK(deallocate(device, &test));

	return 1;
}

/* This function tests that only YUV buffers work with *lock_ycbcr. */
static int test_ycbcr(struct gralloc_module_t* module,
                     struct alloc_device_t* device)

{
	struct gralloctest test;
	gralloctest_init(&test, 512, 512, HAL_PIXEL_FORMAT_YCbCr_420_888,
		GRALLOC_USAGE_SW_READ_OFTEN);

	CHECK(allocate(device, &test));

	CHECK(lock(module, &test) == 0);
	CHECK(lock_ycbcr(module, &test));
	CHECK(test.ycbcr.y);
	CHECK(test.ycbcr.cb);
	CHECK(test.ycbcr.cr);
	CHECK(unlock(module, &test));

	CHECK(deallocate(device, &test));

	test.format = HAL_PIXEL_FORMAT_BGRA_8888;
	CHECK(allocate(device, &test));

	CHECK(lock_ycbcr(module, &test) == 0);
	CHECK(lock(module, &test));
	CHECK(unlock(module, &test));

	CHECK(deallocate(device, &test));

	return 1;
}

/* This function tests asynchronous locking and unlocking of buffers. */
static int test_async(struct gralloc_module_t* module,
		      struct alloc_device_t* device)

{
	struct gralloctest rgba_test, ycbcr_test;
	int fd;

	gralloctest_init(&rgba_test, 512, 512,
		HAL_PIXEL_FORMAT_BGRA_8888, GRALLOC_USAGE_SW_READ_OFTEN);

	gralloctest_init(&ycbcr_test, 512, 512,
		HAL_PIXEL_FORMAT_YCbCr_420_888, GRALLOC_USAGE_SW_READ_OFTEN);

	fd = sw_sync_timeline_create();
	rgba_test.fence_fd = sw_sync_fence_create(fd, "fence", 1);
	ycbcr_test.fence_fd = sw_sync_fence_create(fd, "ycbcr_fence", 2);

	CHECK(allocate(device, &rgba_test));
	CHECK(allocate(device, &ycbcr_test));

	/*
	 * Buffer data should only be available after the fence has been
	 * signaled.
	 */
	CHECK(lock_async(module, &rgba_test));
	CHECK(lock_async_ycbcr(module, &ycbcr_test));

	CHECK(rgba_test.vaddr == NULL);
	CHECK(sw_sync_timeline_inc(fd, 1));
	CHECK(rgba_test.vaddr);
	CHECK(ycbcr_test.ycbcr.y == NULL);
	CHECK(ycbcr_test.ycbcr.cb == NULL);
	CHECK(ycbcr_test.ycbcr.cr == NULL);

	CHECK(sw_sync_timeline_inc(fd, 1));
	CHECK(ycbcr_test.ycbcr.y);
	CHECK(ycbcr_test.ycbcr.cb);
	CHECK(ycbcr_test.ycbcr.cr);

	/*
	 * Wait on the fence returned from unlock_async and check it doesn't
	 * return an error.
	 */
	CHECK(unlock_async(module, &rgba_test));
	CHECK(unlock_async(module, &ycbcr_test));

	CHECK(rgba_test.fence_fd > 0);
	CHECK(ycbcr_test.fence_fd > 0);
	CHECK(sync_wait(rgba_test.fence_fd, 10000) >= 0);
	CHECK(sync_wait(ycbcr_test.fence_fd, 10000) >= 0);

	CHECK(close(rgba_test.fence_fd) == 0);
	CHECK(close(ycbcr_test.fence_fd) == 0);

	CHECK(deallocate(device, &rgba_test));
	CHECK(deallocate(device, &ycbcr_test));

	close(fd);

	return 1;
}

static void print_help(const char* argv0)
{
	printf("usage: %s <test_name>\n\n", argv0);
	printf("A valid test is one the following:\n");
	printf("alloc_varying_sizes\nalloc_usage\napi\ngralloc_order\n");
	printf("uninitialized_handle\nfreed_handle\nmapping\nperform\n");
	printf("ycbcr\nasync\n");
}

int main(int argc, char *argv[])
{
	gralloc_module_t* module = NULL;
	alloc_device_t* device = NULL;

	setbuf(stdout, NULL);

	if (argc == 2) {
		char* name = argv[1];
		int api;

		if(!test_init_gralloc(&module, &device))
			goto fail;

		switch (module->common.module_api_version) {
		case GRALLOC_MODULE_API_VERSION_0_3:
			api = 3;
			break;
		case GRALLOC_MODULE_API_VERSION_0_2:
			api = 2;
			break;
		default:
			api = 1;
		}

		printf("[ RUN      ] gralloctest.%s\n", name);

		if (strcmp(name, "alloc_varying_sizes") == 0) {
			if (!test_alloc_varying_sizes(device))
				goto fail;
		} else if (strcmp(name, "alloc_usage") == 0) {
			if (!test_alloc_usage(device))
				goto fail;
		} else if (strcmp(name, "api") == 0) {
			if (!test_api(module))
				goto fail;
		} else if (strcmp(name, "gralloc_order") == 0) {
			if (!test_gralloc_order(module, device))
				goto fail;
		} else if (strcmp(name, "uninitialized_handle") == 0) {
			if (!test_uninitialized_handle(module))
				goto fail;
		} else if (strcmp(name, "freed_handle") == 0) {
			if (!test_freed_handle(module, device))
				goto fail;
		} else if (strcmp(name, "mapping") == 0) {
			if (!test_mapping(module, device))
				goto fail;
		} else if (strcmp(name, "perform") == 0) {
			if (!test_perform(module, device))
				goto fail;
		} else if (strcmp(name, "ycbcr") == 0) {
			if (api >= 2 && !test_ycbcr(module, device))
				goto fail;
		} else if (strcmp(name, "async") == 0) {
			if (api >= 3 && !test_async(module, device))
				goto fail;
		} else {
			print_help(argv[0]);
			goto fail;
		}

		if(!test_close_allocator(device))
			goto fail;

		printf("[  PASSED  ] gralloctest.%s\n", name);
		return 0;

		fail:
			printf("[  FAILED  ] gralloctest.%s\n", name);

	} else {
		print_help(argv[0]);
	}

	return 0;
}
