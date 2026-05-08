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
        versionCode   = 2
        versionName   = "1.0.1"

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

    // Apache POI for Excel (.xlsx) import/export
    implementation("org.apache.poi:poi:5.2.5")
    implementation("org.apache.poi:poi-ooxml:5.2.5") {
        exclude(group = "org.apache.xmlgraphics")
    }

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
}

// Fix duplicate resource issues from Apache POI on Android
android.packagingOptions {
    resources.excludes += setOf(
        "META-INF/DEPENDENCIES",
        "META-INF/LICENSE",
        "META-INF/LICENSE.txt",
        "META-INF/NOTICE",
        "META-INF/NOTICE.txt",
        "META-INF/versions/**"
    )
}
