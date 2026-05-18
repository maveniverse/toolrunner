# Tool Runner

Tool Runner is a utility and a Maven plugin, that allows to run (and optionally provision beforehand) supported CLI tools 
as part of the build.

## Requirements

Runtime requirement:
* Java 8+ (library and Maven Plugin)
* Maven 3.6+ (Maven Plugin)

Build requirements:
* Java 21
* Maven 3.9+

## Supported tools

- `ant` see https://ant.apache.org/ (1)
- `cosign` see https://github.com/sigstore/cosign (1)
- `gradle` see https://gradle.org/ (1)
- `isx` see https://github.com/Sanne/incus-spawn (2)
- `jbang` see https://github.com/jbangdev/jbang
- `jdk` the `$JAVA_HOME/bin/*` tools (3)
- `maven` see https://maven.apache.org/
- `minisign` see https://github.com/jedisct1/minisign (1)

Limitations:
* (1) Real version discovery is not yet done, so when LATEST asked, right now a currently known "latest" version is used.
* (2) The `isx` is really new tool, only LATEST is being provisioned.
* (3) This is a special case, as JDK tool provider always offers "self", the JDK that runs the tool. It currently never provision and supports, it always offers currently running JDK. In the future it may support provisioning and running other JDKs.

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
