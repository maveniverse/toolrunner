# Tool Runner

Toolbox is a utility Maven plugin that allows one to run (and provision beforehand) any CLI tool as part of the build.

## Requirements

Runtime requirement:
* Java 17+ (CLI and Maven Plugin)
* Maven 3.9+ (Maven Plugin)

Build requirements:
* Java 21
* Maven 3.9+

## Project structure

Structure of the project:
* Subproject "shared" is a **reusable library** module, that depends on MIMA `Context` and Resolver APIs, and implements all the logic.
* Subproject "toolbox" is a Maven Plugin and a CLI at the same time, that exposes Toolbox operations as Mojos and commands. Each Mojo comes in two
  "flavors": without prefix (i.e. "tree"), that requires project, and uses `MavenProject` to get the data for requests, and "gav-" 
  prefixed ones (i.e. "gav-tree"), that do not require project, and is able to target any existing Artifact out there.
* Subproject "mvnsh" is a Maven 4 mvnsh extension, providing Toolbox as mvnsh commands.
