# platform-images

Runtime assemblies and Docker images for the Eclipse CFM platform. Each module bundles a set of
[Eclipse Dataspace Components (EDC)](https://github.com/eclipse-edc) BOMs into a runnable fat JAR and
packages it as a container image published to the GitHub Container Registry (GHCR).

## Modules

| Module          | Description                                       | Image                                               |
|-----------------|---------------------------------------------------|-----------------------------------------------------|
| `controlplane`  | EDC control plane (virtual base + SQL, DCP, NATS) | `ghcr.io/eclipse-cfm/platform-images/controlplane`  |
| `identity-hub`  | EDC IdentityHub (OAuth2 + SQL feature set)        | `ghcr.io/eclipse-cfm/platform-images/identity-hub`  |
| `issuerservice` | EDC IssuerService (OAuth2 + SQL feature set)      | `ghcr.io/eclipse-cfm/platform-images/issuerservice` |

Please note that the `issuerservice` is "unpinionated", that means it does not have any specific attestation sources. In
a production environment, one would likely want to build an opinionated image that includes specific use-case-related
attestation sources. Learn more about attestation sources in
the [IdentityHub documentation](https://github.com/eclipse-edc/IdentityHub/blob/e4714ef4f4447537c113973399ea842a1f606897/docs/developer/architecture/issuer/issuance/issuance.process.md).

All images are based on `eclipse-temurin` JRE, ship the
[OpenTelemetry Java agent](https://opentelemetry.io/docs/zero-code/java/agent/) for observability,
and expose a health check at `http://localhost:8080/api/check/health`.

## Building locally

The project uses the Gradle wrapper; a JDK 23 (or newer) toolchain is expected.

Build a module's fat JAR:

```bash
./gradlew :controlplane:shadowJar
```

The JAR is written to `<module>/build/libs/<module>.jar`.

### Building a Docker image locally

The container build uses the module's `Dockerfile` (under `<module>/src/main/docker/`) with two build
arguments — the fat JAR and the OpenTelemetry agent:

```bash
# 1. build the fat JAR and download the OTel agent
./gradlew :controlplane:dockerize
```

Optional Docker build args:

- `JVM_ARGS` — extra JVM flags (e.g. memory settings) passed to the runtime.

## Running

```bash
docker run --rm -p 8080:8080 ghcr.io/eclipse-cfm/platform-images/controlplane:latest
```

## Continuous integration

CI is defined in [`.github/workflows/docker-images.yaml`](.github/workflows/docker-images.yaml) and runs on
every push (all branches and tags). It builds each module's image in a matrix, and publishes to GHCR
depending on the trigger:

| Trigger                     | Images built | Published tags                        | GitHub release |
|-----------------------------|:------------:|---------------------------------------|:--------------:|
| Push to any branch          |      ✅       | — (build only, not pushed)            |       —        |
| Push to `main`              |      ✅       | `latest`, `<short-sha>`               |       —        |
| Tag created (e.g. `v1.2.3`) |      ✅       | `latest`, `<short-sha>`, `<tag-name>` |  ✅ (tag name)  |

### Cutting a release

Push a tag to publish tagged images and create the corresponding GitHub release:

```bash
git tag v1.2.3
git push origin v1.2.3
```
