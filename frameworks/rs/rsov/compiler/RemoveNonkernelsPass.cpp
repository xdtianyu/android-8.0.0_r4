/*
 * Copyright 2017, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "RemoveNonkernelsPass.h"

#include "llvm/IR/Constants.h"
#include "llvm/IR/Module.h"
#include "llvm/Pass.h"
#include "llvm/Support/Debug.h"
#include "llvm/Support/raw_ostream.h"

#include "Context.h"

#define DEBUG_TYPE "rs2spirv-remove"

using namespace llvm;

namespace rs2spirv {

namespace {

class RemoveNonkernelsPass : public ModulePass {
public:
  static char ID;
  explicit RemoveNonkernelsPass() : ModulePass(ID) {}

  const char *getPassName() const override { return "RemoveNonkernelsPass"; }

  bool runOnModule(Module &M) override {
    DEBUG(dbgs() << "RemoveNonkernelsPass\n");
    DEBUG(M.dump());

    rs2spirv::Context &Ctxt = rs2spirv::Context::getInstance();

    if (Ctxt.getNumForEachKernel() == 0) {
      DEBUG(dbgs() << "RemoveNonkernelsPass detected no kernel\n");
      // Returns false, since no modification is made to the Module.
      return false;
    }

    std::vector<Function *> Functions;
    for (auto &F : M.functions()) {
      Functions.push_back(&F);
    }

    for (auto &F : Functions) {
      if (F->isDeclaration())
        continue;

      if (Ctxt.isForEachKernel(F->getName())) {
        continue; // Skip kernels.
      }

      F->replaceAllUsesWith(UndefValue::get((Type *)F->getType()));
      F->eraseFromParent();

      DEBUG(dbgs() << "Removed:\t" << F->getName() << '\n');
    }

    DEBUG(M.dump());
    DEBUG(dbgs() << "Done removal\n");

    // Returns true, because the pass modifies the Module.
    return true;
  }
};

} // namespace

char RemoveNonkernelsPass::ID = 0;

ModulePass *createRemoveNonkernelsPass() {
  return new RemoveNonkernelsPass();
}

} // namespace rs2spirv
