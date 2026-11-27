plugins {
    `java-library`
}

version = "1.0.0"
group = "me.mykindos.betterpvp.ledger"
description = "Shared ledger contracts: entries, receipts and their wire format"

dependencies {
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    api(libs.annotations)
    implementation(libs.jackson.databind)
    api("io.nats:jnats:2.21.1")
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
