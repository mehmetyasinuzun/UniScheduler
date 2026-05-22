import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

// Load Supabase keys from local.properties (never commit this file)
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}

android {
    namespace   = "com.unischeduler"
    compileSdk  = 34

    defaultConfig {
        applicationId = "com.unischeduler"
        minSdk        = 26
        targetSdk     = 34
        versionCode   = 9
        versionName   = "1.2.8"

        buildConfigField("String", "SUPABASE_URL",      "\"${localProps["SUPABASE_URL"] ?: ""}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${localProps["SUPABASE_ANON_KEY"] ?: ""}\"")
    }

    signingConfigs {
        // Release signing reads from local.properties so the keystore credentials
        // never end up in version control. See TESLIM_REHBERI.md for setup.
        create("release") {
            val keystorePath  = localProps["KEYSTORE_FILE"] as String?
            val keystorePass  = localProps["KEYSTORE_PASSWORD"] as String?
            val keyAliasValue = localProps["KEY_ALIAS"] as String?
            val keyPassValue  = localProps["KEY_PASSWORD"] as String?
            if (!keystorePath.isNullOrBlank() && !keystorePass.isNullOrBlank()
                && !keyAliasValue.isNullOrBlank() && !keyPassValue.isNullOrBlank()) {
                storeFile     = file(keystorePath)
                storePassword = keystorePass
                keyAlias      = keyAliasValue
                keyPassword   = keyPassValue
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            isDebuggable    = true
        }
        release {
            isMinifyEnabled    = true
            isShrinkResources  = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Use the user's release keystore if configured. Otherwise fall back
            // to the debug keystore so the resulting APK is at least installable
            // for internal testing — Android refuses to install unsigned APKs.
            // Production releases MUST set KEYSTORE_FILE in local.properties.
            val rel = signingConfigs.getByName("release")
            signingConfig = if (rel.storeFile != null) rel
                            else signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        viewBinding  = true
        buildConfig  = true
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
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.viewpager2)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.security.crypto)

    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.runtime.ktx)

    implementation(libs.navigation.fragment.ktx)
    implementation(libs.navigation.ui.ktx)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // Supabase (BOM manages versions for all supabase-kt modules)
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.realtime)
    implementation(libs.ktor.client.cio)

    implementation(libs.gridlayout)

    // Excel .xlsx import/export — handled by our own MiniXlsxReader /
    // MiniXlsxWriter (zero external deps). Apache POI was removed
    // after repeated Android-only failures (see those files' headers).

    testImplementation(libs.junit)
    // Robolectric: runs Android-aware UI tests on the JVM (no emulator needed).
    // Used for Activity/Fragment smoke tests that catch layout-inflation
    // crashes, missing resources, and onCreate/onViewCreated regressions
    // without requiring a connected device.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.runner)
    // androidx.test.ext:junit gives us @AndroidJUnit4 runner that works
    // with both Robolectric (JVM unit tests) and Espresso (instrumented).
    testImplementation(libs.androidx.junit)

    androidTestImplementation(libs.androidx.junit)
}

// Robolectric needs unit tests to see Android resources (R.layout, strings,
// drawables). Without this, every smoke test fails with "Unable to find
// resource" before any assertion runs.
android.testOptions {
    unitTests.isIncludeAndroidResources = true
    unitTests.isReturnDefaultValues     = true
    unitTests.all {
        // Force the test JVM to en_US so Robolectric's native-library
        // resolver (which calls String.toLowerCase to build platform-
        // specific .so paths like "conscrypt_openjdk_jni-windows-x86_64")
        // doesn't trip on the Turkish locale's i → ı dotless conversion
        // and look for "wındows-x86_64" — a non-existent file.
        it.systemProperty("user.language", "en")
        it.systemProperty("user.country",  "US")
        it.systemProperty("file.encoding", "UTF-8")
        it.jvmArgs = listOf("-Duser.timezone=UTC")
    }
}

// Standard packaging hygiene — strip license/notice files that bundled
// libs duplicate. (POI-specific META-INF/services merge rule removed
// when we dropped POI.)
android.packagingOptions {
    resources.excludes += setOf(
        "META-INF/DEPENDENCIES",
        "META-INF/LICENSE",
        "META-INF/LICENSE.txt",
        "META-INF/NOTICE",
        "META-INF/NOTICE.txt",
        "META-INF/versions/**",
        "META-INF/*.kotlin_module"
    )
}
