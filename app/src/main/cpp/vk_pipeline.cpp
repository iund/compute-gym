#include "vk_pipeline.h"
#include "log.h"
#include <vector>

static bool loadSpv(AAssetManager *assets, const char *path, std::vector<uint32_t> &out) {
    AAsset *asset = AAssetManager_open(assets, path, AASSET_MODE_BUFFER);
    if (!asset) {
        LOGE("asset not found: %s", path);
        return false;
    }
    size_t len = AAsset_getLength(asset);
    out.resize((len + 3) / 4);
    AAsset_read(asset, out.data(), len);
    AAsset_close(asset);
    return true;
}

bool createComputePipeline(VkContext &ctx, AAssetManager *assets, const char *spvAssetPath, int bufferCount, ComputePipeline &out) {
    std::vector<uint32_t> spv;
    if (!loadSpv(assets, spvAssetPath, spv)) return false;

    VkShaderModuleCreateInfo shaderInfo{VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO};
    shaderInfo.codeSize = spv.size() * 4;
    shaderInfo.pCode = spv.data();
    if (vkCreateShaderModule(ctx.device, &shaderInfo, nullptr, &out.shaderModule) != VK_SUCCESS) {
        LOGE("vkCreateShaderModule failed");
        return false;
    }

    std::vector<VkDescriptorSetLayoutBinding> bindings(bufferCount);
    for (int i = 0; i < bufferCount; i++) {
        bindings[i] = {};
        bindings[i].binding = i;
        bindings[i].descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;
        bindings[i].descriptorCount = 1;
        bindings[i].stageFlags = VK_SHADER_STAGE_COMPUTE_BIT;
    }
    VkDescriptorSetLayoutCreateInfo setLayoutInfo{VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO};
    setLayoutInfo.bindingCount = bufferCount;
    setLayoutInfo.pBindings = bindings.data();
    if (vkCreateDescriptorSetLayout(ctx.device, &setLayoutInfo, nullptr, &out.setLayout) != VK_SUCCESS) {
        LOGE("vkCreateDescriptorSetLayout failed");
        return false;
    }

    VkPipelineLayoutCreateInfo layoutInfo{VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO};
    layoutInfo.setLayoutCount = 1;
    layoutInfo.pSetLayouts = &out.setLayout;
    if (vkCreatePipelineLayout(ctx.device, &layoutInfo, nullptr, &out.pipelineLayout) != VK_SUCCESS) {
        LOGE("vkCreatePipelineLayout failed");
        return false;
    }

    VkPipelineShaderStageCreateInfo stageInfo{VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO};
    stageInfo.stage = VK_SHADER_STAGE_COMPUTE_BIT;
    stageInfo.module = out.shaderModule;
    stageInfo.pName = "main";

    VkComputePipelineCreateInfo pipeInfo{VK_STRUCTURE_TYPE_COMPUTE_PIPELINE_CREATE_INFO};
    pipeInfo.stage = stageInfo;
    pipeInfo.layout = out.pipelineLayout;
    if (vkCreateComputePipelines(ctx.device, VK_NULL_HANDLE, 1, &pipeInfo, nullptr, &out.pipeline) != VK_SUCCESS) {
        LOGE("vkCreateComputePipelines failed");
        return false;
    }

    VkDescriptorPoolSize poolSize{VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, static_cast<uint32_t>(bufferCount)};
    VkDescriptorPoolCreateInfo poolInfo{VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO};
    poolInfo.maxSets = 1;
    poolInfo.poolSizeCount = 1;
    poolInfo.pPoolSizes = &poolSize;
    if (vkCreateDescriptorPool(ctx.device, &poolInfo, nullptr, &out.descriptorPool) != VK_SUCCESS) {
        LOGE("vkCreateDescriptorPool failed");
        return false;
    }

    VkDescriptorSetAllocateInfo dsAlloc{VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO};
    dsAlloc.descriptorPool = out.descriptorPool;
    dsAlloc.descriptorSetCount = 1;
    dsAlloc.pSetLayouts = &out.setLayout;
    if (vkAllocateDescriptorSets(ctx.device, &dsAlloc, &out.descriptorSet) != VK_SUCCESS) {
        LOGE("vkAllocateDescriptorSets failed");
        return false;
    }
    return true;
}

void bindBuffers(VkContext &ctx, ComputePipeline &pipe, GpuBuffer *buffers[], int count) {
    std::vector<VkDescriptorBufferInfo> infos(count);
    std::vector<VkWriteDescriptorSet> writes(count);
    for (int i = 0; i < count; i++) {
        infos[i] = {buffers[i]->buffer, 0, VK_WHOLE_SIZE};
        writes[i] = {VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET};
        writes[i].dstSet = pipe.descriptorSet;
        writes[i].dstBinding = i;
        writes[i].descriptorCount = 1;
        writes[i].descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;
        writes[i].pBufferInfo = &infos[i];
    }
    vkUpdateDescriptorSets(ctx.device, count, writes.data(), 0, nullptr);
}

void destroyPipeline(VkContext &ctx, ComputePipeline &pipe) {
    if (pipe.descriptorPool) vkDestroyDescriptorPool(ctx.device, pipe.descriptorPool, nullptr);
    if (pipe.pipeline) vkDestroyPipeline(ctx.device, pipe.pipeline, nullptr);
    if (pipe.pipelineLayout) vkDestroyPipelineLayout(ctx.device, pipe.pipelineLayout, nullptr);
    if (pipe.setLayout) vkDestroyDescriptorSetLayout(ctx.device, pipe.setLayout, nullptr);
    if (pipe.shaderModule) vkDestroyShaderModule(ctx.device, pipe.shaderModule, nullptr);
    pipe = ComputePipeline{};
}
