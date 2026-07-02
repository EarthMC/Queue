plugins {
    id("java-library")
    alias(libs.plugins.conventions.publishing)
}

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")

    maven("https://repo.earthmc.net/public") {
        mavenContent { includeGroup("net.earthmc.mycelium") }
    }
}

dependencies {
    compileOnly(libs.velocity.api)
    compileOnly(libs.mycelium)
}

java.sourceCompatibility = JavaVersion.VERSION_21

earthmcPublish {
    public = true
}
