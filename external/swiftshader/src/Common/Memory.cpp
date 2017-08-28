// Copyright 2016 The SwiftShader Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#include "Memory.hpp"

#include "Types.hpp"
#include "Debug.hpp"

#if defined(_WIN32)
	#ifndef WIN32_LEAN_AND_MEAN
		#define WIN32_LEAN_AND_MEAN
	#endif
	#include <windows.h>
#else
	#include <sys/mman.h>
	#include <unistd.h>
#endif

#if defined(__ANDROID__) && defined(TAG_JIT_CODE_MEMORY)
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <sys/prctl.h>

// Defined in bionic/libc/private/bionic_prctl.h, but that's not available outside of bionic.
#define ANDROID_PR_SET_VMA           0x53564d41   // 'SVMA'
#define ANDROID_PR_SET_VMA_ANON_NAME 0
#endif

#include <memory.h>

#undef allocate
#undef deallocate
#undef allocateZero
#undef deallocateZero

namespace sw
{
size_t memoryPageSize()
{
	static int pageSize = 0;

	if(pageSize == 0)
	{
		#if defined(_WIN32)
			SYSTEM_INFO systemInfo;
			GetSystemInfo(&systemInfo);
			pageSize = systemInfo.dwPageSize;
		#else
			pageSize = sysconf(_SC_PAGESIZE);
		#endif
	}

	return pageSize;
}

struct Allocation
{
//	size_t bytes;
	unsigned char *block;
};

void *allocate(size_t bytes, size_t alignment)
{
	unsigned char *block = new unsigned char[bytes + sizeof(Allocation) + alignment];
	unsigned char *aligned = 0;

	if(block)
	{
		aligned = (unsigned char*)((uintptr_t)(block + sizeof(Allocation) + alignment - 1) & -(intptr_t)alignment);
		Allocation *allocation = (Allocation*)(aligned - sizeof(Allocation));

	//	allocation->bytes = bytes;
		allocation->block = block;
	}

	return aligned;
}

void *allocateZero(size_t bytes, size_t alignment)
{
	void *memory = allocate(bytes, alignment);

	if(memory)
	{
		memset(memory, 0, bytes);
	}

	return memory;
}

void deallocate(void *memory)
{
	if(memory)
	{
		unsigned char *aligned = (unsigned char*)memory;
		Allocation *allocation = (Allocation*)(aligned - sizeof(Allocation));

		delete[] allocation->block;
	}
}

void *allocateExecutable(size_t bytes)
{
	size_t pageSize = memoryPageSize();
	size_t roundedSize = (bytes + pageSize - 1) & ~(pageSize - 1);
	void *memory = allocate(roundedSize, pageSize);

	#if defined(__ANDROID__) && defined(TAG_JIT_CODE_MEMORY)
		if(memory)
		{
			char *name = nullptr;
			asprintf(&name, "ss_x_%p", memory);

			if(prctl(ANDROID_PR_SET_VMA, ANDROID_PR_SET_VMA_ANON_NAME, memory, roundedSize, name) == -1)
			{
				ALOGE("prctl failed %p 0x%zx (%s)", memory, roundedSize, strerror(errno));
				free(name);
			}
			// The kernel retains a reference to name, so don't free it.
		}
	#endif

	return memory;
}

void markExecutable(void *memory, size_t bytes)
{
	#if defined(_WIN32)
		unsigned long oldProtection;
		VirtualProtect(memory, bytes, PAGE_EXECUTE_READ, &oldProtection);
	#else
		mprotect(memory, bytes, PROT_READ | PROT_EXEC);
	#endif
}

void deallocateExecutable(void *memory, size_t bytes)
{
	#if defined(_WIN32)
		unsigned long oldProtection;
		VirtualProtect(memory, bytes, PAGE_READWRITE, &oldProtection);
	#else
		mprotect(memory, bytes, PROT_READ | PROT_WRITE);
	#endif

	deallocate(memory);
}
}
