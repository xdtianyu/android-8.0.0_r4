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

/*
 * Format of the parsed workload files.
 * 1) Header
 * 2) Table of the entries, each entry describes 1 file
 * 3) Table of IO operations to perform on the files
 */

/*
 * The parsed workload file starts off with the header, which
 * contains the count of the total # of files that are operated on.
 * and the total number of IO operations.
 */
struct ioshark_header {
	int	num_files;
	int	num_io_operations;
};

/*
 * After the header, we have a table of #files entries. Each entry
 * in this table describes 1 file, indexed by fileno and with the
 * specified size.
 * Before the tests starts, these files are pre-created.
 */
struct ioshark_file_state {
	int	fileno;	/* 1..num_files, with files name ioshark.<fileno> */
	size_t	size;
	int	global_filename_ix;
};

enum file_op {
	IOSHARK_LSEEK = 0,
	IOSHARK_LLSEEK,
	IOSHARK_PREAD64,
	IOSHARK_PWRITE64,
	IOSHARK_READ,
	IOSHARK_WRITE,
	IOSHARK_MMAP,
	IOSHARK_MMAP2,
	IOSHARK_OPEN,
	IOSHARK_FSYNC,
	IOSHARK_FDATASYNC,
	IOSHARK_CLOSE,
	IOSHARK_MAPPED_PREAD,
	IOSHARK_MAPPED_PWRITE,
	IOSHARK_MAX_FILE_OP
};

/* mmap prot flags */
#define IOSHARK_PROT_READ	0x1
#define IOSHARK_PROT_WRITE	0x2

/*
 * Next we have the table of IO operatiosn to perform. Each
 * IO operation is described by this entry.
 */
struct ioshark_file_operation {
	/* delta us between previous file op and this */
	u_int64_t		delta_us;
	enum file_op		file_op;
	int			fileno;
	union {
		struct lseek_args {
#define lseek_offset	u.lseek_a.offset
#define lseek_action	u.lseek_a.action
			off_t	offset;
			int action;
		} lseek_a;
		struct prw_args {
#define prw_offset	u.prw_a.offset
#define prw_len		u.prw_a.len
			off_t	offset;
			size_t	len;
		} prw_a;
#define rw_len		u.rw_a.len
		struct rw_args {
			size_t	len;
		} rw_a;
#define mmap_offset	u.mmap_a.offset
#define mmap_len	u.mmap_a.len
#define mmap_prot	u.mmap_a.prot
		struct mmap_args {
			off_t	offset;
			size_t	len;
			int	prot;
	} mmap_a;
#define open_flags	u.open_a.flags
#define open_mode	u.open_a.mode
		struct open_args {
			int	flags;
			mode_t	mode;
		} open_a;
	} u;
};

#define MAX_IOSHARK_PATHLEN	512

/*
 * Global table of all fileames
 */
struct ioshark_filename_struct
{
	char path[MAX_IOSHARK_PATHLEN];
};
