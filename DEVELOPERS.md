# Overview

| Element                                                                | Usage                |
|------------------------------------------------------------------------|----------------------|
| [![](https://img.shields.io/badge/Gradle-blue?logo=gradle)](#gradle)   | Build tool           |
| [![](https://img.shields.io/badge/Kotlin-orange?logo=kotlin)](#kotlin) | Development language |
| [![](https://img.shields.io/badge/Docker-blue?logo=docker)](#docker)   | Test environment     |

## Getting started

### Java

![](https://img.shields.io/badge/require-black)

We recommend [SDKMAN](https://sdkman.io/) to manage your Java versions.

Use the [correct version of Java](.sdkmanrc) for this project:

```shell
sdk env
```

### Gradle

![](https://img.shields.io/badge/require-black)

> [!TIP]
> We provide a [Gradle wrapper](gradlew) to build the project.
> You can use it to avoid installing Gradle on your machine.

To check if the Gradle wrapper is available:

```shell
./gradlew -v
```

All the dependencies are defined in [settings.gradle.kts](settings.gradle.kts) file and used
in [build.gradle.kts](build.gradle.kts) file.

### Kotlin

![](https://img.shields.io/badge/require-black)

We use [Kotlin](https://kotlinlang.org/) to create the multiplatform CLI.

The version of Kotlin is defined in [gradle/libs.versions.toml](gradle/libs.versions.toml) file.

### Docker

![](https://img.shields.io/badge/optional-black)
[![](https://img.shields.io/badge/docker-install-blue?logo=docker)](https://www.docker.com/)
[![](https://img.shields.io/badge/docker--compose-install-blue?logo=docker)](https://docs.docker.com/compose/)

Docker is only used during the execution of [tests](src/commonTest).

A [docker-compose.yml](docker-compose.yml) file is provided to start a Vault server.

For the moment, the use of [TestContainers](https://www.testcontainers.org/) (or another solution) is not possible
because of the lack of support for Kotlin Multiplatform.
If one day it is possible, we will use it to avoid the use of a `docker-compose` file.

## Commands

### Build

To build the project:

```shell
./gradlew assemble
```

The final executables will be located at:

| Platform              | Executable path                                                                                                                                    |
|-----------------------|----------------------------------------------------------------------------------------------------------------------------------------------------|
| JVM                   | [app/build/libs/vault-tools-<version>.jar](app/build/libs)                                                                                         |
| JS                    | [app/build/compileSync/js/main/productionExecutable/kotlin/vault-tools-js-<version>.js](app/build/compileSync/js/main/productionExecutable/kotlin) |
| Native (Linux, Apple) | [app/build/bin/<platform>/releaseExecutable/vault-tools-<platform>-<version>.kexe](app/build/bin)                                                  |
| Native Windows        | [app/build/bin/mingwX64/releaseExecutable/vault-tools-windows-<version>.exe](app/build/bin/mingwX64/releaseExecutable)                             |

### Run

In order to run the project without (or minimum) building the executables, you can use the following commands:

- To run the JVM executable:

```shell
./gradlew runJvm --args='<command> [subcommand] [arguments] [options]'
```

- To run the JS executable:

```shell
./gradlew app:bundleProductionExecutableJs
node app/build/dist/js/productionExecutable/vault-tools-<version>.js <command> [subcommand] [arguments] [options]
```

- To run the Native executable:

```shell
./gradlew runDebugExecutable<platform>
./app/build/bin/<platform>/debugExecutable/vault-tools-<platform>-<version>.kexe <command> [subcommand] [arguments] [options]
```

### Test

The tests are located in the `src/commonTest` or `src/<platform>Test` directories of each module.

> [!NOTE]
> Some tests need [Playwright](https://playwright.dev/). They will download the required browsers automatically.
> If you have a problem with the download, you can install the browsers manually by running the following command:
> ```bash
> ./gradlew :common-test:install
> ```

- To start Vault:

```shell
docker compose up -d
```

- To run the tests for all platforms:

> [!IMPORTANT]
> We need to use `--max-workers=1` because the tests are using a single Vault server and the tests cannot be properly isolated.
> If the option is not used, the tests will fail because one test will erase the data created by another test.

```shell
./gradlew allTests --max-workers=1
```

- To run the tests for a specific platform:

```shell
./gradlew <platform>Test
# Example
./gradlew jvmTest
```

- To stop Vault:

```shell
docker compose down
```

### Linter

We use [Ktlint](https://github.com/JLLeitschuh/ktlint-gradle) to lint the code.

To format the code:

```shell
./gradlew ktlintFormat
```

To check the formatting:

```shell
./gradlew ktlintCheck
```

### Code analyzer

We use [Detekt](https://detekt.dev/) to analyze the code.

To analyze the code:

```shell
./gradlew detektAll
```
