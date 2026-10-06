plugins {
    java
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.diffplug.spotless") version "8.10.2"
}

group = "com.twistmeet"
version = "0.1.0-m1"

java {
    toolchain {
        // Java 25 LTS. Auto-provisioned via the Foojay resolver (settings.gradle.kts) on any
        // machine, including CI, that doesn't already have a matching JDK installed.
        languageVersion = JavaLanguageVersion.of(25)
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    // Spring Boot 4 moved Flyway auto-configuration behind a dedicated starter; adding
    // flyway-core directly no longer triggers it. The starter pulls in flyway-core at Spring
    // Boot's managed version (12.4.0 as of 4.1.1), which has verified PostgreSQL 18 support.
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")
    // OD01 (DECISIONS.md): the official WCA scramble program — the strongest available evidence
    // of a "reviewed, maintained, puzzle-state-correct" 3x3x3 generator per 00 §7/02. GPL-3.0;
    // see DECISIONS.md for the SaaS-deployment license analysis. lib-scrambles pulls in
    // scrambler-threephase/scrambler-min2phase (runtime) for the actual 3x3x3 algorithm.
    implementation("org.worldcubeassociation.tnoodle:lib-scrambles:0.20.0")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    // Spring Boot 4.1 split TestRestTemplate out of spring-boot-test into this starter, which
    // in turn needs spring-boot-restclient on the classpath for its RestTemplateBuilder.
    testImplementation("org.springframework.boot:spring-boot-resttestclient")
    testImplementation("org.springframework.boot:spring-boot-restclient")
    // TestRestTemplate defaults to JDK HttpURLConnection, which throws HttpRetryException on a
    // streamed POST body that gets a non-2xx response (e.g. a 401 from a bad login). Apache
    // HttpClient5 on the test classpath makes Spring Boot auto-configure TestRestTemplate to use
    // it instead, which does not have that quirk.
    // Unpinned: let Spring Boot's own dependency management pick the version (5.3.1 predates
    // the TlsSocketStrategy API that Spring Boot 4's HttpComponentsHttpClientBuilder requires).
    testImplementation("org.apache.httpcomponents.client5:httpclient5")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

spotless {
    java {
        target("src/**/*.java")
        // Spotless 8.10.x's own compatibility matrix: 1.30.0 is its validated default for
        // JVM 21+ (and the minimum it requires on JVM 25+); newer google-java-format releases
        // are not yet validated against this Spotless version.
        googleJavaFormat("1.30.0")
        removeUnusedImports()
    }
}

tasks.named("check") {
    dependsOn("spotlessCheck")
}
