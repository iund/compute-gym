# Compute Gym

A minimal native Android app that runs real Vulkan compute kernels — vector
add and a shared-memory/`barrier()` parallel reduction — and shows the buffer
results, timing, and console output. Built and verified against a Tabwee T50
tablet (Unisoc Tiger T606 / Mali-G57, Vulkan 1.3).

A standalone, non-functional preview of the UI is at [`mockup.html`](mockup.html) — open it directly in a browser.

## Quickstart

**Prerequisites**
- JDK 17+
- Android SDK with `platforms;android-36` and `build-tools;37.0.0`
- NDK **25.2.9519653** specifically (its bundled `glslc` compiles the shaders; other NDK versions may not include it)
- An Android device or emulator exposing `android.hardware.vulkan.compute` (the app targets a real Mali-G57 on a T50; behavior on other GPUs is untested)

**Setup**
```bash
git clone git@github.com:iund/compute-gym.git
cd compute-gym
echo "sdk.dir=$ANDROID_HOME" > local.properties   # or point it at your SDK path
```

**Build**
```bash
./gradlew :app:assembleDebug
```
APK output: `app/build/outputs/apk/debug/app-debug.apk`

**Install & run**
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n dev.computegym.hello/.MainActivity
```

**Test**
```bash
./gradlew :app:testDebugUnitTest          # host-JVM, no device needed
./gradlew :app:connectedDebugAndroidTest  # on a connected device — runs real GPU dispatches
```

**Debug from plain IntelliJ IDEA** (no Android plugin required)
```bash
scripts/debug_launch.sh   # installs, launches suspended, forwards the JDWP port
```
Then create a *Remote JVM Debug* run configuration targeting `localhost:8700` and attach.
