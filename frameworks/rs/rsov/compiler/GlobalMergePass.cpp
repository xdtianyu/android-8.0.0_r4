/*
 * Copyright 2016, The Android Open Source Project
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

#include "GlobalMergePass.h"

#include "llvm/IR/Constants.h"
#include "llvm/IR/DataLayout.h"
#include "llvm/IR/GlobalVariable.h"
#include "llvm/IR/Instructions.h"
#include "llvm/IR/Module.h"
#include "llvm/Pass.h"
#include "llvm/Support/Debug.h"
#include "llvm/Support/raw_ostream.h"

#include "RSAllocationUtils.h"

#define DEBUG_TYPE "rs2spirv-global-merge"

using namespace llvm;

namespace rs2spirv {

namespace {

class GlobalMergePass : public ModulePass {
public:
  static char ID;
  GlobalMergePass() : ModulePass(ID) {}
  const char *getPassName() const override { return "GlobalMergePass"; }

  bool runOnModule(Module &M) override {
    DEBUG(dbgs() << "RS2SPIRVGlobalMergePass\n");
    DEBUG(M.dump());

    const auto &DL = M.getDataLayout();
    SmallVector<GlobalVariable *, 8> Globals;
    const bool CollectRes = collectGlobals(M, Globals);
    if (!CollectRes)
      return false; // Module not modified.

    IntegerType *Int32Ty = Type::getInt32Ty(M.getContext());
    uint64_t MergedSize = 0;
    SmallVector<Type *, 8> Tys;
    Tys.reserve(Globals.size());

    for (auto *GV : Globals) {
      auto *Ty = GV->getValueType();
      MergedSize += DL.getTypeAllocSize(Ty);
      Tys.push_back(Ty);
    }

    auto *MergedTy = StructType::create(M.getContext(), "struct.__GPUBuffer");
    MergedTy->setBody(Tys, false);
    DEBUG(MergedTy->dump());
    auto *MergedGV =
        new GlobalVariable(M, MergedTy, false, GlobalValue::ExternalLinkage,
                           nullptr, "__GPUBlock");
    MergedGV->setInitializer(nullptr); // TODO: Emit initializers for CPU code.

    Value *Idx[2] = {ConstantInt::get(Int32Ty, 0), nullptr};

    for (size_t i = 0, e = Globals.size(); i != e; ++i) {
      auto *G = Globals[i];
      Idx[1] = ConstantInt::get(Int32Ty, i);

      // Keep users in a vector - they get implicitly removed
      // in the loop below, which would invalidate users() iterators.
      std::vector<User *> Users(G->user_begin(), G->user_end());
      for (auto *User : Users) {
        DEBUG(dbgs() << "User: ");
        DEBUG(User->dump());
        auto *Inst = dyn_cast<Instruction>(User);

        // TODO: Consider what should actually happen. Global variables can
        // appear in ConstantExprs, but this case requires fixing the LLVM-SPIRV
        // converter, which currently emits ill-formed SPIR-V code.
        if (!Inst) {
          errs() << "Found a global variable user that is not an Instruction\n";
          assert(false);
          return true; // Module may have been modified.
        }

        auto *GEP = GetElementPtrInst::CreateInBounds(MergedTy, MergedGV, Idx,
                                                      "gpu_gep", Inst);
        for (unsigned k = 0, k_e = User->getNumOperands(); k != k_e; ++k)
          if (User->getOperand(k) == G)
            User->setOperand(k, GEP);
      }

      // TODO: Investigate emitting a GlobalAlias for each global variable.
      G->eraseFromParent();
    }

    // Return true, as the pass modifies module.
    return true;
  }

private:
  bool collectGlobals(Module &M, SmallVectorImpl<GlobalVariable *> &Globals) {
    for (auto &GV : M.globals()) {
      // TODO: Rethink what should happen with global statics.
      if (GV.isDeclaration() || GV.isThreadLocal() || GV.hasSection())
        continue;

      if (isRSAllocation(GV))
        continue;

      DEBUG(GV.dump());
      auto *PT = cast<PointerType>(GV.getType());

      const unsigned AddressSpace = PT->getAddressSpace();
      if (AddressSpace != 0) {
        errs() << "Unknown address space! (" << AddressSpace
               << ")\nGlobalMergePass failed!\n";
        return false;
      }

      Globals.push_back(&GV);
    }

    return !Globals.empty();
  }
};
} // namespace

char GlobalMergePass::ID = 0;

ModulePass *createGlobalMergePass() { return new GlobalMergePass(); }

} // namespace rs2spirv
