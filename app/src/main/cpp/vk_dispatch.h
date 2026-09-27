#pragma once
#include "vk_context.h"
#include "vk_pipeline.h"

bool dispatchAndWait(VkContext &ctx, ComputePipeline &pipe, uint32_t groupCountX, double &elapsedMsOut);
