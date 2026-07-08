plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.DONGFANG_WANGDAREN.Reader_RX"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.DONGFANG_WANGDAREN.Reader_RX"
        minSdk = 33
        targetSdk = 36
        versionCode = 26_0709_00_1
        versionName = "26_0709_00_1.NANJING"

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
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.commonmark)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.jxl)
    implementation(libs.pdf.viewer)
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}
