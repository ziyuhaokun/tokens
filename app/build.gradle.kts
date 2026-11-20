plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.ziyuhaokun.tokens"
    compileSdk = 37
    compileSdkMinor = 2

    defaultConfig {
        applicationId = "com.ziyuhaokun.tokens"
        minSdk = 26
        targetSdk = 37
        versionCode = 16
        versionName = "0.11.1"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    signingConfigs {
        // 本机有 ~/.gradle/gradle.properties 里的四项才创建；CI/别人机器没有就跳过，
        // 只是打不出可发布的包，不影响 assembleDebug。
        val storePath = providers.gradleProperty("TOKENS_STORE_FILE").orNull
        if (storePath != null) {
            create("release") {
                storeFile = rootProject.file(storePath)
                storePassword = providers.gradleProperty("TOKENS_STORE_PASSWORD").orNull
                keyAlias = providers.gradleProperty("TOKENS_KEY_ALIAS").orNull
                keyPassword = providers.gradleProperty("TOKENS_KEY_PASSWORD").orNull
            }
        }
    }

    buildTypes {
        release {
            // 由公开 debug 证书签名的包不能对外发：任何人都能造同包名、同签名的“升级包”。
            // 签名参数从本机 gradle.properties / 环境变量读，绝不入库。
            signingConfigs.findByName("release")?.let { signingConfig = it }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)

    implementation(libs.datastore.preferences)
    implementation(libs.work.runtime)
    implementation(libs.glance.appwidget)
    implementation(libs.okhttp)

    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.json)
}
