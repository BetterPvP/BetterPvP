plugins {
    `java-library`
}

version = "1.0.0"
group = "me.mykindos.betterpvp.metrics"
description = "Micrometer metrics served to Prometheus, shared by the servers and the network services"

// No Bukkit here, so the standalone services can use it too.
dependencies {
    api(libs.micrometer.prometheus)
    compileOnly(libs.jnats)
    compileOnly(libs.annotations)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
