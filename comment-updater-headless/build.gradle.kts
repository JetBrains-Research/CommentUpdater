group = rootProject.group
version = rootProject.version

dependencies {
    implementation(project(":comment-updater-core"))
    implementation("com.beust:klaxon:5.5")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.0")
    implementation("com.github.ajalt:clikt:2.8.0")
    implementation("com.google.code.gson:gson:2.6.2")
}

open class IOCliTask : org.jetbrains.intellij.tasks.RunIdeTask() {
    @get:Input
    val runner: String? by project
    @get:Input
    val dataset: String? by project
    @get:Input
    val output: String? by project
    @get:Input
    val config: String? by project
    @get:Input
    val statsOutput: String? by project

    init {
        jvmArgs = listOf(
            "-Djava.awt.headless=true",
            "--add-exports",
            "java.base/jdk.internal.vm=ALL-UNNAMED",
            "-Djdk.module.illegalAccess.silent=true"
        )
        maxHeapSize = "20g"
        standardInput = System.`in`
        standardOutput = System.`out`
    }
}

tasks {
    register<IOCliTask>("runCommentUpdater") {
        dependsOn("buildPlugin")
        args = listOfNotNull(
            runner,
            dataset?.let { it },
            output?.let { it },
            config?.let { it },
            statsOutput?.let { it }
        )
    }
}