#pragma once
#include <vulkan/vulkan.h>
#include "vk_context.h"

struct GpuBuffer {
    VkBuffer buffer = VK_NULL_HANDLE;
    VkDeviceMemory memory = VK_NULL_HANDLE;
    void *mapped = nullptr;
    VkDeviceSize size = 0;
};

bool createHostVisibleBuffer(VkContext &ctx, VkDeviceSize size, VkBufferUsageFlags usage, GpuBuffer &out);
void destroyBuffer(VkContext &ctx, GpuBuffer &buf);
