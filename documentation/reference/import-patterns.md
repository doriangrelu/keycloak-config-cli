# Import File Patterns

The `--import.files.locations` option supports multiple file locations using Spring's `PathMatchingResourcePatternResolver`.

## Ant-Style Path Patterns

Pattern matching rules:

| Pattern | Description |
|---------|-------------|
| `?` | Matches one character |
| `*` | Matches zero or more characters |
| `**` | Matches zero or more directories in a path |
| `{label:regex}` | Matches a regex pattern |

## Examples

| Example | Description |
|---------|-------------|
| `realm.json` | Single file from current directory |
| `path/*.json` | All `.json` files in `path/` directory |
| `path/realm_?.json` | Files matching `realm_X.json` (single char) |
| `path/**/a*.json` | Recursively find files starting with `a` |
| `path/{filename:[abc]+}.json` | Files matching regex pattern |
| `https://example.com/realm.json` | Load from URL |
| `https://user:password@example.com/realm.json` | URL with basic auth |
| `zip:file:path/file.zip!/*` | All files from zip archive |
| `zip:file:path/file.zip!/**/*.yaml` | Recursively from zip archive |

## Usage

### Command Line

```bash
# Single file
--import.files.locations=realm.yaml

# Multiple files
--import.files.locations=realm.yaml,clients.yaml,groups.yaml

# Directory
--import.files.locations=/config/

# Pattern
--import.files.locations=/config/**/*.yaml

# Remote
--import.files.locations=https://example.com/config/realm.yaml
```

### Environment Variable

```bash
export IMPORT_FILES_LOCATIONS="/config/*.yaml"
```

## File Processing Order

Files are processed in alphabetical order. Use numeric prefixes to control order:

```
config/
├── 00-realm.yaml       # Processed first
├── 01-roles.yaml
├── 02-groups.yaml
└── 03-clients.yaml     # Processed last
```
