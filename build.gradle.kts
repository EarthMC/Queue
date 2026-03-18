plugins {
    id("java")
    alias(libs.plugins.shadow)
}

repositories {
    mavenCentral()

    maven {
        name = "paper"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
}

dependencies {
    compileOnly(libs.velocity.api)
    annotationProcessor(libs.velocity.api)
    implementation(libs.mysql.connector)

    testImplementation(libs.junit)
    testImplementation(libs.mockito)
    testImplementation(libs.velocity.api)
    testRuntimeOnly(libs.junit.launcher)
    testRuntimeOnly(libs.junit.engine)
}

java.sourceCompatibility = JavaVersion.VERSION_21

tasks {
    assemble {
        dependsOn(shadowJar)
    }

    shadowJar {
        archiveClassifier.set("")

        relocate("com.mysql", "net.earthmc.queue.libs.mysql")
    }

    compileJava {
        options.encoding = "UTF-8"
        options.release.set(21)

        dependsOn(generateTemplates)
    }

    test {
        useJUnitPlatform()
    }
}

val generateTemplates = tasks.register<Copy>("generateTemplates") {
    val props = mapOf("version" to project.version)
    inputs.properties(props)

    from(file("src/main/templates"))
    into(layout.buildDirectory.dir("generated/sources/templates"))
    expand(props)
}

java.sourceSets["main"].java.srcDir(generateTemplates.map { it.outputs })
