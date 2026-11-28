plugins {
    application
}

version = "1.0.0"
group = "me.mykindos.betterpvp.orchestration.service"
description = "Standalone orchestration service for queueing, admission and placement"

dependencies {
    implementation(project(":metrics"))
    implementation(project(":orchestration"))
    implementation(libs.jackson.core)
    implementation(libs.jackson.annotations)
    implementation(libs.jackson.databind)
    implementation("org.tomlj:tomlj:1.1.1")
    implementation(project(":network-protocol"))
    // BouncyCastle is only for NKey and JWT sign-in, which the network does not use
    implementation(libs.jnats) {
        exclude(group = "org.bouncycastle")
    }
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.testcontainers)
    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass.set("me.mykindos.betterpvp.orchestration.service.OrchestrationServiceApplication")
}
