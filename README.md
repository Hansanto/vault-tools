# Vault tools

Multiplatform command-line interface (CLI) to interact with [Vault](https://www.hashicorp.com/en/products/vault)

## Platform

Here is a list of the supported platforms on which the CLI can be used.

- ✅ JVM
- ✅ JS (NodeJS)
- ❌ WasmJS
- ❌ WasmWasi

### Native

[Tier 1](https://kotlinlang.org/docs/native-target-support.html#tier-1)

- ✅ macosArm64
- ✅ iosSimulatorArm64
- ✅ iosArm64

[Tier 2](https://kotlinlang.org/docs/native-target-support.html#tier-2)

- ✅ linuxX64
- ✅ linuxArm64
- ❌ watchosSimulatorArm64
- ❌ watchosArm32
- ❌ watchosArm64
- ❌ tvosSimulatorArm64
- ❌ tvosArm64

[Tier 3](https://kotlinlang.org/docs/native-target-support.html#tier-3)

- ❌ androidNativeArm32
- ❌ androidNativeArm64
- ❌ androidNativeX86
- ❌ androidNativeX64
- ✅ mingwX64 (Windows)
- ❌ watchosDeviceArm64
- ❌ iosX64

## Prerequisite

- If you want to use Java, you need to have [Java 17](https://www.java.com) or higher installed.
- If you want to use NodeJS, you need to have [NodeJS](https://nodejs.org) installed.
- Otherwise, you can use the native executable for your platform.

## Usage

Command pattern:

| Platform              | Command shape                                                                      |
|-----------------------|------------------------------------------------------------------------------------|
| JVM                   | `java -jar vault-tools-<version>.jar <command> [subcommand] [arguments] [options]` |
| JS                    | `node vault-tools-<version>.js <command> [subcommand] [arguments] [options]`       |
| Native (Linux, Apple) | `./vault-tools-<version>.kexe <command> [subcommand] [arguments] [options]`        |
| Native (Windows)      | `./vault-tools-<version>.exe <command> [subcommand] [arguments] [options]`         |

### List of commands

> [!NOTE]
> Each command has its own documentation to explain its usage and options.

| Command          | Description                            |
|------------------|----------------------------------------|
| [Search](search) | Search for string in a Vault namespace |

## Contributing

- [Contribution guidelines](CONTRIBUTING.md)
- [Technical environment setup](DEVELOPERS.md)
