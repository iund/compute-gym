plugins {
    id("com.android.application")
}

val androidHome = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
    ?: "${System.getProperty("user.home")}/Library/Android/sdk"
val ndkDir = System.getenv("ANDROID_NDK_HOME") ?: "$androidHome/ndk/25.2.9519653"
val osName = System.getProperty("os.name")
val hostTag = when {
    osName.contains("Mac") -> "darwin-x86_64"
    osName.contains("Linux") -> "linux-x86_64"
    else -> "windows-x86_64"
}
val glslc = "$ndkDir/shader-tools/$hostTag/glslc"
val clangxx = "$ndkDir/toolchains/llvm/prebuilt/$hostTag/bin/aarch64-linux-android26-clang++"

android {
    namespace = "dev.computegym.hello"
    compileSdk = 36
    ndkVersion = "25.2.9519653"

    defaultConfig {
        applicationId = "dev.computegym.hello"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    buildTypes {
        debug {
            isJniDebuggable = true
        }
        release {
            isMinifyEnabled = false
        }
    }

    sourceSets {
        getByName("main") {
            assets.srcDirs("src/main/assets", layout.buildDirectory.dir("generated/shaders"))
            jniLibs.srcDirs(layout.buildDirectory.dir("generated/jni"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

val shaderOutDir = layout.buildDirectory.dir("generated/shaders")
val jniOutDir = layout.buildDirectory.dir("generated/jni/arm64-v8a")

val shadersDir = file("src/main/shaders")

val compileComputeShader = tasks.register("compileComputeShader") {
    inputs.dir(shadersDir)
    outputs.dir(shaderOutDir)
    doLast {
        val outDir = shaderOutDir.get().asFile
        outDir.mkdirs()
        shadersDir.listFiles { f -> f.extension == "comp" }?.forEach { src ->
            val proc = ProcessBuilder(
                glslc, "--target-env=vulkan1.3",
                "-o", outDir.resolve("${src.nameWithoutExtension}.spv").absolutePath,
                src.absolutePath
            ).inheritIO().start()
            check(proc.waitFor() == 0) { "glslc failed for ${src.name}" }
        }
    }
}

val libcxxShared = "$ndkDir/toolchains/llvm/prebuilt/$hostTag/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so"

val compileNativeLib = tasks.register<Exec>("compileNativeLib") {
    val cppDir = file("src/main/cpp")
    val sources = listOf(
        "native_lib.cpp",
        "vk_context.cpp",
        "vk_buffers.cpp",
        "vk_pipeline.cpp",
        "vk_dispatch.cpp"
    ).map { cppDir.resolve(it).absolutePath }
    val outFile = jniOutDir.get().asFile.resolve("libgpucompute.so")
    inputs.dir(cppDir)
    outputs.file(outFile)
    outputs.file(jniOutDir.get().asFile.resolve("libc++_shared.so"))
    doFirst {
        outFile.parentFile.mkdirs()
        file(libcxxShared).copyTo(outFile.parentFile.resolve("libc++_shared.so"), overwrite = true)
    }
    commandLine(
        listOf(
            clangxx,
            "-std=c++17", "-O2", "-fPIC", "-shared",
            "-DANDROID",
            "-I$cppDir"
        ) + sources + listOf(
            "-landroid", "-lvulkan", "-llog",
            "-o", outFile.absolutePath
        )
    )
}

tasks.named("preBuild") {
    dependsOn(compileComputeShader, compileNativeLib)
}

tasks.matching { it.name.contains("JniLibFolders") }.configureEach {
    dependsOn(compileNativeLib)
}
tasks.matching { it.name.contains("Assets") && !it.name.contains("UnitTest") }.configureEach {
    dependsOn(compileComputeShader)
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
