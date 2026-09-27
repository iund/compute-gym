#pragma once
#include <vulkan/vulkan.h>
#include <string>

struct VkContext {
    VkInstance instance = VK_NULL_HANDLE;
    VkPhysicalDevice physicalDevice = VK_NULL_HANDLE;
    VkDevice device = VK_NULL_HANDLE;
    VkQueue queue = VK_NULL_HANDLE;
    uint32_t queueFamilyIndex = 0;
    std::string deviceName;
};

bool createVkContext(VkContext &ctx);
void destroyVkContext(VkContext &ctx);
