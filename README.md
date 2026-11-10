# TibcoBWUtils
Set of tools for manipulation with Tibco BusinessWorks 5 source codes

[![Build status](https://travis-ci.com/jiri-meluzin/TibcoBWUtils.svg?branch=master)](https://travis-ci.com/jiri-meluzin/TibcoBWUtils)

A multi-module Maven project (Java, groupId `io.github.jiri-meluzin`, version 1.3) that helps build, compare and
validate TIBCO BusinessWorks 5 projects, EAR files and deployment configuration, typically from a CI server (e.g. Jenkins).
All command line tools use [argparse4j](https://argparse4j.github.io/) with single-dash options (e.g. `-old`, `-new`).

## Modules

| Module | Purpose |
|---|---|
| `Tibco.DeploymentRepository` | Object model of a BW5 project repository (files, folders, libraries, global variables, deployments) |
| `Tibco.EARComparer` | Tools for comparing EARs and full configs, normalizing/resolving configuration, changing EAR versions |
| `Tibco.Build` | Parallel build/validation of deployments and libraries, change tracking from VCS logs |

Build everything with `mvn clean install`.

## Tibco.DeploymentRepository

Library (no CLI) for reading and modifying a BW5 project on disk.

- `Repository` / `RepositoryImpl` – entry point: `getRoot()`, `findItem(path)`, `findAll(predicate)` (breadth-first search),
  `createItem(...)`, `removeItem(item)`, `getAlternatives(item)`, `save()` / `isChanged()`. Predefined searches for
  shared HTTP connections (`SEARCH_SHARED_HTTP`, `SEARCH_HTTP_CONNECTION`, `SEARCH_HTTPS_CONNECTION`).
- `Item` – a file or folder in the repository; content can be loaded as XML (`loadAsXml()`) and updated (`updateContent`, `setContent`).
- Item sources (`FileSystemSource`, `ItemSourceType`) distinguish files on disk from items coming from libraries (`Library`, `LibraryImpl`).
- Changes are kept in memory until `save()` is called (see [In-memory changes](#in-memory-changes)).
- `GlobalVariables` / `GlobalVariable` / `Alias` / `Config` / `Deployment` – model of global variables, aliases, and deployment configuration.
- Password handling: `PasswordDecrypter` plus key providers (`ConstantKeyProvider`, `EnvironmentKeyProvider`, `DynamicKeyProvider`).
- `FullConfigAnalyzer` – analyzes full configs against the repository.

### In-memory changes

`InMemoryChangesImpl` (a `Deployment` with source type `Memory`) is a copy-on-write overlay over the on-disk `DeploymentImpl`;
its items are `InMemoryChangesItem`s wrapping the original items.

- **Reads** pass through to the original item until it is modified.
- **First modification** (`modify()`, triggered by `setContent()`, `createItem`, `removeItem`) copies the item's name, path and
  children (folder) or bytes (file) into memory and marks it changed. All ancestors are marked changed too.
- **Create** adds a new, already-changed in-memory item to the (modified) parent.
- **Remove** records the item in a `removedItems` set and detaches it from its parent's children.
- **Save** (`Repository.save()`): removed items are deleted from disk first (deepest path first), then changed items are
  created/written via `DeploymentImpl`. `Repository.isChanged()` tells whether anything is pending.

Nothing touches the file system until `save()`, so a set of edits can be prepared and applied together.

## Tibco.EARComparer

Main classes (run with `java -cp ... <class> <options>`):

| Class | Description |
|---|---|
| `CompareEARStarter` | Compares two EARs (`-old`, `-new`, `-verbose`, `-compareAdapterSDKProperties`). Exit code 0 = equal, 1 = different, 2 = error |
| `ConfigComparer` | Compares deployment configs (`-mode FULL-FULL\|UPDATE-UPDATE\|CHECK`, `-old`, `-new`, `-env`, `-tibcohome`) |
| `NormalizeConfig` | Normalizes a config file (`-config`, `-out`, `-tibcohome`) |
| `FullConfigChecker` | Checks source code vs. exported and stored full configs (`-prefs`, `-source`, `-export`, `-config`) |
| `FullConfigExpressionResolver` | Resolves expressions in a full config using a global config CSV (`-fullconfig`, `-globalconfig`, `-fullconfigout` or `-onlyvalidate`, `-strict`, CSV format options) |
| `FullInstallSetParallel` | Compares deployed vs. built EARs for a domain in parallel and copies changed EARs to `-output` |
| `EARVersionExtractor` / `EARVersionUpdater` | Reads or writes the version in `TIBCO.xml` of all EARs in a directory |
| `Decrypter` | Encrypts/decrypts passwords (`-action encrypt\|decrypt`, `-password`) |
| `CompareInstanceVariables`, `JDBCUrlConnectionUpdater` | Helpers for instance variables and JDBC URL templates in configs |

The `fullconfig.model` package models environments, services and archives of full configs; `expressionresolver` provides
template loading and JDBC URL parsing.

## Tibco.Build

| Class | Description |
|---|---|
| `BuildBranchParallel` | Builds and validates a branch's deployments (EARs) and libraries in parallel (`-branch`, `-source`, `-ears`, `-libraries`, `-old-libraries`, `-change-log-source`, `-change-log-config`, `-parallelism`, build/validation log options) |
| `BuildLibraryParallel` | Builds libraries listed in a file (`-librariespath`, `-parallelism`) |
| `RunParallel` | Runs commands read from stdin in parallel (`-utilization`, `-threads`) and reports duration, output and return code of each |
| `ChangeTracker` | Computes changed sources/configs of a branch from an SVN log (`-svn-log`, `-branch`, `-artifacts`, `-source`, `-init-build`) |
| `BinExecutor` | Executes TIBCO binaries (e.g. buildear) used by build tasks |

Supporting packages: `deploymentbuild` (task model: `TaskBuildDeployment`, `TaskBuildLibrary`, `TaskValidateDeployment`, build log
recorder/analyzer) and `versioning` (ANTLR-based `GitLogParser` producing `ChangeInfo`/`FileChangeInfo`/`ChangeStatus`).

## Tests

Only `Tibco.Build` has unit tests (`GitLogParserTest`, JUnit 4). Run `mvn test`.

## License

See [LICENSE](LICENSE).
