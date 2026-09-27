package dev.computegym.hello;

import android.content.res.AssetManager;

/** Thin JNI surface over the native Vulkan compute pipeline in libgpucompute.so. */
final class VulkanBridge {

    static {
        System.loadLibrary("gpucompute");
    }

    private VulkanBridge() {}

    /** Creates the Vulkan instance/device/compute queue and loads the compiled shader. */
    static native boolean nativeInit(AssetManager assetManager);

    /** Runs c[i] = a[i] + b[i] on the GPU and returns c. a and b must be the same length. */
    static native float[] nativeRunVectorAdd(float[] a, float[] b, int dispatchGroupsX);

    /** Tree-reduces input via shared memory + barrier(); returns one partial sum per dispatched workgroup. */
    static native float[] nativeRunReduction(float[] input, int dispatchGroupsX);

    static native String nativeGetDeviceName();

    static native double nativeGetElapsedMs();

    static native void nativeShutdown();
}
