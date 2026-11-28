plugins {
    application
}

version = "1.0.0"
group = "me.mykindos.betterpvp.sessions.service"
description = "Standalone sessions service, which decides which server owns each player"

dependencies {
    implementation(project(":metrics"))
    implementation(project(":sessions"))
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    compileOnly(libs.annotations)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.testcontainers)
    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass.set("me.mykindos.betterpvp.sessions.service.SessionsServiceApplication")
}
