plugins {
    kotlin("jvm") version "2.3.0"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("ai.koog:koog-agents-jvm:0.7.3")
    implementation("ai.koog:prompt-executor-llms-all:0.7.3")
    implementation("io.github.optimumcode:json-schema-validator:0.5.4")
    implementation(kotlin("reflect"))
    testImplementation("io.mockk:mockk:1.14.9")
    testImplementation("org.junit.jupiter:junit-jupiter:5.x")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(22)
}