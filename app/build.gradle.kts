plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
}

android {
  namespace = "com.example.campushub"
  compileSdk = 37

  val ciVersionName = System.getenv("APP_VERSION_NAME")?.removePrefix("v")?.takeIf { it.isNotBlank() }
  val ciVersionCode = System.getenv("APP_VERSION_CODE")?.toIntOrNull()
  val keystorePath = System.getenv("SIGNING_KEYSTORE_PATH")
  val hasReleaseSigning = !keystorePath.isNullOrBlank() && file(keystorePath).exists()

  defaultConfig {
    applicationId = "com.example.campushub"
    minSdk = 26
    targetSdk = 37
    versionCode = ciVersionCode ?: 1
    versionName = ciVersionName ?: "1.0"
  }

  if (hasReleaseSigning) {
    signingConfigs {
      create("release") {
        storeFile = file(keystorePath)
        storePassword = System.getenv("SIGNING_STORE_PASSWORD")
        keyAlias = System.getenv("SIGNING_KEY_ALIAS")
        keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
      }
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      if (hasReleaseSigning) {
        signingConfig = signingConfigs.getByName("release")
      }
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  buildFeatures {
    compose = true
    aidl = false
    buildConfig = false
    shaders = false
  }

  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
      excludes += "/META-INF/NOTICE.md"
      excludes += "/META-INF/LICENSE.md"
      excludes += "/META-INF/NOTICE.txt"
      excludes += "/META-INF/LICENSE.txt"
    }
  }
}

kotlin {
  jvmToolchain(17)
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)

  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons.core)
  debugImplementation(libs.androidx.compose.ui.tooling)

  implementation(libs.okhttp)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.android.mail)
  implementation(libs.android.activation)

  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
}
