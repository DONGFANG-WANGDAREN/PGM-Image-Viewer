plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.DONGFANG_WANGDAREN.PGM_Image_Viewer"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.DONGFANG_WANGDAREN.PGM_Image_Viewer"
        minSdk = 33
        targetSdk = 36
        versionCode = 26_0704_22_1
        versionName = "26_0704_22_1.NANJING"

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
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}