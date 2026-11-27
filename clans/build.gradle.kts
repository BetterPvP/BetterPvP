project.ext["jooqPackageName"] = "me.mykindos.betterpvp.clans.database.jooq"
project.ext["jooqOutputDir"] = "src/main/java"

plugins {
    id("org.flywaydb.flyway")
    id("io.papermc.paperweight.userdev")
    id("jooqdynamic")
}

version = "1.0.0"
group = "me.mykindos.betterpvp.clans"
description = "Clans plugin for BetterPvP"

dependencies {
    compileOnly(libs.bundles.paper)
    paperweight.paperDevBundle(libs.versions.paper)
    implementation(libs.reflections)
    implementation(project(":clans-protocol"))

    compileOnly(project(":core"))
    compileOnly(project(":progression"))
    compileOnly(project(":champions"))
    compileOnly(project(":shops"))
    compileOnly(libs.mapper)
    compileOnly(libs.packetevents)
    compileOnly(libs.modelengine)

    annotationProcessor(libs.lombok)
    compileOnly(libs.lombok)

    testImplementation(libs.bundles.test)
    testImplementation(project(":core"))
    testImplementation(libs.mapper)
    testImplementation(libs.modelengine)
    testImplementation("org.mockito:mockito-core:5.23.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.23.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// jooqdynamic excludes org.jooq from this module's runtimeOnly/runtimeClasspath (it ships only via :core's shaded
// jar at real server runtime), and that exclusion also taints testRuntimeClasspath since it extends testRuntimeOnly
// -> runtimeOnly. Tests that touch classes referencing jOOQ types (e.g. IslandInstanceRepository) still need those
// classes to *load*, so they are resolved into a detached configuration and appended straight to the test task's
// classpath, bypassing the excluding hierarchy entirely.
val jooqTestRuntime: Configuration = configurations.detachedConfiguration(
    project.dependencies.create(
        extensions.getByType<VersionCatalogsExtension>().named("libs")
            .findLibrary("jooq").orElseThrow().get().toString()))

tasks.test {
    classpath += jooqTestRuntime
}

// Cross-service tests against real NATS, Postgres and network services, run by networkTest instead of test
val networkTest: SourceSet by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output + sourceSets.test.get().output
    runtimeClasspath += sourceSets.main.get().output + sourceSets.test.get().output
}
// Not the test configurations, which carry the server's own API at a version MockBukkit does not know
configurations.named(networkTest.implementationConfigurationName) {
    extendsFrom(configurations.implementation.get())
}
configurations.named(networkTest.runtimeOnlyConfigurationName) {
    extendsFrom(configurations.testRuntimeOnly.get())
}
configurations.named(networkTest.compileOnlyConfigurationName) {
    extendsFrom(configurations.compileOnly.get())
}
configurations.named(networkTest.annotationProcessorConfigurationName) {
    extendsFrom(configurations.annotationProcessor.get())
}

dependencies {
    "networkTestImplementation"(project(path = ":core", configuration = "networkTestFixtures"))
    "networkTestImplementation"(project(":core"))
    "networkTestImplementation"(project(":shops"))
    "networkTestImplementation"(libs.bundles.test)
    "networkTestImplementation"("org.mockito:mockito-core:5.23.0")
}

tasks.register<Test>("networkTest") {
    description = "Runs the cross-service tests. Needs Docker."
    group = "verification"
    testClassesDirs = networkTest.output.classesDirs
    classpath = networkTest.runtimeClasspath + jooqTestRuntime
    useJUnitPlatform()
    shouldRunAfter(tasks.test)
}
