plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.greetingwidget"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.greetingwidget"
        minSdk = 26          // Android 8.0；选 26 是为了能用纯 XML 的自适应图标，不需要 PNG
        targetSdk = 34       // Android 14，和 Pixel 5 对齐
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    // WorkManager：可选的定时兜底刷新（系统最小周期 15 分钟）
    implementation("androidx.work:work-runtime-ktx:2.9.0")
}
