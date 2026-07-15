
import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage

plugins {
    `java-library`
    alias(libs.plugins.edc.build)
    // put the plugins on the build classpath for the subprojects, which apply them
    // declaratively in their own `plugins {}` blocks
    alias(libs.plugins.docker) apply false
    alias(libs.plugins.shadow) apply false
}

// the `libs` accessor is not in scope inside the `subprojects {}` lambda, so resolve the id here
val dockerPluginId = libs.plugins.docker.get().pluginId

val downloadOtelAgent by tasks.register("downloadOtelAgent", Copy::class) {
    val openTelemetry = configurations.create("open-telemetry")

    dependencies {
        openTelemetry(libs.opentelemetry.javaagent)
    }

    from(openTelemetry)
    into("build/otel")
    rename { "opentelemetry-javaagent.jar" }
}

subprojects {
    configurations.all {
        // jnats drags in the BouncyCastle LTS provider (bcprov-lts8on), which (a) ships
        // glibc-linked JNI natives that crash on the musl-based alpine images
        // (UnsatisfiedLinkError in org.bouncycastle.crypto.NativeLoader) and (b) duplicates
        // the org.bouncycastle.* classes already provided by bcprov-jdk18on in the shadow
        // jar. jnats' ed25519/NKey code works against plain bcprov, which stays on the
        // classpath via the EDC dependencies.
        exclude(group = "org.bouncycastle", module = "bcprov-lts8on")
    }

    // configure the docker image build for every subproject that applies the docker plugin
    // (in its `plugins {}` block) and ships a Dockerfile
    plugins.withId(dockerPluginId) {
        if (!file("${project.projectDir}/src/main/docker/Dockerfile").exists()) {
            return@withId
        }

        val copyOtelAgent = tasks.register<Copy>("copyOtelAgent") {
            dependsOn(rootProject.tasks.named("downloadOtelAgent"))
            from(rootProject.layout.buildDirectory.dir("otel"))
            into(project.layout.buildDirectory.dir("otel"))
        }
        tasks.named("shadowJar") {
            dependsOn(copyOtelAgent)
        }

        tasks.register<DockerBuildImage>("dockerize") {
            dependsOn(tasks.named("shadowJar"))
            val dockerContextDir = project.projectDir
            dockerFile.set(file("$dockerContextDir/src/main/docker/Dockerfile"))
            images.add("ghcr.io/eclipse-cfm/platform-images/${project.name}:${project.version}")
            images.add("ghcr.io/eclipse-cfm/platform-images/${project.name}:latest")

            // specify platform with the -Dplatform flag:
            if (System.getProperty("platform") != null) {
                platform.set(System.getProperty("platform"))
            }
            buildArgs.put("JAR", "build/libs/${project.name}.jar")
            buildArgs.put("OTEL_AGENT", "build/otel/opentelemetry-javaagent.jar")
            inputDir.set(file(dockerContextDir))
        }
    }
}
