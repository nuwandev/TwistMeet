plugins {
    // Lets Gradle auto-provision the Java 25 toolchain (via the Foojay Disco API) on any
    // machine that doesn't already have it installed, including CI.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "twistmeet-api"
