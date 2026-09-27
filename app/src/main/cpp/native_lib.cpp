#include <jni.h>
#include <cstring>
#include <android/asset_manager_jni.h>
#include "vk_context.h"
#include "vk_buffers.h"
#include "vk_pipeline.h"
#include "vk_dispatch.h"
#include "log.h"

namespace {
constexpr uint32_t N = 1024;
constexpr uint32_t LOCAL_SIZE = 64;
constexpr uint32_t MAX_GROUPS = 32;

VkContext g_ctx;
GpuBuffer g_a, g_b, g_c;
ComputePipeline g_vectorAddPipeline;
GpuBuffer g_reduceIn, g_reduceOut;
ComputePipeline g_reducePipeline;
double g_lastElapsedMs = 0;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_dev_computegym_hello_VulkanBridge_nativeInit(JNIEnv *env, jclass, jobject assetManagerObj) {
    if (!createVkContext(g_ctx)) return JNI_FALSE;

    VkDeviceSize bytes = N * sizeof(float);
    VkBufferUsageFlags usage = VK_BUFFER_USAGE_STORAGE_BUFFER_BIT;
    if (!createHostVisibleBuffer(g_ctx, bytes, usage, g_a) ||
        !createHostVisibleBuffer(g_ctx, bytes, usage, g_b) ||
        !createHostVisibleBuffer(g_ctx, bytes, usage, g_c)) {
        return JNI_FALSE;
    }
    memset(g_c.mapped, 0, bytes);

    AAssetManager *assets = AAssetManager_fromJava(env, assetManagerObj);
    if (!createComputePipeline(g_ctx, assets, "vector_add.spv", 3, g_vectorAddPipeline)) return JNI_FALSE;
    GpuBuffer *vectorAddBufs[3] = {&g_a, &g_b, &g_c};
    bindBuffers(g_ctx, g_vectorAddPipeline, vectorAddBufs, 3);

    if (!createHostVisibleBuffer(g_ctx, bytes, usage, g_reduceIn) ||
        !createHostVisibleBuffer(g_ctx, MAX_GROUPS * sizeof(float), usage, g_reduceOut)) {
        return JNI_FALSE;
    }
    memset(g_reduceOut.mapped, 0, MAX_GROUPS * sizeof(float));
    if (!createComputePipeline(g_ctx, assets, "reduction.spv", 2, g_reducePipeline)) return JNI_FALSE;
    GpuBuffer *reduceBufs[2] = {&g_reduceIn, &g_reduceOut};
    bindBuffers(g_ctx, g_reducePipeline, reduceBufs, 2);

    LOGI("init complete");
    return JNI_TRUE;
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_dev_computegym_hello_VulkanBridge_nativeRunVectorAdd(JNIEnv *env, jclass, jfloatArray aIn, jfloatArray bIn, jint dispatchGroupsX) {
    env->GetFloatArrayRegion(aIn, 0, N, static_cast<jfloat *>(g_a.mapped));
    env->GetFloatArrayRegion(bIn, 0, N, static_cast<jfloat *>(g_b.mapped));

    uint32_t groups = dispatchGroupsX > 0 ? static_cast<uint32_t>(dispatchGroupsX) : (N / LOCAL_SIZE);
    if (!dispatchAndWait(g_ctx, g_vectorAddPipeline, groups, g_lastElapsedMs)) {
        return nullptr;
    }

    jfloatArray out = env->NewFloatArray(N);
    env->SetFloatArrayRegion(out, 0, N, static_cast<jfloat *>(g_c.mapped));
    return out;
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_dev_computegym_hello_VulkanBridge_nativeRunReduction(JNIEnv *env, jclass, jfloatArray inputIn, jint dispatchGroupsX) {
    env->GetFloatArrayRegion(inputIn, 0, N, static_cast<jfloat *>(g_reduceIn.mapped));

    uint32_t groups = dispatchGroupsX > 0 ? static_cast<uint32_t>(dispatchGroupsX) : (N / LOCAL_SIZE);
    if (groups > MAX_GROUPS) groups = MAX_GROUPS;
    if (!dispatchAndWait(g_ctx, g_reducePipeline, groups, g_lastElapsedMs)) {
        return nullptr;
    }

    jfloatArray out = env->NewFloatArray(groups);
    env->SetFloatArrayRegion(out, 0, groups, static_cast<jfloat *>(g_reduceOut.mapped));
    return out;
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_computegym_hello_VulkanBridge_nativeGetDeviceName(JNIEnv *env, jclass) {
    return env->NewStringUTF(g_ctx.deviceName.c_str());
}

extern "C" JNIEXPORT jdouble JNICALL
Java_dev_computegym_hello_VulkanBridge_nativeGetElapsedMs(JNIEnv *, jclass) {
    return g_lastElapsedMs;
}

extern "C" JNIEXPORT void JNICALL
Java_dev_computegym_hello_VulkanBridge_nativeShutdown(JNIEnv *, jclass) {
    destroyPipeline(g_ctx, g_vectorAddPipeline);
    destroyBuffer(g_ctx, g_a);
    destroyBuffer(g_ctx, g_b);
    destroyBuffer(g_ctx, g_c);
    destroyPipeline(g_ctx, g_reducePipeline);
    destroyBuffer(g_ctx, g_reduceIn);
    destroyBuffer(g_ctx, g_reduceOut);
    destroyVkContext(g_ctx);
}
