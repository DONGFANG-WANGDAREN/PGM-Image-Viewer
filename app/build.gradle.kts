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
        versionCode = 26_0814_17_1
        versionName = "26_0814_17_1.ICARUS"

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
    implementation(libs.nanohttpd)
    implementation(libs.zxing.core)
    implementation(libs.zxing.android.embedded)
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}
