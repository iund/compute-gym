#pragma once
#include <vulkan/vulkan.h>
#include <android/asset_manager.h>
#include "vk_context.h"
#include "vk_buffers.h"

struct ComputePipeline {
    VkShaderModule shaderModule = VK_NULL_HANDLE;
    VkDescriptorSetLayout setLayout = VK_NULL_HANDLE;
    VkPipelineLayout pipelineLayout = VK_NULL_HANDLE;
    VkPipeline pipeline = VK_NULL_HANDLE;
    VkDescriptorPool descriptorPool = VK_NULL_HANDLE;
    VkDescriptorSet descriptorSet = VK_NULL_HANDLE;
};

bool createComputePipeline(VkContext &ctx, AAssetManager *assets, const char *spvAssetPath, int bufferCount, ComputePipeline &out);
void bindBuffers(VkContext &ctx, ComputePipeline &pipe, GpuBuffer *buffers[], int count);
void destroyPipeline(VkContext &ctx, ComputePipeline &pipe);
