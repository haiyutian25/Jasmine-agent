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
dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
  }
}

rootProject.name = "jasmine"
include(":app")
include(":core:data")
include(":core:database")
include(":core:navigation")
include(":core:network")
include(":core:ui")
include(":feature:main:api")
include(":feature:main:impl")
include(":feature:settings:api")
include(":feature:settings:impl")
