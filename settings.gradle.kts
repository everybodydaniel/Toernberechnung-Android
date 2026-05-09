pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Törnberechnung"
include(":app")

// ══════════════════════════════════════════════════════════════
// FIX: Redirect build output OUTSIDE OneDrive to avoid file-lock errors.
// OneDrive syncs placeholder files that Gradle cannot delete/snapshot.
// Only applied locally (skipped in CI environments).
// ══════════════════════════════════════════════════════════════
if (System.getenv("CI") == null) {
    gradle.beforeProject {
        project.layout.buildDirectory.set(file("C:/temp/toern-build/${project.name}"))
    }
}
 