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
        versionCode = 19
        versionName = "0.11.4"
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
        val storePath = providers.gradleProperty("TOKENS_STORE_FILE").orNull?.takeIf { it.isNotBlank() }
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

// 发布包必须有正式签名：没配签名四项时，打 release 包 / bundle 直接失败。
// 之前会默默产出一个未签名的包，CI 里还显示成功，很容易误发。
// 只抓最终打包的两个任务，不影响 assembleDebug 和单元测试。
// 开一个局部作用域、只捕获一个 Boolean：直接引用脚本级变量会把整个脚本对象带进任务，
// configuration cache 会报「cannot serialize Gradle script object references」。
run {
    val signingConfigured = providers.gradleProperty("TOKENS_STORE_FILE").orNull?.isNotBlank() == true
    tasks.matching { it.name == "packageRelease" || it.name == "bundleRelease" }.configureEach {
        doFirst {
            check(signingConfigured) {
                "没有配置发布签名，拒绝产出未签名的 release 包。" +
                    "请在 ~/.gradle/gradle.properties 里填 TOKENS_STORE_FILE / TOKENS_STORE_PASSWORD / " +
                    "TOKENS_KEY_ALIAS / TOKENS_KEY_PASSWORD（见 README「从源码运行」）；只想试装请用 assembleDebug。"
            }
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
