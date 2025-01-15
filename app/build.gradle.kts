plugins {
    application
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(libs.windowlicker)
}

testing {
    suites {
        // Configure the built-in test suite
        val test by getting(JvmTestSuite::class) {
            // Use JUnit Jupiter test framework
            useJUnitJupiter("5.10.3")
        }
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

application {
    mainClass = "me.forketyfork.growing.Main"
}
