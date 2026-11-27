version = "1.0.0"
group = "me.mykindos.betterpvp.clans.protocol"
description = "What the clans plugin and the Clans service say to each other"

dependencies {
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    compileOnly(libs.annotations)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
