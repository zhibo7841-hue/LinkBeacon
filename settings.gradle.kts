pluginManagement {
    repositories {
        google()
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

rootProject.name = "NetworkToolbox"

include(":app")
include(":core:common")
include(":core:designsystem")
include(":core:network")
include(":core:permission")
include(":core:database")
include(":feature:dashboard")
include(":feature:subnet")
include(":feature:ping")
include(":feature:dns")
include(":feature:port")
include(":feature:report")
include(":feature:history")
include(":feature:lanscan")
include(":feature:traceroute")
include(":feature:webdiagnostics")
include(":feature:wifi")
