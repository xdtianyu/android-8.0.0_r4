/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef ANDROID_HARDWARE_BINDER_KERNEL_H
#define ANDROID_HARDWARE_BINDER_KERNEL_H

#include <linux/android/binder.h>

/**
 * This file exists because the uapi kernel headers in bionic are built
 * from upstream kernel headers only, and the hwbinder kernel changes
 * haven't made it upstream yet. Therefore, the modifications to the
 * binder header are added locally in this file.
 */

enum {
	BINDER_TYPE_PTR		= B_PACK_CHARS('p', 't', '*', B_TYPE_LARGE),
	BINDER_TYPE_FDA		= B_PACK_CHARS('f', 'd', 'a', B_TYPE_LARGE),
};

/* This header is used in all binder objects that are fixed
 * up by the kernel driver */
struct binder_object_header {
	__u32        type;
};

struct binder_fd_object {
	struct binder_object_header	hdr;
	/* FD objects used to be represented in flat_binder_object as well,
	 * so we're using pads here to remain compatibile to existing userspace
	 * clients.
	 */
	__u32				pad_flags;
	union {
		binder_uintptr_t	pad_binder;
		__u32			fd;
	};

	binder_uintptr_t		cookie;
};

/* A binder_buffer object represents an object that the
 * binder kernel driver copies verbatim to the target
 * address space. A buffer itself may be pointed to from
 * within another buffer, meaning that the pointer inside
 * that other buffer needs to be fixed up as well. This
 * can be done by specifying the parent buffer, and the
 * byte offset at which the pointer lives in that buffer.
 */
struct binder_buffer_object {
	struct binder_object_header	hdr;
	__u32				flags;

	union {
		struct {
			binder_uintptr_t   buffer; /* Pointer to buffer data */
			binder_size_t      length; /* Length of the buffer data */
		};
		struct {
			binder_size_t      child;        /* index of child in objects array */
			binder_size_t      child_offset; /* byte offset in child buffer */
		};
	};
	binder_size_t			parent; /* index of parent in objects array */
	binder_size_t			parent_offset; /* byte offset of pointer in parent buffer */
};

enum {
	BINDER_BUFFER_HAS_PARENT   = 1U << 0,
	BINDER_BUFFER_REF          = 1U << 1,
};

/* A binder_fd_array object represents an array of file
 * descriptors embedded in a binder_buffer_object. The
 * kernel driver will fix up all file descriptors in
 * the parent buffer specified by parent and parent_offset
 */
struct binder_fd_array_object {
	struct binder_object_header	hdr;
	__u32			_pad; /* hdr is 4 bytes, ensure 8-byte alignment of next fields */
	binder_size_t		num_fds;
	binder_size_t		parent; /* index of parent in objects array */
	binder_size_t		parent_offset; /* offset of pointer in parent */
};

struct binder_transaction_data_sg {
    binder_transaction_data    tr; /* regular transaction data */
    binder_size_t              buffers_size; /* number of bytes of SG buffers */
};

enum {
	BC_TRANSACTION_SG = _IOW('c', 17, struct binder_transaction_data_sg),
	BC_REPLY_SG = _IOW('c', 18, struct binder_transaction_data_sg),
};

enum {
        FLAT_BINDER_FLAG_SCHEDPOLICY_MASK = 0x600,
        FLAT_BINDER_FLAG_SCHEDPOLICY_SHIFT = 9,
};


#endif // ANDROID_HARDWARE_BINDER_KERNEL_H
