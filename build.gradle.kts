import org.jreleaser.model.Active

plugins {
    `maven-publish`
    signing
    id("java-library")
    id("java")
    kotlin("jvm") version "1.9.20"
    id("org.jreleaser") version "1.18.0"
}

group = "com.botbye"
version = "0.0.9"

repositories {
    mavenCentral()
}

java {
    withSourcesJar()
    withJavadocJar()
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api("com.squareup.okhttp3:okhttp:4.12.0")
    api("com.fasterxml.jackson.core:jackson-databind:2.18.2")
    api("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.18.2")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    api("org.slf4j:slf4j-api:2.0.16")
}

publishing {
    publications {
        create<MavenPublication>("mavenKotlin") {
            artifactId = "kotlin-module"
            from(components["java"])

            pom {
                packaging = "jar"
                name.set("BotBye Kotlin module")
                url.set("https://github.com/botbye/${project.rootProject.name}")
                description.set("Kotlin module for integration with botbye.com")

                licenses {
                    license {
                        name.set("MIT License")
                        url.set("http://www.opensource.org/licenses/mit-license.php")
                    }
                }

                scm {
                    connection.set("scm:https://github.com/botbye/${project.rootProject.name}.git")
                    developerConnection.set("scm:git@github.com:botbye/${project.rootProject.name}")
                    url.set("https://github.com/botbye/${project.rootProject.name}")
                }

                developers {
                    developer {
                        id.set("BotBye")
                        name.set("BotBye")
                        email.set("accounts@botbye.com")
                    }
                }
            }
        }
    }
    repositories {
        maven {
            setUrl(layout.buildDirectory.dir("staging-deploy"))
        }
    }
}

jreleaser {
    release {
        github {
            name = "botbye-kotlin-module"
            token = "env:GITHUB_TOKEN"
        }
    }

    signing {
        active = Active.ALWAYS
        armored = true
        verify = true
    }

    project {
        inceptionYear = "2023"
        author("@botbye")
    }

    deploy {
        maven {
            mavenCentral.create("sonatype") {
                active = Active.ALWAYS
                url = "https://central.sonatype.com/api/v1/publisher"
                stagingRepository(layout.buildDirectory.dir("staging-deploy").get().toString())
                setAuthorization("Basic")
                retryDelay = 60
                sign = true
                checksums = true
                sourceJar = true
                javadocJar = true
            }
        }
    }
}
