import java.util.*

plugins {
    kotlin("multiplatform")
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

repositories {
    mavenCentral()
}

val javadocJar by tasks.registering(Jar::class) {
    group = JavaBasePlugin.DOCUMENTATION_GROUP
    description = "Assembles Javadoc JAR"
    archiveClassifier.set("javadoc")
    from(tasks.named("dokkaHtml"))
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
            //setUrl("https://s01.oss.sonatype.org/content/repositories/snapshots/")
            setUrl("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2")
            credentials {
                username = getExtraString("ossrhUsername")
                password = getExtraString("ossrhPassword")
            }
        }
    }
    // Configure all publications
    publications.withType<MavenPublication> {

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

kotlin {
    jvm {
        jvmToolchain(17)
        //withJava()
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }

    sourceSets {
        val jvmMain by getting {
            dependencies {
                api(project(":pg"))
                implementation("org.postgresql:postgresql:42.2.27")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.0")
                implementation("com.zaxxer:HikariCP:5.1.0")
            }
        }
    }
}

val signingTasks = tasks.withType<Sign>()
tasks.withType<AbstractPublishToMaven>().configureEach {
    dependsOn(signingTasks)
}