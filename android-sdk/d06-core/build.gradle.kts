plugins {
    kotlin("jvm")
    `maven-publish`
}

java {
    withSourcesJar()
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(kotlin("test-junit5"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "d06-core"
            from(components["java"])
        }
    }
}
