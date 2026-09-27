package dev.computegym.hello;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KernelMathTest {

    private static float[] range(int n, float scale) {
        float[] out = new float[n];
        for (int i = 0; i < n; i++) out[i] = i * scale;
        return out;
    }

    @Test
    void vectorAddPasses_trueForCorrectFullBuffer() {
        float[] a = range(1024, 1f);
        float[] b = range(1024, 2f);
        float[] c = range(1024, 3f);
        assertTrue(KernelMath.vectorAddPasses(a, b, c, 1024));
    }

    @Test
    void vectorAddPasses_falseWhenAnElementIsWrong() {
        float[] a = range(1024, 1f);
        float[] b = range(1024, 2f);
        float[] c = range(1024, 3f);
        c[500] = 0f;
        assertFalse(KernelMath.vectorAddPasses(a, b, c, 1024));
    }

    @Test
    void vectorAddPasses_onlyChecksTheComputedPrefix() {
        float[] a = range(1024, 1f);
        float[] b = range(1024, 2f);
        float[] c = range(1024, 3f);
        c[600] = -1f; // wrong, but past the computed prefix
        assertTrue(KernelMath.vectorAddPasses(a, b, c, 256));
    }

    @Test
    void reductionExpectedTotal_matchesKnownSumOfFirst1024Integers() {
        assertEquals(523776f, KernelMath.reductionExpectedTotal(1024));
    }

    @Test
    void reductionExpectedTotal_partialRangeMatchesSubsetSum() {
        assertEquals(130816f, KernelMath.reductionExpectedTotal(512));
    }

    @Test
    void reductionPasses_trueWithinEpsilon() {
        float[] partials = {100f, 200f, 300.005f};
        assertTrue(KernelMath.reductionPasses(partials, 600f));
    }

    @Test
    void reductionPasses_falseWhenSumIsWrong() {
        float[] partials = {100f, 200f, 300f};
        assertFalse(KernelMath.reductionPasses(partials, 999f));
    }
}
