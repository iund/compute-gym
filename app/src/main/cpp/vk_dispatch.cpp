#include "vk_dispatch.h"
#include "log.h"
#include <chrono>

bool dispatchAndWait(VkContext &ctx, ComputePipeline &pipe, uint32_t groupCountX, double &elapsedMsOut) {
    VkCommandPoolCreateInfo poolInfo{VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO};
    poolInfo.queueFamilyIndex = ctx.queueFamilyIndex;
    VkCommandPool cmdPool;
    if (vkCreateCommandPool(ctx.device, &poolInfo, nullptr, &cmdPool) != VK_SUCCESS) {
        LOGE("vkCreateCommandPool failed");
        return false;
    }

    VkCommandBufferAllocateInfo cbAlloc{VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO};
    cbAlloc.commandPool = cmdPool;
    cbAlloc.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
    cbAlloc.commandBufferCount = 1;
    VkCommandBuffer cmd;
    vkAllocateCommandBuffers(ctx.device, &cbAlloc, &cmd);

    VkCommandBufferBeginInfo beginInfo{VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO};
    vkBeginCommandBuffer(cmd, &beginInfo);

    vkCmdBindPipeline(cmd, VK_PIPELINE_BIND_POINT_COMPUTE, pipe.pipeline);
    vkCmdBindDescriptorSets(cmd, VK_PIPELINE_BIND_POINT_COMPUTE, pipe.pipelineLayout, 0, 1, &pipe.descriptorSet, 0, nullptr);
    vkCmdDispatch(cmd, groupCountX, 1, 1);

    // Guard against the driver eliding a dispatch whose output looks unconsumed:
    // make the write -> host-read dependency explicit.
    VkMemoryBarrier barrier{VK_STRUCTURE_TYPE_MEMORY_BARRIER};
    barrier.srcAccessMask = VK_ACCESS_SHADER_WRITE_BIT;
    barrier.dstAccessMask = VK_ACCESS_HOST_READ_BIT;
    vkCmdPipelineBarrier(cmd, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, VK_PIPELINE_STAGE_HOST_BIT,
                          0, 1, &barrier, 0, nullptr, 0, nullptr);

    vkEndCommandBuffer(cmd);

    VkSubmitInfo submitInfo{VK_STRUCTURE_TYPE_SUBMIT_INFO};
    submitInfo.commandBufferCount = 1;
    submitInfo.pCommandBuffers = &cmd;

    auto start = std::chrono::steady_clock::now();
    if (vkQueueSubmit(ctx.queue, 1, &submitInfo, VK_NULL_HANDLE) != VK_SUCCESS) {
        LOGE("vkQueueSubmit failed");
        vkDestroyCommandPool(ctx.device, cmdPool, nullptr);
        return false;
    }
    vkQueueWaitIdle(ctx.queue);
    auto end = std::chrono::steady_clock::now();
    elapsedMsOut = std::chrono::duration<double, std::milli>(end - start).count();

    vkDestroyCommandPool(ctx.device, cmdPool, nullptr);
    return true;
}
