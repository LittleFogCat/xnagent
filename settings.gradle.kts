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
        val isCI = System.getenv("CI") == "true"

        if (isCI) {
            // CI 环境用官方源，规避第三方镜像偶发的 502
            google()
            mavenCentral()
        } else {
            // 本地开发用阿里云镜像加速
            maven { url = uri("https://maven.aliyun.com/repository/google") }
            maven { url = uri("https://maven.aliyun.com/repository/public") }
            google()
            mavenCentral()
        }
    }
}

rootProject.name = "XNAgent"
include(":app")
 