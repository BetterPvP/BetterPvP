plugins {
    application
}

version = "1.0.0"
group = "me.mykindos.betterpvp.clans.service"
description = "Standalone service that is the only writer of clans, members, ranks, relations and camp permissions"

dependencies {
    implementation(project(":clans-protocol"))
    implementation(libs.jnats)
    implementation(libs.postgres)
    implementation(libs.hikari.cp)
    implementation(libs.jackson.databind)
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    compileOnly(libs.annotations)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.testcontainers)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.16")
}

application {
    mainClass.set("me.mykindos.betterpvp.clans.service.ClansServiceApplication")
}
