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

#include "rsovScript.h"

#include "bcinfo/MetadataExtractor.h"
#include "rsContext.h"
#include "rsDefines.h"
#include "rsType.h"
#include "rsUtils.h"
#include "rsovAllocation.h"
#include "rsovContext.h"
#include "rsovCore.h"
#include "spirit/instructions.h"
#include "spirit/module.h"

#include <fstream>
#include <functional>

namespace android {
namespace renderscript {
namespace rsov {

namespace {
// Layout of this struct has to be the same as the struct in generated SPIR-V
// TODO: generate this file from some spec that is shared with the compiler
struct rsovTypeInfo {
  uint32_t element_size;  // TODO: not implemented
  uint32_t x_size;
  uint32_t y_size;
  uint32_t z_size;
};

const char *COMPILER_EXE_PATH = "/system/bin/bcc_rsov";

std::vector<const char *> setCompilerArgs(const char *bcFileName,
                                          const char *cacheDir) {
  rsAssert(bcFileName && cacheDir);

  std::vector<const char *> args;

  args.push_back(COMPILER_EXE_PATH);
  args.push_back(bcFileName);

  args.push_back(nullptr);
  return args;
}

void writeBytes(const char *filename, const char *bytes, size_t size) {
  std::ofstream ofs(filename, std::ios::binary);
  ofs.write(bytes, size);
  ofs.close();
}

std::vector<uint32_t> readWords(const char *filename) {
  std::ifstream ifs(filename, std::ios::binary);

  ifs.seekg(0, ifs.end);
  int length = ifs.tellg();
  ifs.seekg(0, ifs.beg);

  rsAssert(((length & 3) == 0) && "File size expected to be multiples of 4");

  std::vector<uint32_t> spvWords(length / sizeof(uint32_t));

  ifs.read((char *)(spvWords.data()), length);

  ifs.close();

  return spvWords;
}

std::vector<uint32_t> compileBitcode(const char *resName, const char *cacheDir,
                                     const char *bitcode, size_t bitcodeSize) {
  rsAssert(bitcode && bitcodeSize);

  // TODO: Cache the generated code

  std::string bcFileName(cacheDir);
  bcFileName.append("/");
  bcFileName.append(resName);
  bcFileName.append(".bc");

  writeBytes(bcFileName.c_str(), bitcode, bitcodeSize);

  auto args = setCompilerArgs(bcFileName.c_str(), cacheDir);

  if (!rsuExecuteCommand(COMPILER_EXE_PATH, args.size() - 1, args.data())) {
    ALOGE("compiler command line failed");
    return std::vector<uint32_t>();
  }

  ALOGV("compiler command line succeeded");

  std::string spvFileName(cacheDir);
  spvFileName.append("/");
  spvFileName.append(resName);
  spvFileName.append(".spv");

  return readWords(spvFileName.c_str());
}

}  // anonymous namespace

bool RSoVScript::isScriptCpuBacked(const Script *s) {
  return s->mHal.info.mVersionMinor == CPU_SCRIPT_MAGIC_NUMBER;
}

void RSoVScript::initScriptOnCpu(Script *s, RsdCpuReference::CpuScript *cs) {
  s->mHal.drv = cs;
  s->mHal.info.mVersionMajor = 0;  // Unused. Don't care.
  s->mHal.info.mVersionMinor = CPU_SCRIPT_MAGIC_NUMBER;
}

void RSoVScript::initScriptOnRSoV(Script *s, RSoVScript *rsovScript) {
  s->mHal.drv = rsovScript;
  s->mHal.info.mVersionMajor = 0;  // Unused. Don't care.
  s->mHal.info.mVersionMinor = 0;
}

RSoVScript::RSoVScript(RSoVContext *context, std::vector<uint32_t> &&spvWords,
                       bcinfo::MetadataExtractor *ME,
                       std::map<std::string, int> *GA2ID)
    : mRSoV(context),
      mDevice(context->getDevice()),
      mSPIRVWords(std::move(spvWords)),
      mME(ME),
      mGlobalAllocationMetadata(nullptr),
      mGAMapping(GA2ID) {}

RSoVScript::~RSoVScript() {
  delete mCpuScript;
  delete mME;
}

void RSoVScript::populateScript(Script *) {
  // TODO: implement this
}

void RSoVScript::invokeFunction(uint32_t slot, const void *params,
                                size_t paramLength) {
  getCpuScript()->invokeFunction(slot, params, paramLength);
}

int RSoVScript::invokeRoot() { return getCpuScript()->invokeRoot(); }

void RSoVScript::invokeForEach(uint32_t slot, const Allocation **ains,
                               uint32_t inLen, Allocation *aout,
                               const void *usr, uint32_t usrLen,
                               const RsScriptCall *sc) {
  // TODO: Handle kernel without input Allocation
  rsAssert(ains);
  std::vector<RSoVAllocation *> inputAllocations(inLen);
  for (uint32_t i = 0; i < inLen; ++i) {
    inputAllocations[i] = static_cast<RSoVAllocation *>(ains[i]->mHal.drv);
  }
  RSoVAllocation *outputAllocation =
      static_cast<RSoVAllocation *>(aout->mHal.drv);
  runForEach(slot, inLen, inputAllocations, outputAllocation);
}

void RSoVScript::invokeReduce(uint32_t slot, const Allocation **ains,
                              uint32_t inLen, Allocation *aout,
                              const RsScriptCall *sc) {
  getCpuScript()->invokeReduce(slot, ains, inLen, aout, sc);
}

void RSoVScript::invokeInit() {
  // TODO: implement this
}

void RSoVScript::invokeFreeChildren() {
  // TODO: implement this
}

void RSoVScript::setGlobalVar(uint32_t slot, const void *data,
                              size_t dataLength) {
  // TODO: implement this
  ALOGV("%s missing.", __FUNCTION__);
}

void RSoVScript::getGlobalVar(uint32_t slot, void *data, size_t dataLength) {
  // TODO: implement this
  ALOGV("%s missing.", __FUNCTION__);
}

void RSoVScript::setGlobalVarWithElemDims(uint32_t slot, const void *data,
                                          size_t dataLength, const Element *e,
                                          const uint32_t *dims,
                                          size_t dimLength) {
  // TODO: implement this
}

void RSoVScript::setGlobalBind(uint32_t slot, Allocation *data) {
  ALOGV("%s succeeded.", __FUNCTION__);
  // TODO: implement this
}

void RSoVScript::setGlobalObj(uint32_t slot, ObjectBase *obj) {
  mCpuScript->setGlobalObj(slot, obj);
  ALOGV("%s succeeded.", __FUNCTION__);
}

Allocation *RSoVScript::getAllocationForPointer(const void *ptr) const {
  // TODO: implement this
  return nullptr;
}

int RSoVScript::getGlobalEntries() const {
  // TODO: implement this
  return 0;
}

const char *RSoVScript::getGlobalName(int i) const {
  // TODO: implement this
  return nullptr;
}

const void *RSoVScript::getGlobalAddress(int i) const {
  // TODO: implement this
  return nullptr;
}

size_t RSoVScript::getGlobalSize(int i) const {
  // TODO: implement this
  return 0;
}

uint32_t RSoVScript::getGlobalProperties(int i) const {
  // TODO: implement this
  return 0;
}

void RSoVScript::InitDescriptorAndPipelineLayouts(uint32_t inLen) {
  // TODO: global variables
  // TODO: kernels with zero output allocations
  std::vector<VkDescriptorSetLayoutBinding> layout_bindings{
      {
          // for the global allocation metadata
          .binding = 0,
          .descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_BUFFER,
          .descriptorCount = 1,
          .stageFlags = VK_SHADER_STAGE_COMPUTE_BIT,
          .pImmutableSamplers = nullptr,
      },
      {
          // for the output allocation
          .binding = 1,
          .descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_BUFFER,
          .descriptorCount = 1,
          .stageFlags = VK_SHADER_STAGE_COMPUTE_BIT,
          .pImmutableSamplers = nullptr,
      },
  };

  // initialize descriptors for input allocations
  for (uint32_t i = 0; i < inLen; ++i) {
    layout_bindings.push_back({
        .binding = i + 2,  // input allocations start from bining #2
        .descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_BUFFER,
        .descriptorCount = 1,
        .stageFlags = VK_SHADER_STAGE_COMPUTE_BIT,
        .pImmutableSamplers = nullptr,
    });
  }

  VkDescriptorSetLayoutCreateInfo descriptor_layout = {
      .sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO,
      .pNext = nullptr,
      .flags = 0,
      .bindingCount = inLen + 2,
      .pBindings = layout_bindings.data(),
  };

  VkResult res;

  mDescLayout.resize(NUM_DESCRIPTOR_SETS);
  res = vkCreateDescriptorSetLayout(mDevice, &descriptor_layout, NULL,
                                    mDescLayout.data());
  if (res != VK_SUCCESS) {
    __android_log_print(ANDROID_LOG_ERROR, "ComputeTest",
                        "vkCreateDescriptorSetLayout() returns %d", res);
  }
  rsAssert(res == VK_SUCCESS);

  /* Now use the descriptor layout to create a pipeline layout */
  VkPipelineLayoutCreateInfo pPipelineLayoutCreateInfo = {
      .sType = VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO,
      .pNext = nullptr,
      .pushConstantRangeCount = 0,
      .pPushConstantRanges = nullptr,
      .setLayoutCount = NUM_DESCRIPTOR_SETS,
      .pSetLayouts = mDescLayout.data(),
  };

  res = vkCreatePipelineLayout(mDevice, &pPipelineLayoutCreateInfo, NULL,
                               &mPipelineLayout);
  rsAssert(res == VK_SUCCESS);

  ALOGV("%s succeeded.", __FUNCTION__);
}

void RSoVScript::InitShader(uint32_t slot) {
  VkResult res;

  mShaderStage.sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
  mShaderStage.pNext = nullptr;
  mShaderStage.pSpecializationInfo = nullptr;
  mShaderStage.flags = 0;
  mShaderStage.stage = VK_SHADER_STAGE_COMPUTE_BIT;
  const char **RSKernelNames = mME->getExportForEachNameList();
  size_t RSKernelNum = mME->getExportForEachSignatureCount();
  rsAssert(slot < RSKernelNum);
  rsAssert(RSKernelNames);
  rsAssert(RSKernelNames[slot]);
  ALOGV("slot = %d kernel name = %s", slot, RSKernelNames[slot]);
  std::string entryName("entry_");
  entryName.append(RSKernelNames[slot]);
  mShaderStage.pName = strndup(entryName.c_str(), entryName.size());

  VkShaderModuleCreateInfo moduleCreateInfo = {
      .sType = VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO,
      .pNext = nullptr,
      .flags = 0,
      .codeSize = mSPIRVWords.size() * sizeof(unsigned int),
      .pCode = mSPIRVWords.data(),
  };
  res = vkCreateShaderModule(mDevice, &moduleCreateInfo, NULL,
                             &mShaderStage.module);
  rsAssert(res == VK_SUCCESS);

  ALOGV("%s succeeded.", __FUNCTION__);
}

void RSoVScript::InitDescriptorPool(uint32_t inLen) {
  /* DEPENDS on InitDescriptorAndPipelineLayouts() */

  VkResult res;
  VkDescriptorPoolSize type_count[] = {
      {
          .type = VK_DESCRIPTOR_TYPE_STORAGE_BUFFER,
          .descriptorCount = 2 + inLen,
      },
  };

  VkDescriptorPoolCreateInfo descriptor_pool = {
      .sType = VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO,
      .pNext = nullptr,
      .maxSets = 1,
      .poolSizeCount = NELEM(type_count),
      .pPoolSizes = type_count,
  };

  res = vkCreateDescriptorPool(mDevice, &descriptor_pool, NULL, &mDescPool);
  rsAssert(res == VK_SUCCESS);

  ALOGV("%s succeeded.", __FUNCTION__);
}

// Iterate through a list of global allocations that are used inside the module
// and marshal their type information to a dedicated Vulkan Buffer
void RSoVScript::MarshalTypeInfo(void) {
  // Marshal global allocation metadata to the device
  auto *cs = getCpuScript();
  int nr_globals = mGAMapping->size();
  if (mGlobalAllocationMetadata == nullptr) {
    mGlobalAllocationMetadata.reset(
        new RSoVBuffer(mRSoV, sizeof(struct rsovTypeInfo) * nr_globals));
  }
  struct rsovTypeInfo *mappedMetadata =
      (struct rsovTypeInfo *)mGlobalAllocationMetadata->getHostPtr();
  for (int i = 0; i < nr_globals; ++i) {
    if (getGlobalRsType(cs->getGlobalProperties(i)) ==
        RsDataType::RS_TYPE_ALLOCATION) {
      ALOGV("global variable %d is an allocation!", i);
      const void *host_buf;
      cs->getGlobalVar(i, (void *)&host_buf, sizeof(host_buf));
      if (!host_buf) continue;
      const android::renderscript::Allocation *GA =
          static_cast<const android::renderscript::Allocation *>(host_buf);
      const android::renderscript::Type *T = GA->getType();
      rsAssert(T);

      auto global_it = mGAMapping->find(cs->getGlobalName(i));
      rsAssert(global_it != (*mGAMapping).end());
      int id = global_it->second;
      ALOGV("global allocation %s is mapped to ID %d", cs->getGlobalName(i),
            id);
      // TODO: marshal other properties
      mappedMetadata[id].x_size = T->getDimX();
      mappedMetadata[id].y_size = T->getDimY();
      mappedMetadata[id].z_size = T->getDimZ();
    }
  }
}

void RSoVScript::InitDescriptorSet(
    const std::vector<RSoVAllocation *> &inputAllocations,
    RSoVAllocation *outputAllocation) {
  VkResult res;

  VkDescriptorSetAllocateInfo alloc_info = {
      .sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO,
      .pNext = NULL,
      .descriptorPool = mDescPool,
      .descriptorSetCount = NUM_DESCRIPTOR_SETS,
      .pSetLayouts = mDescLayout.data(),
  };

  mDescSet.resize(NUM_DESCRIPTOR_SETS);
  res = vkAllocateDescriptorSets(mDevice, &alloc_info, mDescSet.data());
  ALOGD("vkAllocateDescriptorSets() result = %d", res);
  rsAssert(res == VK_SUCCESS);

  // TODO: support for set up the binding(s) of global variables
  uint32_t nBindings = inputAllocations.size() + 1;  // input + output.
  std::vector<VkWriteDescriptorSet> writes{
      // Metadata for global allocations
      {
          .sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET,
          .dstSet = mDescSet[0],
          .dstBinding = 0,
          .dstArrayElement = 0,
          .descriptorCount = 1,
          .descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_BUFFER,
          .pBufferInfo = mGlobalAllocationMetadata->getBufferInfo(),
      },

      {
          .sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET,
          .dstSet = mDescSet[0],
          .dstBinding = 1,
          .dstArrayElement = 0,
          .descriptorCount = 1,
          .descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_BUFFER,
          .pBufferInfo = outputAllocation->getBuffer()->getBufferInfo(),
      },
  };
  for (uint32_t i = 0; i < inputAllocations.size(); ++i) {
    writes.push_back({
        .sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET,
        .dstSet = mDescSet[0],
        .dstBinding = 2 + i,  // input allocations start from binding #2
        .dstArrayElement = 0,
        .descriptorCount = 1,
        .descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_BUFFER,
        .pBufferInfo = inputAllocations[i]->getBuffer()->getBufferInfo(),
    });
  }

  vkUpdateDescriptorSets(mDevice, writes.size(), writes.data(), 0, NULL);

  ALOGV("%s succeeded.", __FUNCTION__);
}

void RSoVScript::InitPipeline() {
  // DEPENDS on mShaderStage, i.e., InitShader()

  VkResult res;

  VkComputePipelineCreateInfo pipeline_info = {
      .sType = VK_STRUCTURE_TYPE_COMPUTE_PIPELINE_CREATE_INFO,
      .pNext = nullptr,
      .layout = mPipelineLayout,
      .basePipelineHandle = VK_NULL_HANDLE,
      .basePipelineIndex = 0,
      .flags = 0,
      .stage = mShaderStage,
  };
  res = vkCreateComputePipelines(mDevice, VK_NULL_HANDLE, 1, &pipeline_info,
                                 NULL, &mComputePipeline);
  rsAssert(res == VK_SUCCESS);

  ALOGV("%s succeeded.", __FUNCTION__);
}

void RSoVScript::runForEach(
    uint32_t slot, uint32_t inLen,
    const std::vector<RSoVAllocation *> &inputAllocations,
    RSoVAllocation *outputAllocation) {
  VkResult res;

  InitDescriptorAndPipelineLayouts(inLen);
  InitShader(slot);
  InitDescriptorPool(inLen);
  MarshalTypeInfo();
  InitDescriptorSet(inputAllocations, outputAllocation);
  // InitPipelineCache();
  InitPipeline();

  VkCommandBuffer cmd;

  VkCommandBufferAllocateInfo cmd_info = {
      .sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO,
      .pNext = nullptr,
      .commandPool = mRSoV->getCmdPool(),
      .level = VK_COMMAND_BUFFER_LEVEL_PRIMARY,
      .commandBufferCount = 1,
  };

  res = vkAllocateCommandBuffers(mDevice, &cmd_info, &cmd);
  rsAssert(res == VK_SUCCESS);

  VkCommandBufferBeginInfo cmd_buf_info = {
      .sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO,
      .pNext = nullptr,
      .flags = 0,
      .pInheritanceInfo = nullptr,
  };

  res = vkBeginCommandBuffer(cmd, &cmd_buf_info);
  rsAssert(res == VK_SUCCESS);

  vkCmdBindPipeline(cmd, VK_PIPELINE_BIND_POINT_COMPUTE, mComputePipeline);

  vkCmdBindDescriptorSets(cmd, VK_PIPELINE_BIND_POINT_COMPUTE, mPipelineLayout,
                          0, mDescSet.size(), mDescSet.data(), 0, nullptr);
  // Assuming all input allocations are of the same dimensionality
  const uint32_t width = inputAllocations[0]->getWidth();
  const uint32_t height = rsMax(inputAllocations[0]->getHeight(), 1U);
  const uint32_t depth = rsMax(inputAllocations[0]->getDepth(), 1U);
  vkCmdDispatch(cmd, width, height, depth);

  res = vkEndCommandBuffer(cmd);
  assert(res == VK_SUCCESS);

  VkSubmitInfo submit_info = {
      .sType = VK_STRUCTURE_TYPE_SUBMIT_INFO,
      .commandBufferCount = 1,
      .pCommandBuffers = &cmd,
  };

  VkFence fence;

  VkFenceCreateInfo fenceInfo = {
      .sType = VK_STRUCTURE_TYPE_FENCE_CREATE_INFO,
      .pNext = nullptr,
      .flags = 0,
  };

  vkCreateFence(mDevice, &fenceInfo, NULL, &fence);

  vkQueueSubmit(mRSoV->getQueue(), 1, &submit_info, fence);

  // Make sure command buffer is finished
  do {
    res = vkWaitForFences(mDevice, 1, &fence, VK_TRUE, 100000);
  } while (res == VK_TIMEOUT);

  rsAssert(res == VK_SUCCESS);

  vkDestroyFence(mDevice, fence, NULL);

  // TODO: shall we reuse command buffers?
  VkCommandBuffer cmd_bufs[] = {cmd};
  vkFreeCommandBuffers(mDevice, mRSoV->getCmdPool(), 1, cmd_bufs);

  vkDestroyPipeline(mDevice, mComputePipeline, nullptr);
  for (int i = 0; i < NUM_DESCRIPTOR_SETS; i++)
    vkDestroyDescriptorSetLayout(mDevice, mDescLayout[i], nullptr);
  vkDestroyPipelineLayout(mDevice, mPipelineLayout, nullptr);
  vkFreeDescriptorSets(mDevice, mDescPool, NUM_DESCRIPTOR_SETS,
                       mDescSet.data());
  vkDestroyDescriptorPool(mDevice, mDescPool, nullptr);
  free((void *)mShaderStage.pName);
  vkDestroyShaderModule(mDevice, mShaderStage.module, nullptr);

  ALOGV("%s succeeded.", __FUNCTION__);
}

}  // namespace rsov
}  // namespace renderscript
}  // namespace android

using android::renderscript::Allocation;
using android::renderscript::Context;
using android::renderscript::Element;
using android::renderscript::ObjectBase;
using android::renderscript::RsdCpuReference;
using android::renderscript::Script;
using android::renderscript::ScriptC;
using android::renderscript::rs_script;
using android::renderscript::rsov::RSoVContext;
using android::renderscript::rsov::RSoVScript;
using android::renderscript::rsov::compileBitcode;

namespace {
// A class to parse global allocation metadata; essentially a subset of JSON
// it would look like {"__RSoV_GA": {"g":42}}
// The result is stored in a refence to a map<string, int>
class ParseMD {
 public:
  ParseMD(std::string s, std::map<std::string, int> &map)
      : mString(s), mMapping(map) {}

  bool parse(void) {
    // remove outermose two pairs of braces
    mString = removeBraces(mString);
    mString = removeBraces(mString);
    // Now we are supposed to have a comma-separated list that looks like:
    // "foo":42, "bar":56
    split<','>(mString, [&](auto s) {
      split<':'>(s, nullptr, [&](auto pair) {
        rsAssert(pair.size() == 2);
        std::string ga_name = removeQuotes(pair[0]);
        int id = atoi(pair[1].c_str());
        ALOGV("ParseMD: global allocation %s has ID %d", ga_name.c_str(), id);
        mMapping[ga_name] = id;
      });
    });
    return true;
  }

 private:
  template <char L, char R>
  static std::string removeMatching(const std::string &s) {
    auto leftCBrace = s.find(L);
    rsAssert(leftCBrace != std::string::npos);
    leftCBrace++;
    return s.substr(leftCBrace, s.rfind(R) - leftCBrace);
  }

  static std::string removeBraces(const std::string &s) {
    return removeMatching<'{', '}'>(s);
  }

  static std::string removeQuotes(const std::string &s) {
    return removeMatching<'"', '"'>(s);
  }

  // Splitting a string, and call "each" and/or "all" with individal elements
  // and a vector of all tokenized elements
  template <char D>
  static void split(const std::string &s,
                    std::function<void(const std::string &)> each,
                    std::function<void(const std::vector<const std::string> &)>
                        all = nullptr) {
    std::vector<const std::string> result;
    for (std::string::size_type pos = 0; pos < s.size(); pos++) {
      std::string::size_type begin = pos;

      while (s[pos] != D && pos <= s.size()) pos++;
      std::string found = s.substr(begin, pos - begin);
      if (each) each(found);
      if (all) result.push_back(found);
    }
    if (all) all(result);
  }

  std::string mString;
  std::map<std::string, int> &mMapping;
};

}  // namespace

class ExtractRSoVMD : public android::spirit::DoNothingVisitor {
 public:
  ExtractRSoVMD() : mGAMapping(new std::map<std::string, int>) {}

  void visit(android::spirit::StringInst *s) {
    ALOGV("ExtractRSoVMD: string = %s", s->mOperand1.c_str());
    ParseMD p(s->mOperand1, *mGAMapping);
    p.parse();
  }

  std::map<std::string, int> *takeMapping(void) { return mGAMapping.release(); }

 private:
  std::unique_ptr<std::map<std::string, int> > mGAMapping;
};

bool rsovScriptInit(const Context *rsc, ScriptC *script, char const *resName,
                    char const *cacheDir, uint8_t const *bitcode,
                    size_t bitcodeSize, uint32_t flags) {
  RSoVHal *hal = static_cast<RSoVHal *>(rsc->mHal.drv);

  std::unique_ptr<RsdCpuReference::CpuScript> cs(hal->mCpuRef->createScript(
      script, resName, cacheDir, bitcode, bitcodeSize, flags));
  if (cs == nullptr) {
    ALOGE("Failed creating a CPU script %p for %s (%p)", cs.get(), resName,
          script);
    return false;
  }
  cs->populateScript(script);

  std::unique_ptr<bcinfo::MetadataExtractor> bitcodeMetadata(
      new bcinfo::MetadataExtractor((const char *)bitcode, bitcodeSize));
  if (!bitcodeMetadata || !bitcodeMetadata->extract()) {
    ALOGE("Could not extract metadata from bitcode from %s", resName);
    return false;
  }

  auto spvWords =
      compileBitcode(resName, cacheDir, (const char *)bitcode, bitcodeSize);
  if (!spvWords.empty()) {
    // Extract compiler metadata on allocation->binding mapping
    android::spirit::Module *module =
        android::spirit::Deserialize<android::spirit::Module>(spvWords);
    rsAssert(module);
    ExtractRSoVMD ga_md;
    module->accept(&ga_md);
    RSoVScript *rsovScript =
        new RSoVScript(hal->mRSoV, std::move(spvWords),
                       bitcodeMetadata.release(), ga_md.takeMapping());
    if (rsovScript) {
      rsovScript->setCpuScript(cs.release());
      RSoVScript::initScriptOnRSoV(script, rsovScript);
      return true;
    }
  }

  ALOGD("Failed creating an RSoV script for %s", resName);
  // Fall back to CPU driver instead
  RSoVScript::initScriptOnCpu(script, cs.release());

  return true;
}

bool rsovInitIntrinsic(const Context *rsc, Script *s, RsScriptIntrinsicID iid,
                       Element *e) {
  RSoVHal *dc = (RSoVHal *)rsc->mHal.drv;
  RsdCpuReference::CpuScript *cs = dc->mCpuRef->createIntrinsic(s, iid, e);
  if (cs == nullptr) {
    return false;
  }
  s->mHal.drv = cs;
  cs->populateScript(s);
  return true;
}

void rsovScriptInvokeForEach(const Context *rsc, Script *s, uint32_t slot,
                             const Allocation *ain, Allocation *aout,
                             const void *usr, size_t usrLen,
                             const RsScriptCall *sc) {
  if (ain == nullptr) {
    rsovScriptInvokeForEachMulti(rsc, s, slot, nullptr, 0, aout, usr, usrLen,
                                 sc);
  } else {
    const Allocation *ains[1] = {ain};

    rsovScriptInvokeForEachMulti(rsc, s, slot, ains, 1, aout, usr, usrLen, sc);
  }
}

void rsovScriptInvokeForEachMulti(const Context *rsc, Script *s, uint32_t slot,
                                  const Allocation **ains, size_t inLen,
                                  Allocation *aout, const void *usr,
                                  size_t usrLen, const RsScriptCall *sc) {
  RsdCpuReference::CpuScript *cs = (RsdCpuReference::CpuScript *)s->mHal.drv;
  cs->invokeForEach(slot, ains, inLen, aout, usr, usrLen, sc);
}

int rsovScriptInvokeRoot(const Context *dc, Script *s) {
  RsdCpuReference::CpuScript *cs = (RsdCpuReference::CpuScript *)s->mHal.drv;
  return cs->invokeRoot();
}

void rsovScriptInvokeInit(const Context *dc, Script *s) {
  RsdCpuReference::CpuScript *cs = (RsdCpuReference::CpuScript *)s->mHal.drv;
  cs->invokeInit();
}

void rsovScriptInvokeFreeChildren(const Context *dc, Script *s) {
  RsdCpuReference::CpuScript *cs = (RsdCpuReference::CpuScript *)s->mHal.drv;
  cs->invokeFreeChildren();
}

void rsovScriptInvokeFunction(const Context *dc, Script *s, uint32_t slot,
                              const void *params, size_t paramLength) {
  RsdCpuReference::CpuScript *cs = (RsdCpuReference::CpuScript *)s->mHal.drv;
  cs->invokeFunction(slot, params, paramLength);
}

void rsovScriptInvokeReduce(const Context *dc, Script *s, uint32_t slot,
                            const Allocation **ains, size_t inLen,
                            Allocation *aout, const RsScriptCall *sc) {
  RsdCpuReference::CpuScript *cs = (RsdCpuReference::CpuScript *)s->mHal.drv;
  cs->invokeReduce(slot, ains, inLen, aout, sc);
}

void rsovScriptSetGlobalVar(const Context *dc, const Script *s, uint32_t slot,
                            void *data, size_t dataLength) {
  RsdCpuReference::CpuScript *cs = (RsdCpuReference::CpuScript *)s->mHal.drv;
  cs->setGlobalVar(slot, data, dataLength);
}

void rsovScriptGetGlobalVar(const Context *dc, const Script *s, uint32_t slot,
                            void *data, size_t dataLength) {
  RsdCpuReference::CpuScript *cs = (RsdCpuReference::CpuScript *)s->mHal.drv;
  cs->getGlobalVar(slot, data, dataLength);
}

void rsovScriptSetGlobalVarWithElemDims(
    const Context *dc, const Script *s, uint32_t slot, void *data,
    size_t dataLength, const android::renderscript::Element *elem,
    const uint32_t *dims, size_t dimLength) {
  RsdCpuReference::CpuScript *cs = (RsdCpuReference::CpuScript *)s->mHal.drv;
  cs->setGlobalVarWithElemDims(slot, data, dataLength, elem, dims, dimLength);
}

void rsovScriptSetGlobalBind(const Context *dc, const Script *s, uint32_t slot,
                             Allocation *data) {
  RsdCpuReference::CpuScript *cs = (RsdCpuReference::CpuScript *)s->mHal.drv;
  cs->setGlobalBind(slot, data);
}

void rsovScriptSetGlobalObj(const Context *dc, const Script *s, uint32_t slot,
                            ObjectBase *data) {
  RsdCpuReference::CpuScript *cs = (RsdCpuReference::CpuScript *)s->mHal.drv;
  cs->setGlobalObj(slot, data);
}

void rsovScriptDestroy(const Context *dc, Script *s) {
  RsdCpuReference::CpuScript *cs = (RsdCpuReference::CpuScript *)s->mHal.drv;
  delete cs;
  s->mHal.drv = nullptr;
}

Allocation *rsovScriptGetAllocationForPointer(
    const android::renderscript::Context *dc,
    const android::renderscript::Script *sc, const void *ptr) {
  RsdCpuReference::CpuScript *cs = (RsdCpuReference::CpuScript *)sc->mHal.drv;
  return cs->getAllocationForPointer(ptr);
}

void rsovScriptUpdateCachedObject(const Context *rsc, const Script *script,
                                  rs_script *obj) {
  obj->p = script;
#ifdef __LP64__
  obj->unused1 = nullptr;
  obj->unused2 = nullptr;
  obj->unused3 = nullptr;
#endif
}
