package dev.computegym.hello;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertTrue;

/** Runs the real Vulkan pipeline on-device, reproducing the scenarios verified by hand this session. */
@RunWith(AndroidJUnit4.class)
public class VulkanBridgeInstrumentedTest {

    private static final int N = 1024;
    private static final int LOCAL_SIZE = 64;

    @Before
    public void init() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertTrue("nativeInit failed", VulkanBridge.nativeInit(context.getAssets()));
    }

    @After
    public void shutdown() {
        VulkanBridge.nativeShutdown();
    }

    @Test
    public void init_reportsMaliDevice() {
        assertTrue(VulkanBridge.nativeGetDeviceName().contains("Mali"));
    }

    @Test
    public void vectorAdd_fullDispatch_allElementsCorrect() {
        float[] a = new float[N];
        float[] b = new float[N];
        for (int i = 0; i < N; i++) {
            a[i] = i;
            b[i] = 2f * i;
        }

        float[] c = VulkanBridge.nativeRunVectorAdd(a, b, N / LOCAL_SIZE);
        assertTrue(KernelMath.vectorAddPasses(a, b, c, N));
    }

    @Test
    public void vectorAdd_underDispatch_onlyPrefixComputed() {
        float[] a = new float[N];
        float[] b = new float[N];
        for (int i = 0; i < N; i++) {
            a[i] = i;
            b[i] = 2f * i;
        }

        int groups = 4;
        float[] c = VulkanBridge.nativeRunVectorAdd(a, b, groups);
        assertTrue(KernelMath.vectorAddPasses(a, b, c, groups * LOCAL_SIZE));
    }

    @Test
    public void reduction_fullDispatch_matchesExpectedTotal() {
        float[] input = new float[N];
        for (int i = 0; i < N; i++) input[i] = i;

        float[] partialSums = VulkanBridge.nativeRunReduction(input, N / LOCAL_SIZE);
        assertTrue(KernelMath.reductionPasses(partialSums, KernelMath.reductionExpectedTotal(N)));
    }

    @Test
    public void reduction_underDispatch_partialSumMatchesSubset() {
        float[] input = new float[N];
        for (int i = 0; i < N; i++) input[i] = i;

        int groups = 8;
        float[] partialSums = VulkanBridge.nativeRunReduction(input, groups);
        assertTrue(KernelMath.reductionPasses(partialSums, KernelMath.reductionExpectedTotal(groups * LOCAL_SIZE)));
    }
}
