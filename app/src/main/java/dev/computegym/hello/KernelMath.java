package dev.computegym.hello;

/** Pure correctness checks for the compute kernels, kept free of Android/JNI so they're host-testable. */
final class KernelMath {

    private static final float EPSILON = 1e-2f;

    private KernelMath() {}

    static boolean vectorAddPasses(float[] a, float[] b, float[] c, int computed) {
        for (int i = 0; i < computed; i++) {
            if (Math.abs(c[i] - (a[i] + b[i])) > 1e-4f) return false;
        }
        return true;
    }

    static float reductionExpectedTotal(int count) {
        float total = 0;
        for (int i = 0; i < count; i++) total += i;
        return total;
    }

    static boolean reductionPasses(float[] partialSums, float expectedTotal) {
        float total = 0;
        for (float s : partialSums) total += s;
        return Math.abs(total - expectedTotal) < EPSILON;
    }
}
