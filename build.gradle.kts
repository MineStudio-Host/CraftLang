plugins {
    kotlin("jvm") version "2.0.21"
}

group = "host.minestudio"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    compileOnly("net.minestom:minestom-snapshots:7320437640")    // Minestom
    implementation("org.slf4j:slf4j-api:2.0.16")                    // SLF4J API (Logging)
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}