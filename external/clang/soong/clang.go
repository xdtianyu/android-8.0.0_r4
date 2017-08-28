// Copyright (C) 2016 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package clang

import (
	"android/soong/android"
	"android/soong/cc"

	"github.com/google/blueprint"
)

// Clang binaries (clang, clang-check, clang-format) need to be compiled for both 32-bit and 64-bit,
// but only when FORCE_BUILD_LLVM_COMPONENTS is set

func init() {
	android.RegisterModuleType("clang_binary_host", clangBinaryHostFactory)
}

func clangForceBuildLlvmComponents(ctx android.LoadHookContext) {
	if ctx.AConfig().IsEnvTrue("FORCE_BUILD_LLVM_COMPONENTS") {
		type props struct {
			Target struct {
				Host struct {
					Compile_multilib string
				}
			}
			Multilib struct {
				Lib32 struct {
					Suffix string
				}
			}
		}
		p := &props{}
		p.Target.Host.Compile_multilib = "both"
		p.Multilib.Lib32.Suffix = "_32"
		ctx.AppendProperties(p)
	}
}

func clangBinaryHostFactory() (blueprint.Module, []interface{}) {
	module, _ := cc.NewBinary(android.HostSupported)
	android.AddLoadHook(module, clangForceBuildLlvmComponents)

	return module.Init()
}
