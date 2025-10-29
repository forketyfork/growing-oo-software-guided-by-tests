plugins {
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.smack)
    implementation(libs.smack.extensions)
    implementation(libs.smack.tcp)

    testImplementation(libs.windowlicker)
    testImplementation(libs.smack)
    testImplementation(libs.smack.tcp)
    testImplementation(libs.smack.im)
    testImplementation(libs.smack.extensions)
    testImplementation(libs.jmock)
}

testing {
    suites {
        // Configure the built-in test suite
        val test by getting(JvmTestSuite::class) {
            // Use JUnit Jupiter test framework
            useJUnitJupiter("6.0.0")
        }
    }
}

// Configure JVM args for all tasks
tasks.withType<JavaExec> {
    systemProperty("java.util.logging.config.file", "src/main/resources/logging.properties")
}

tasks.withType<Test> {
    systemProperty("java.util.logging.config.file", "src/main/resources/logging.properties")
    
    testLogging {
        events("passed", "skipped", "failed", "standardOut", "standardError")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showExceptions = true
        showCauses = true
        showStackTraces = true
        showStandardStreams = true
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.withType<JavaCompile> {
    options.compilerArgs.addAll(arrayOf("-Xlint:all", "-Xlint:-serial"))
}

application {
    mainClass = "me.forketyfork.growing.Main"
}
