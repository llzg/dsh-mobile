pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        // dsh-mobile: Maven Central mirror via Aliyun (direct Central is blocked from this network)
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        // dsh-mobile: Maven Central mirror via Aliyun (direct Central is blocked from this network)
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        mavenCentral()
    }
}

rootProject.name = "dsh-mobile"

include(":app")
include(":core")
include(":mock-harness")
