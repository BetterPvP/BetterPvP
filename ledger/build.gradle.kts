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
    api(project(":network-protocol"))
    // BouncyCastle is only for NKey and JWT sign-in, which the network does not use
    api(libs.jnats) {
        exclude(group = "org.bouncycastle")
    }
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
