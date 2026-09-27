package dev.computegym.hello;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {

    private static final int N = 1024;
    private static final int LOCAL_SIZE = 64;

    /** Kept in sync by hand with src/main/shaders/vector_add.comp. */
    private static final String VECTOR_ADD_SOURCE =
            "#version 450\n\n" +
            "layout(local_size_x = 64) in;\n\n" +
            "layout(std430, binding = 0) readonly buffer A { float a[]; };\n" +
            "layout(std430, binding = 1) readonly buffer B { float b[]; };\n" +
            "layout(std430, binding = 2) writeonly buffer C { float c[]; };\n\n" +
            "void main() {\n" +
            "    uint i = gl_GlobalInvocationID.x;\n" +
            "    if (i >= a.length()) return;\n" +
            "    c[i] = a[i] + b[i];\n" +
            "}\n";

    /** Kept in sync by hand with src/main/shaders/reduction.comp. */
    private static final String REDUCTION_SOURCE =
            "#version 450\n\n" +
            "layout(local_size_x = 64) in;\n\n" +
            "layout(std430, binding = 0) readonly buffer Input { float data[]; };\n" +
            "layout(std430, binding = 1) writeonly buffer Output { float partialSums[]; };\n\n" +
            "shared float tile[64];\n\n" +
            "void main() {\n" +
            "    uint tid = gl_LocalInvocationID.x;\n" +
            "    uint gid = gl_GlobalInvocationID.x;\n" +
            "    tile[tid] = (gid < data.length()) ? data[gid] : 0.0;\n" +
            "    barrier();\n" +
            "    for (uint stride = 32u; stride > 0u; stride >>= 1u) {\n" +
            "        if (tid < stride) tile[tid] += tile[tid + stride];\n" +
            "        barrier();\n" +
            "    }\n" +
            "    if (tid == 0u) partialSums[gl_WorkGroupID.x] = tile[0];\n" +
            "}\n";

    private enum Kernel { VECTOR_ADD, REDUCTION }
    private Kernel activeKernel = Kernel.VECTOR_ADD;
    private boolean initialized = false;

    private static final class Entry {
        final String category, name, signature, cost, detail;
        Entry(String category, String name, String signature, String cost, String detail) {
            this.category = category;
            this.name = name;
            this.signature = signature;
            this.cost = cost;
            this.detail = detail;
        }
    }

    private static final Entry[] PALETTE = {
            new Entry("Invocation & Indexing", "gl_GlobalInvocationID", "uvec3 — index across the whole dispatch", "~1",
                    "The (x, y, z) index of this invocation across every workgroup in the dispatch. " +
                            "For a 1-D kernel like vector_add, only .x is used — it's the element index each thread computes."),
            new Entry("Invocation & Indexing", "gl_LocalInvocationID", "uvec3 — index within the workgroup", "~1", null),
            new Entry("Invocation & Indexing", "gl_WorkGroupID", "uvec3 — which workgroup this is", "~1", null),
            new Entry("Invocation & Indexing", "gl_NumWorkGroups", "uvec3 — total dispatch size", "~1", null),
            new Entry("Synchronization", "barrier()", "void — wait for the whole workgroup", "sync",
                    "Blocks every invocation in the workgroup until all of them reach this call. Use it after writing to " +
                            "shared memory and before another invocation reads what you wrote."),
            new Entry("Synchronization", "memoryBarrierShared()", "void — flush shared-memory writes", "sync", null),
            new Entry("Memory & Buffers", "layout(binding=N) buffer", "declares a storage buffer", "mem", null),
            new Entry("Memory & Buffers", "shared", "declares workgroup-local memory", "mem",
                    "Memory local to one workgroup, visible to every invocation in it. Parallel Reduction uses a " +
                            "64-entry shared array — one slot per thread — so the workgroup can tree-reduce its own values " +
                            "together before writing a single partial sum back to the output buffer."),
            new Entry("Math", "dot(a, b)", "float — vector dot product", "~4", null),
            new Entry("Math", "mix(a, b, t)", "float — linear interpolation", "~2", null),
            new Entry("Math", "clamp(x, lo, hi)", "float — bound a value", "~2", null),
    };

    private LinearLayout paletteList, codeList, bufferList;
    private TextView detailBand, deviceChipText, timelineView, consoleView, kernelNameText, codeHeaderText;
    private EditText dispatchGroupsXInput;
    private Button tabBuffer, tabTimeline, tabConsole;
    private ScrollView bufferScroll;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        paletteList = findViewById(R.id.paletteList);
        codeList = findViewById(R.id.codeList);
        bufferList = findViewById(R.id.bufferList);
        detailBand = findViewById(R.id.detailBand);
        deviceChipText = findViewById(R.id.deviceChipText);
        timelineView = findViewById(R.id.timelineView);
        consoleView = findViewById(R.id.consoleView);
        dispatchGroupsXInput = findViewById(R.id.dispatchGroupsXInput);
        bufferScroll = findViewById(R.id.bufferScroll);
        tabBuffer = findViewById(R.id.tabBuffer);
        tabTimeline = findViewById(R.id.tabTimeline);
        tabConsole = findViewById(R.id.tabConsole);
        kernelNameText = findViewById(R.id.kernelNameText);
        codeHeaderText = findViewById(R.id.codeHeaderText);

        buildPalette();
        selectKernel(Kernel.VECTOR_ADD);
        wireTabs();
        kernelNameText.setOnClickListener(v -> selectKernel(
                activeKernel == Kernel.VECTOR_ADD ? Kernel.REDUCTION : Kernel.VECTOR_ADD));
        findViewById(R.id.runButton).setOnClickListener(v -> run());

        if (!VulkanBridge.nativeInit(getAssets())) {
            consoleView.setText("Vulkan init failed — see logcat (tag ComputeGym)");
            showTab(consoleView);
            return;
        }
        deviceChipText.setText(VulkanBridge.nativeGetDeviceName());
        initialized = true;
        run();
    }

    private void buildPalette() {
        String category = null;
        for (Entry e : PALETTE) {
            if (!e.category.equals(category)) {
                category = e.category;
                paletteList.addView(header(category));
            }
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(12), dp(8), dp(12), dp(8));
            row.setClickable(true);
            row.setBackgroundColor(color(R.color.panel));

            LinearLayout top = new LinearLayout(this);
            top.setOrientation(LinearLayout.HORIZONTAL);
            top.addView(text(e.name, R.color.text, 12, true, 1f));
            top.addView(text(e.cost, R.color.accent_text, 10, false, 0f));
            row.addView(top);
            row.addView(text(e.signature, R.color.faint, 10, false, 0f));

            row.setOnClickListener(v -> detailBand.setText(e.detail != null
                    ? e.name + " — " + e.detail
                    : e.name + ": " + e.signature));
            paletteList.addView(row);
            paletteList.addView(divider());
        }
    }

    private void selectKernel(Kernel kernel) {
        activeKernel = kernel;
        kernelNameText.setText((kernel == Kernel.VECTOR_ADD ? "Vector Add" : "Parallel Reduction") + "  ▾");
        codeHeaderText.setText(kernel == Kernel.VECTOR_ADD ? "vector_add.comp" : "reduction.comp");
        buildCodeView(kernel == Kernel.VECTOR_ADD ? VECTOR_ADD_SOURCE : REDUCTION_SOURCE);
        if (initialized) run();
    }

    private void buildCodeView(String source) {
        codeList.removeAllViews();
        String[] lines = source.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(dp(12), dp(1), dp(12), dp(1));

            TextView lineNo = new TextView(this);
            lineNo.setText(String.valueOf(i + 1));
            lineNo.setTextColor(color(R.color.faint));
            lineNo.setTextSize(12);
            lineNo.setTypeface(android.graphics.Typeface.MONOSPACE);
            lineNo.setWidth(dp(24));
            lineNo.setGravity(Gravity.END);
            row.addView(lineNo);

            TextView src = new TextView(this);
            src.setText("  " + lines[i]);
            src.setTextColor(color(R.color.text));
            src.setTextSize(12);
            src.setTypeface(android.graphics.Typeface.MONOSPACE);
            row.addView(src);

            codeList.addView(row);
        }
    }

    private void wireTabs() {
        tabBuffer.setOnClickListener(v -> showTab(bufferScroll));
        tabTimeline.setOnClickListener(v -> showTab(timelineView));
        tabConsole.setOnClickListener(v -> showTab(consoleView));
        showTab(bufferScroll);
    }

    private void showTab(View selected) {
        bufferScroll.setVisibility(selected == bufferScroll ? View.VISIBLE : View.GONE);
        timelineView.setVisibility(selected == timelineView ? View.VISIBLE : View.GONE);
        consoleView.setVisibility(selected == consoleView ? View.VISIBLE : View.GONE);
        tabBuffer.setTextColor(color(selected == bufferScroll ? R.color.accent_text : R.color.faint));
        tabTimeline.setTextColor(color(selected == timelineView ? R.color.accent_text : R.color.faint));
        tabConsole.setTextColor(color(selected == consoleView ? R.color.accent_text : R.color.faint));
    }

    private void run() {
        if (activeKernel == Kernel.VECTOR_ADD) runVectorAdd();
        else runReduction();
    }

    private void runVectorAdd() {
        float[] a = new float[N];
        float[] b = new float[N];
        for (int i = 0; i < N; i++) {
            a[i] = i;
            b[i] = 2f * i;
        }

        int groups = parseDispatchGroups();
        float[] c = VulkanBridge.nativeRunVectorAdd(a, b, groups);
        if (c == null) {
            consoleView.setText("Dispatch failed — see logcat (tag ComputeGym)");
            showTab(consoleView);
            return;
        }

        int computed = Math.min(N, groups * LOCAL_SIZE);
        boolean pass = KernelMath.vectorAddPasses(a, b, c, computed);

        buildVectorAddBufferTable(a, b, c, computed);
        timelineView.setText(String.format(
                "Last run: %.3f ms%nDispatch: %d workgroups × %d threads%nElements computed: %d / %d",
                VulkanBridge.nativeGetElapsedMs(), groups, LOCAL_SIZE, computed, N));
        consoleView.setText(String.format(
                "Device: %s%nCompiled OK%n%s",
                VulkanBridge.nativeGetDeviceName(),
                computed == N ? (pass ? "PASS — all " + N + " elements correct" : "FAIL")
                        : "PARTIAL — only the first " + computed + " elements were recomputed this run"));
    }

    private void runReduction() {
        float[] input = new float[N];
        for (int i = 0; i < N; i++) input[i] = i;

        int groups = parseDispatchGroups();
        float[] partialSums = VulkanBridge.nativeRunReduction(input, groups);
        if (partialSums == null) {
            consoleView.setText("Dispatch failed — see logcat (tag ComputeGym)");
            showTab(consoleView);
            return;
        }

        int computed = Math.min(N, groups * LOCAL_SIZE);
        float expectedPartial = KernelMath.reductionExpectedTotal(computed);
        boolean pass = KernelMath.reductionPasses(partialSums, expectedPartial);

        float total = 0;
        for (float s : partialSums) total += s;
        buildReductionBufferTable(partialSums, total, expectedPartial);
        timelineView.setText(String.format(
                "Last run: %.3f ms%nDispatch: %d workgroups × %d threads%nElements reduced: %d / %d",
                VulkanBridge.nativeGetElapsedMs(), groups, LOCAL_SIZE, computed, N));
        consoleView.setText(String.format(
                "Device: %s%nCompiled OK%n%s",
                VulkanBridge.nativeGetDeviceName(),
                computed == N
                        ? (pass ? "PASS — reduced sum matches expected total" : "FAIL")
                        : (pass ? "PARTIAL — only the first " + computed + " elements were reduced, sum matches that subset"
                                : "FAIL")));
    }

    private int parseDispatchGroups() {
        try {
            int v = Integer.parseInt(dispatchGroupsXInput.getText().toString().trim());
            return Math.max(1, v);
        } catch (NumberFormatException e) {
            return N / LOCAL_SIZE;
        }
    }

    private void buildVectorAddBufferTable(float[] a, float[] b, float[] c, int computed) {
        bufferList.removeAllViews();
        bufferList.addView(bufferRow("idx", "A", "B", "C", R.color.faint, true));
        for (int i = 0; i < N; i++) {
            boolean stale = i >= computed;
            bufferList.addView(bufferRow(String.valueOf(i),
                    fmt(a[i]), fmt(b[i]), fmt(c[i]),
                    stale ? R.color.faint : R.color.text, false));
        }
    }

    private void buildReductionBufferTable(float[] partialSums, float total, float expected) {
        bufferList.removeAllViews();
        bufferList.addView(bufferRow("workgroup", "partial sum", "", "", R.color.faint, true));
        for (int i = 0; i < partialSums.length; i++) {
            bufferList.addView(bufferRow(String.valueOf(i), fmt(partialSums[i]), "", "", R.color.text, false));
        }
        bufferList.addView(divider());
        bufferList.addView(bufferRow("total", fmt(total), "", "", R.color.accent_text, true));
        bufferList.addView(bufferRow("expected", fmt(expected), "", "", R.color.faint, true));
    }

    private LinearLayout bufferRow(String idx, String av, String bv, String cv, int colorRes, boolean isHeader) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(10), dp(3), dp(10), dp(3));
        float size = isHeader ? 10 : 11;
        row.addView(text(idx, colorRes, size, isHeader, 1f));
        row.addView(text(av, colorRes, size, isHeader, 1f));
        row.addView(text(bv, colorRes, size, isHeader, 1f));
        row.addView(text(cv, colorRes, size, isHeader, 1f));
        return row;
    }

    private static String fmt(float v) {
        return String.format("%.1f", v);
    }

    private TextView header(String label) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setAllCaps(true);
        t.setTextColor(color(R.color.faint));
        t.setTextSize(9);
        t.setPadding(dp(12), dp(8), dp(12), dp(4));
        return t;
    }

    private View divider() {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
        v.setBackgroundColor(color(R.color.line));
        return v;
    }

    private TextView text(String s, int colorRes, float sizeSp, boolean bold, float weight) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(color(colorRes));
        t.setTextSize(sizeSp);
        t.setTypeface(android.graphics.Typeface.MONOSPACE, bold ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        if (weight > 0) {
            t.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight));
        }
        return t;
    }

    private int color(int colorRes) {
        return getResources().getColor(colorRes, getTheme());
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        VulkanBridge.nativeShutdown();
        super.onDestroy();
    }
}
