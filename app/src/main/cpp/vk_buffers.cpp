#include "vk_buffers.h"
#include "log.h"

static bool findMemoryType(VkContext &ctx, uint32_t typeBits, VkMemoryPropertyFlags props, uint32_t &out) {
    VkPhysicalDeviceMemoryProperties memProps;
    vkGetPhysicalDeviceMemoryProperties(ctx.physicalDevice, &memProps);
    for (uint32_t i = 0; i < memProps.memoryTypeCount; i++) {
        if ((typeBits & (1u << i)) && (memProps.memoryTypes[i].propertyFlags & props) == props) {
            out = i;
            return true;
        }
    }
    return false;
}

bool createHostVisibleBuffer(VkContext &ctx, VkDeviceSize size, VkBufferUsageFlags usage, GpuBuffer &out) {
    VkBufferCreateInfo bufInfo{VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO};
    bufInfo.size = size;
    bufInfo.usage = usage;
    bufInfo.sharingMode = VK_SHARING_MODE_EXCLUSIVE;

    if (vkCreateBuffer(ctx.device, &bufInfo, nullptr, &out.buffer) != VK_SUCCESS) {
        LOGE("vkCreateBuffer failed");
        return false;
    }

    VkMemoryRequirements req;
    vkGetBufferMemoryRequirements(ctx.device, out.buffer, &req);

    uint32_t memType;
    VkMemoryPropertyFlags hostFlags = VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT;
    if (!findMemoryType(ctx, req.memoryTypeBits, hostFlags, memType)) {
        LOGE("no host-visible+coherent memory type");
        return false;
    }

    VkMemoryAllocateInfo allocInfo{VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO};
    allocInfo.allocationSize = req.size;
    allocInfo.memoryTypeIndex = memType;

    if (vkAllocateMemory(ctx.device, &allocInfo, nullptr, &out.memory) != VK_SUCCESS) {
        LOGE("vkAllocateMemory failed");
        return false;
    }
    vkBindBufferMemory(ctx.device, out.buffer, out.memory, 0);
    vkMapMemory(ctx.device, out.memory, 0, size, 0, &out.mapped);
    out.size = size;
    return true;
}

void destroyBuffer(VkContext &ctx, GpuBuffer &buf) {
    if (buf.mapped) vkUnmapMemory(ctx.device, buf.memory);
    if (buf.buffer) vkDestroyBuffer(ctx.device, buf.buffer, nullptr);
    if (buf.memory) vkFreeMemory(ctx.device, buf.memory, nullptr);
    buf = GpuBuffer{};
}
