# Search

Search a string in keys or values of Vault KV v2 secrets.

## Command structure

### Arguments

The arguments are mandatory and are provided without name.

| Argument | Description               | Default | Mandatory |
|----------|---------------------------|---------|-----------|
| `<url>`  | Base URL of Vault server. |         | ✅        |

### Options

| Option                   | Environment variable | Type    | Description                                                                                | Default           | Mandatory |
|--------------------------|----------------------|---------|--------------------------------------------------------------------------------------------|-------------------|-----------|
| `-s`, `--search`         | VAULT_SEARCH         | string  | String to search among the keys and values                                                 |                   | ✅        |
| `-r`, `--root`           |                      | string  | Path to start the search from, subpaths will be searched recursively                       | '' (empty string) | ❌        |
| `-n`, `--namespaces`     |                      | string  | Comma-separated list of namespaces to search in                                            | '' (empty string) | ❌        |
| `-i`, `--ignore-case`    |                      | boolean | Ignore case when searching for the string                                                  | false             | ❌        |
| `-t`, `--token`          | VAULT_TOKEN          | string  | Vault token                                                                                |                   | ❌        |
| `-o`, `--oidc`           |                      | boolean | Enable to use OIDC authentication instead of token authentication                          | false             | ❌        |
| `--oidc-port`            |                      | number  | OIDC authentication callback server port                                                   | 8250              | ❌        |
| `--oidc-timeout`         |                      | number  | Timeout for OIDC authentication before automatic failure                                   | 120               | ❌        |
| `--oidc-parallel`        |                      | boolean | Enable to start parallel OIDC authentication, useful when you search in several namespaces | false             | ❌        |
| `--concurrency`          |                      | number  | Number of simultaneous requests to perform                                                 | (unlimited)       | ❌        |
| `--output-terminal-text` |                      | boolean | Enable output in terminal text format                                                      | true              | ❌        |
| `--output-terminal-json` |                      | boolean | Enable output in terminal json format                                                      | false             | ❌        |

## Usage

Command pattern:

| Platform | Command shape                                                |
|----------|--------------------------------------------------------------|
| JVM      | `java -jar vault-tools-<version>.jar search <url> [options]` |
| JS       | `node vault-tools-<version>.js search <url> [options]`       |
| Native   | `./vault-tools-<version>.kexe search <url> [options]`        |

### Examples

For the examples, we will use the JVM executable `vault-tools.jar` and the Vault server URL `https://vault.example.com`.

- Search for `my-secret` in the default namespace with token authentication:

```bash
java -jar vault-tools.jar search https://vault.example.com -s "my-secret" --token "my-token"
# or using the environment variable:
VAULT_TOKEN="my-token" VAULT_SEARCH="my-secret" java -jar vault-tools.jar search https://vault.example.com
```

- Search for `my-secret` in the default namespace with OIDC authentication and output in terminal JSON format only:

```bash
java -jar vault-tools.jar search https://vault.example.com -s "my-secret" --oidc true --output-terminal-json true --output-terminal-text false
```

- Search for `my-secret` in the `my-namespace` namespace with token authentication:

```bash
java -jar vault-tools.jar search https://vault.example.com -s "my-secret" -n "my-namespace" -t "my-token"
# or using the environment variable:
VAULT_TOKEN="my-token" VAULT_SEARCH="my-secret" java -jar vault-tools.jar search https://vault.example.com -n "my-namespace"
```

- Search for `my-secret` in the default, `my-namespace` and `another-namespace` namespaces with OIDC authentication:

> [!NOTE]
> The `namespaces` option is a comma-separated list of namespaces.
> If you want to search in the default namespace, you must include an empty string before the comma.

```bash
java -jar vault-tools.jar search https://vault.example.com --search my-secret --namespaces ",my-namespace,another-namespace" --oidc true
```

- Search for `my-secret` in the default, `my-namespace` namespaces starting from the `my-folder` path, with OIDC authentication, ignoring case and limiting concurrent requests:

```bash
java -jar vault-tools.jar search https://vault.example.com --search my-secret --namespaces ",my-namespace" -r "my-folder" -i true --concurrency 10 --oidc true
````

## Output

There is a list of examples of the output in available formats.

<table>
<tr>
<td>Code / Format</td>
<td>Terminal text</td>
<td>Terminal json</td>
</tr>
<tr>
<td>Search in root namespace without secrets found, and errors</td>
<td>

```text
Namespace: 
Total paths analyzed: 123
Secret paths: 0
Error paths: 0
```

</td>
<td>

```json
{
  "namespace": "",
  "totalPathsAnalyzed": 123,
  "secretPaths": {},
  "errorPaths": {}
}
```

</td>
</tr>
<td>Search in namespace "my-namespace" with secrets found and errors</td>
<td>

```text
Namespace: my-namespace
Total paths analyzed: 123
Secret paths: 2
- 'my-secret' (L. 1, 2, 3)
- 'sub/another-secret' (L. 4, 5)
Error paths: 1
- 'sub/secret-with-error' (permission denied)
```

</td>
<td>

```json
{
  "namespace": "my-namespace",
  "totalPathsAnalyzed": 123,
  "secretPaths": {
    "my-secret": [
      1,
      2,
      3
    ],
    "sub/another-secret": [
      4,
      5
    ]
  },
  "errorPaths": {
    "sub/secret-with-error": "permission denied"
  }
}
```

</td>
<tr>
</tr>
</table>
