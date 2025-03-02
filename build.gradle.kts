plugins {
    `maven-publish`
    signing
    kotlin("jvm") version "1.9.20"
    id("io.codearte.nexus-staging") version "0.30.0"
}

group = "com.botbye"
version = "0.0.4-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    api("com.squareup.okhttp3:okhttp:4.12.0")
    api("com.fasterxml.jackson.core:jackson-databind:2.15.3")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
}

tasks {
    register<Javadoc>("withJavadoc")

    register<Jar>("withJavadocJar") {
        archiveClassifier.set("javadoc")
        dependsOn(named("withJavadoc"))
        val destination = named<Javadoc>("withJavadoc").get().destinationDir
        from(destination)
    }

    register<Jar>("withSourcesJar") {
        archiveClassifier.set("sources")
        from(project.sourceSets.getByName("main").java.srcDirs)
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenKotlin") {
            artifactId = "kotlin-module"
            from(components["kotlin"])
            pom {
                packaging = "jar"
                name.set("BotBye Kotlin module")
                url.set("https://github.com/botbye/botbye-kotlin-module")
                description.set("Kotlin module for integration with botbye.com")

                licenses {
                    license {
                        name.set("MIT License")
                        url.set("http://www.opensource.org/licenses/mit-license.php")
                    }
                }

                scm {
                    connection.set("scm:https://github.com/botbye/botbye-kotlin-module.git")
                    developerConnection.set("scm:git@github.com:botbye/botbye-kotlin-module.git")
                    url.set("https://botbye.com/")
                }

                developers {
                    developer {
                        id.set("BotBye")
                        name.set("BotBye")
                        email.set("https://botbye.com/")
                    }
                }
            }

            artifact(tasks.named<Jar>("withJavadocJar"))
            artifact(tasks.named<Jar>("withSourcesJar"))
        }
    }
    repositories {
        maven {
            val releasesUrl = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            val snapshotsUrl = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
            url = if (project.version.toString().endsWith("SNAPSHOT")) snapshotsUrl else releasesUrl
            credentials {
                username = project.properties["ossrhUsername"].toString()
                password = project.properties["ossrhPassword"].toString()
            }
        }
    }
}

signing {
    sign(publishing.publications["mavenKotlin"])
}

nexusStaging {
    serverUrl = "https://s01.oss.sonatype.org/service/local/"
    username = project.properties["ossrhUsername"].toString()
    password = project.properties["ossrhPassword"].toString()
}
