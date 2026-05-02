# Tool Runner

Tool Runner is a utility and a Maven plugin, that allows one to run (and provision beforehand) supported CLI tools 
as part of the build.

## Requirements

Runtime requirement:
* Java 17+ (CLI and Maven Plugin)
* Maven 3.9+ (Maven Plugin)

Build requirements:
* Java 21
* Maven 3.9+

## Project structure

Structure of the project:
* Subproject "shared" is a **reusable library** module, that depends on MIMA `Context` and Resolver APIs, and implements all the core logic.
* Subproject "tools" are tool providers.
* Subproject "toolrunner" is a Maven plugin.
