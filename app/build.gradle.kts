plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "me.huanjue.cloudmail"
    compileSdk = 34

    defaultConfig {
        applicationId = "me.huanjue.cloudmail"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "0.1.2-Beta"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("KEYSTORE_PATH")
            if (!keystorePath.isNullOrBlank()) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 有 keystore 才签名，否则打出 unsigned 包
            if (System.getenv("KEYSTORE_PATH")?.isNotBlank() == true) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    packaging {
        resources {
            // BouncyCastle 三个 jar（bcprov/bcpg/bcutil）都带了
            // META-INF/versions/9/OSGI-INF/MANIFEST.MF，打包时去重；
            // Android 不用 multi-release 的版本化类，一并排除也无影响
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    debugImplementation(libs.androidx.ui.tooling)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)
    implementation(libs.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    // PGP：BouncyCastle（纯 Java，内置实现）+ 私钥加密存储
    implementation(libs.bcprov)
    implementation(libs.bcpg)
    implementation(libs.security.crypto)
}
