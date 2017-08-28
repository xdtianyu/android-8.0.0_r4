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

#ifndef sw_MutexLock_hpp
#define sw_MutexLock_hpp

#include "Thread.hpp"

#ifdef __ANDROID__
/* Use an actual mutex on android. Since many processes may use switchshader
   at the same time it's best to just have the scheduler overhead. */
#include <pthread.h>

namespace sw
{
	class BackoffLock
	{
	public:
		BackoffLock()
		{
			pthread_mutex_init(&mutex, NULL);
		}
		~BackoffLock()
		{
			pthread_mutex_destroy(&mutex);
		}

		bool attemptLock()
		{
                	if (pthread_mutex_trylock(&mutex) == 0)
                        {
                        	return true;
                        }
			return false;
		}

		void lock()
		{
			pthread_mutex_lock(&mutex);
		}

		void unlock()
		{
			pthread_mutex_unlock(&mutex);
		}

	private:
		struct
		{
			// Ensure that the mutex variable is on its own 64-byte cache line to avoid false sharing
			// Padding must be public to avoid compiler warnings
			volatile int padding1[16];
			pthread_mutex_t mutex;
			volatile int padding2[15];
		};
	};
}

#else

namespace sw
{
	class BackoffLock
	{
	public:
		BackoffLock()
		{
			mutex = 0;
		}

		bool attemptLock()
		{
			if(!isLocked())
			{
				if(atomicExchange(&mutex, 1) == 0)
				{
					return true;
				}
			}

			return false;
		}

		void lock()
		{
			int backoff = 1;

			while(!attemptLock())
			{
				if(backoff <= 64)
				{
					for(int i = 0; i < backoff; i++)
					{
						nop();
						nop();
						nop();
						nop();
						nop();

						nop();
						nop();
						nop();
						nop();
						nop();

						nop();
						nop();
						nop();
						nop();
						nop();

						nop();
						nop();
						nop();
						nop();
						nop();

						nop();
						nop();
						nop();
						nop();
						nop();

						nop();
						nop();
						nop();
						nop();
						nop();

						nop();
						nop();
						nop();
						nop();
						nop();
					}

					backoff *= 2;
				}
				else
				{
					Thread::yield();

					backoff = 1;
				}
			};
		}

		void unlock()
		{
			mutex = 0;
		}

		bool isLocked()
		{
			return mutex != 0;
		}

	private:
		struct
		{
			// Ensure that the mutex variable is on its own 64-byte cache line to avoid false sharing
			// Padding must be public to avoid compiler warnings
			volatile int padding1[16];
			volatile int mutex;
			volatile int padding2[15];
		};
	};
}
#endif   // __android

#endif   // sw_MutexLock_hpp
