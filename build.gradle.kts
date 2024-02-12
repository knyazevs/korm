import java.util.*

plugins {
    `kotlin-dsl`
    id("maven-publish")
    id("org.jetbrains.dokka") version "1.9.10"
    signing
}

// Stub secrets to let the project sync and build without the publication values set up
ext["signing.keyId"] = null
ext["signing.password"] = null
ext["signing.secretKeyRingFile"] = null
ext["ossrhUsername"] = null
ext["ossrhPassword"] = null

// Grabbing secrets from local.properties file or from environment variables, which could be used on CI
val secretPropsFile = project.rootProject.file("local.properties")
if (secretPropsFile.exists()) {
    secretPropsFile.reader().use {
        Properties().apply {
            load(it)
        }
    }.onEach { (name, value) ->
        ext[name.toString()] = value
    }
} else {
    ext["signing.keyId"] = System.getenv("SIGNING_KEY_ID")
    ext["signing.password"] = System.getenv("SIGNING_PASSWORD")
    ext["signing.secretKeyRingFile"] = System.getenv("SIGNING_SECRET_KEY_RING_FILE")
    ext["ossrhUsername"] = System.getenv("OSSRH_USERNAME")
    ext["ossrhPassword"] = System.getenv("OSSRH_PASSWORD")
}

fun getExtraString(name: String) = ext[name]?.toString()

    /*
val javadocJar by tasks.registering(Jar::class) {
    archiveClassifier.set("javadoc")
}
     */

val javadocJar by tasks.registering(Jar::class) {
    group = JavaBasePlugin.DOCUMENTATION_GROUP
    description = "Assembles Javadoc JAR"
    archiveClassifier.set("javadoc")
    from(tasks.named("dokkaHtml"))
}

buildscript {
    repositories {
        mavenCentral()
        maven {
            url = uri("https://maven.pkg.jetbrains.space/public/p/ktor/eap")
        }
    }

    dependencies {
        // kotlin
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.20")
    }
}


allprojects {
    repositories {
        mavenCentral()
        maven {
            url = uri("https://maven.pkg.jetbrains.space/public/p/ktor/eap")
        }

    }
}

publishing {
    repositories {
        /*
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/knyazevs/korm")
            credentials {
                username = System.getenv("USERNAME")
                password = System.getenv("TOKEN")
            }
        }
        */
        maven {
            name = "sonatype"
            setUrl("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            credentials {
                username = getExtraString("ossrhUsername")
                password = getExtraString("ossrhPassword")
            }
        }
    }
    // Configure all publications
    publications.withType<MavenPublication> {

        // Stub javadoc.jar artifact
        artifact(javadocJar.get())

        // Provide artifacts information requited by Maven Central
        pom {
            name.set("Korm")
            description.set("Korm is a simple ORM library for working with Postgresql via kotlin mpp")
            url.set("https://github.com/knyazevs/korm")

            licenses {
                license {
                    name.set("GPL-3.0-only")
                    url.set("https://opensource.org/licenses/gpl-3-0")
                }
            }
            developers {
                developer {
                    id.set("knyazevs")
                    name.set("Sergey Knyazev")
                    email.set("sknyazev@vk.com")
                }
            }
            scm {
                url.set("https://github.com/knyazevs/korm")
            }

        }
    }
}

// Signing artifacts. Signing.* extra properties values will be used

signing {
    sign(publishing.publications)
}