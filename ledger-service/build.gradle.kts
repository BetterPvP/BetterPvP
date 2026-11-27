plugins {
    application
}

version = "1.0.0"
group = "me.mykindos.betterpvp.ledger.service"
description = "Standalone ledger service, the only writer of coins"

dependencies {
    implementation(project(":ledger"))
    implementation(libs.jackson.databind)
    implementation(libs.postgres)
    implementation(libs.hikari.cp)
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgres)
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    testImplementation(libs.junit.jupiter)
    testImplementation("org.testcontainers:testcontainers:1.21.3")
    testImplementation("org.testcontainers:postgresql:1.21.3")
    testImplementation("org.testcontainers:junit-jupiter:1.21.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass.set("me.mykindos.betterpvp.ledger.service.LedgerServiceApplication")
}
