plugins {
    `java-library`
}

version = "1.0.0"
group = "me.mykindos.betterpvp.network"
description = "What servers and network services put on NATS: subjects, the message envelope and directory keys"

// No Bukkit here. The standalone services depend on this module and speak the same format as the servers.
dependencies {
    api(libs.jackson.core)
    api(libs.jackson.annotations)
    api(libs.jackson.databind)
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
