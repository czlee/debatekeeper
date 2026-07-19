plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.navigation.safeargs.kotlin)
}

android {
    namespace = "net.czlee.debatekeeper"
    compileSdk = 36

    defaultConfig {
        applicationId = "net.czlee.debatekeeper"
        minSdk = 21
        targetSdk = 36
        versionCode = 43
        versionName = "1.4.2"
        vectorDrawables.useSupportLibrary = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.txt")
        }
        debug {
            isDebuggable = true
        }
    }

    buildFeatures {
        viewBinding = true
    }

    lint {
        // Records pre-existing issues (mostly untranslated strings) so that
        // lint only fails the build on newly introduced problems.
        baseline = file("lint-baseline.xml")
    }

    testOptions {
        unitTests {
            // Robolectric needs the real resources (XML element names used by
            // the format parser are defined as string resources).
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.preference)
    implementation(libs.androidx.localbroadcastmanager)
    implementation(libs.androidx.recyclerview)
    implementation(libs.icu4j)
    implementation(libs.androidx.navigation.fragment)
    implementation(libs.androidx.navigation.ui)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}

// Robolectric pulls in a newer icu4j whose LocaleMatcher API is incompatible with the
// icu4j the app code uses; force the app's version on the test classpath so tests
// exercise the same library the app ships.
configurations.configureEach {
    if (name.contains("UnitTestRuntimeClasspath"))
        resolutionStrategy.force("com.ibm.icu:icu4j:${libs.versions.icu4j.get()}")
}
