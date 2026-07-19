pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    resolutionStrategy {
        eachPlugin {
            // The SafeArgs plugin doesn't publish plugin marker artifacts, so map its
            // plugin ID to the artifact that contains it.
            if (requested.id.id.startsWith("androidx.navigation.safeargs")) {
                useModule("androidx.navigation:navigation-safe-args-gradle-plugin:${requested.version}")
            }
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "debatekeeper"
include(":app")
