# Tool Runner

Tool Runner is a utility and a Maven plugin, that allows one to run (and optionally provision beforehand) supported CLI tools 
as part of the build.

## Requirements

Runtime requirement:
* Java 17+ (CLI and Maven Plugin)
* Maven 3.9+ (Maven Plugin)

Build requirements:
* Java 21
* Maven 3.9+

## Project structure

### The `shared` subproject

Is a **reusable library** module, that depends on MIMA `Context` and Resolver APIs, and implements all the core logic
and provides SPI. The idea is that using `ToolManager` you discover all the available tools, and you can provision
and invoke them. By default, this subproject provides only the "scaffolding" and no tool. Tools are discovered by
`ToolProvider` SPI and using Java plain ServiceLoader infra.

### The `tools` subproject

The `tools` subproject are where the tool providers are implemented.

### The `toolrunner` Maven Plugin

The `toolrunner` subproject is a nearly trivial Maven plugin, that depends on `shared`. The idea of the plugin is
that when used, user declared not only the plugins, but also plugin dependencies with all the tools user requires,
and they will be dynamically discovered.

### The `it` subproject

Is where the ITs are .
