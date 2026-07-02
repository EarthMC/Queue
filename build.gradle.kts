plugins {
    id("java")
    alias(libs.plugins.shadow)
    alias(libs.plugins.conventions.java)
    alias(libs.plugins.conventions.publishing) apply false
    id("jacoco")
}

repositories {
    mavenCentral()

    maven("https://repo.papermc.io/repository/maven-public/")

    maven("https://repo.earthmc.net/public") {
        mavenContent { includeGroup("net.earthmc.mycelium") }
    }
}

val mockitoAgent = configurations.create("mockitoAgent")

dependencies {
    implementation(project(":mycelium-api"))
    compileOnly(libs.velocity.api)
    annotationProcessor(libs.velocity.api)
    implementation(libs.mysql.connector)
    compileOnly(libs.mycelium)

    testImplementation(libs.junit)
    testImplementation(libs.mockito)
    testImplementation(libs.velocity.api)
    testRuntimeOnly(libs.junit.launcher)
    testRuntimeOnly(libs.junit.engine)

    mockitoAgent(libs.mockito) { isTransitive = false }
}

java.sourceCompatibility = JavaVersion.VERSION_21

tasks {
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
        jvmArgs.add("-javaagent:${mockitoAgent.asPath}")
        finalizedBy(jacocoTestReport)
    }

    jacocoTestReport {
        dependsOn(test)
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
