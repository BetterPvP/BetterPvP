plugins {
    `java-library`
}

version = "1.0.0"
group = "me.mykindos.betterpvp.sessions"
description = "What servers and proxies send the sessions service, and the client a proxy uses to send it"

// No Bukkit or Velocity here, so the service, the servers and the proxies share it.
dependencies {
    api(project(":network-protocol"))
    api(libs.jnats) {
        exclude(group = "org.bouncycastle")
    }
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    compileOnly(libs.annotations)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
