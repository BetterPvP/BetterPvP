plugins {
    `java-library`
}

version = "1.0.0"
group = "me.mykindos.betterpvp.proxy"
description = "Velocity proxy for BetterPvP: admission, routing and the network player list"

dependencies {
    compileOnly(libs.velocity.api)
    annotationProcessor(libs.velocity.api)
    implementation(project(":orchestration"))
    implementation(libs.jackson.core)
    implementation(libs.jackson.annotations)
    implementation(libs.jackson.databind)
    implementation("org.tomlj:tomlj:1.1.1")
    implementation(libs.postgres)
    implementation(project(":network-protocol"))
    implementation(project(":sessions"))
    // BouncyCastle is only for NKey and JWT sign-in, which the network does not use
    implementation(libs.jnats) {
        exclude(group = "org.bouncycastle")
    }

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.testcontainers)
    testImplementation(project(":sessions-service"))
    testImplementation(libs.velocity.api)
    testImplementation("org.slf4j:slf4j-api:2.0.16")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
