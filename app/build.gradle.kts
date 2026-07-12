plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.DONGFANG_WANGDAREN.Station_RX"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.DONGFANG_WANGDAREN.Station_RX"
        minSdk = 33
        targetSdk = 36
        versionCode = 26_0712_12_1
        versionName = "26_0712_12_1.NANJING"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    sourceSets.getByName("main") {
        jniLibs.srcDirs("src/main/jniLibs")
    }
}

fun findNdkDirectory(localPropertiesPath: String): String? {
    // 1. local.properties ndk.dir
    val localProperties = File(localPropertiesPath)
    if (localProperties.exists()) {
        localProperties.readText().lines().forEach { line ->
            if (line.startsWith("ndk.dir=")) {
                val path = line.substringAfter("ndk.dir=").trim()
                    .replace("\\ ", " ")
                    .replace("\\\\", "/")
                val dir = File(path)
                if (dir.isDirectory) return dir.absolutePath
            }
        }
    }

    // 2. Environment variables
    System.getenv("ANDROID_NDK_HOME")?.let {
        val dir = File(it)
        if (dir.isDirectory) return dir.absolutePath
    }
    System.getenv("ANDROID_NDK")?.let {
        val dir = File(it)
        if (dir.isDirectory) return dir.absolutePath
    }

    // 3. Auto-detect under Android SDK
    val userHome = System.getProperty("user.home")
    listOf(
        "$userHome/Library/Android/sdk/ndk",
        "$userHome/Android/Sdk/ndk",
        "$userHome/android-sdk/ndk"
    ).forEach { base ->
        val baseDir = File(base)
        if (baseDir.isDirectory) {
            baseDir.listFiles()
                ?.filter { it.isDirectory && File(it, "ndk-build").exists() }
                ?.maxByOrNull { it.name }
                ?.let { return it.absolutePath }
        }
    }

    return null
}

fun findCargoExecutable(): File? {
    return System.getenv("PATH")
        ?.split(File.pathSeparator)
        ?.map { File(it, "cargo") }
        ?.firstOrNull { it.exists() }
}

tasks.register<Exec>("cargoBuild") {
    group = "build"
    description = "Build Rust server for Android"

    val localPropertiesPath = rootProject.file("local.properties").absolutePath
    val jniLibsOutputPath = file("src/main/jniLibs").absolutePath
    val rustProjectPath = file("../rust_server").absolutePath

    doFirst {
        val ndk = findNdkDirectory(localPropertiesPath)
            ?: error(
                "Android NDK not found. " +
                "Set ndk.dir in local.properties, or set ANDROID_NDK_HOME environment variable."
            )
        val cargo = findCargoExecutable()
            ?: error("cargo not found in PATH. Install Rust and cargo.")

        environment("ANDROID_NDK_HOME", ndk)
        commandLine(
            cargo.absolutePath, "ndk",
            "-t", "aarch64-linux-android",
            "-t", "x86_64-linux-android",
            "-o", jniLibsOutputPath,
            "build", "--release"
        )
    }

    workingDir = File(rustProjectPath)
}

tasks.named("preBuild").configure {
    dependsOn("cargoBuild")
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.commonmark)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.jxl)
    implementation(libs.pdf.viewer)
    implementation(libs.java.websocket)
    implementation(libs.nanohttpd)
    implementation(libs.zxing.core)
    implementation(libs.zxing.android.embedded)
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}
