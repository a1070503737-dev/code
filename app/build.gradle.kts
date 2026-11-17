plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Explicit build input only. Never use the GitHub credential of the build environment.
val bundledUpdateToken = providers.gradleProperty("ledgerUpdateTokenFile").orNull?.let { path ->
    val tokenFile = rootProject.file(path)
    require(tokenFile.isFile) { "The specified update token file does not exist" }
    tokenFile.readText().trim().also { token ->
        require(token.matches(Regex("github_pat_[A-Za-z0-9_]{20,500}"))) {
            "Use a dedicated fine-grained GitHub token restricted to this repository with Contents: Read-only"
        }
    }
}.orEmpty()

android {
    namespace = "com.zhuangxiu.ledger"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.zhuangxiu.ledger"
        minSdk = 26
        targetSdk = 37
        versionCode = 10
        versionName = "1.0.9"
        buildConfigField("String", "BUNDLED_GITHUB_TOKEN", "\"\"")
    }

    buildTypes {
        release {
            // The field is deliberately embedded in this APK and is extractable by its recipients.
            buildConfigField("String", "BUNDLED_GITHUB_TOKEN", "\"$bundledUpdateToken\"")
            // 发布包开启混淆和资源压缩，减小 APK 体积
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        // Robolectric 需要读取 Android 资源
        unitTests.isIncludeAndroidResources = true
    }
}

kotlin {
    compilerOptions {
        // 项目里用到的少量实验性 Compose API，统一在这里声明同意使用
        optIn.addAll(
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.foundation.ExperimentalFoundationApi",
            "androidx.compose.foundation.layout.ExperimentalLayoutApi",
        )
    }
}

ksp {
    // 导出 Room 数据库结构，方便以后写迁移
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    // Compose + Material 3
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    // Hilt 依赖注入
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Room 本地数据库
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // DataStore 设置项 / WorkManager 提醒
    implementation(libs.datastore.preferences)
    implementation(libs.work.runtime)

    // CameraX 拍照 / Coil 图片加载
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    implementation(libs.coil.compose)

    // 单元测试
    testImplementation(platform(libs.compose.bom))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
