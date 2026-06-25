/*
 * Copyright (c) 2023-2026 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.maven.toolrunner.tools.jdk;

import eu.maveniverse.maven.shared.core.fs.FileUtils;
import eu.maveniverse.maven.toolrunner.shared.ToolExecution;
import eu.maveniverse.maven.toolrunner.shared.ToolHandle;
import eu.maveniverse.maven.toolrunner.shared.ToolHandler;
import eu.maveniverse.maven.toolrunner.shared.spi.ToolContext;
import eu.maveniverse.maven.toolrunner.shared.spi.ToolDetector;
import eu.maveniverse.maven.toolrunner.shared.spi.ToolExecutor;
import eu.maveniverse.maven.toolrunner.shared.spi.ToolProvider;
import eu.maveniverse.maven.toolrunner.shared.spi.ToolProvisioner;
import eu.maveniverse.maven.toolrunner.shared.support.ProcessBuilderExecutor;
import eu.maveniverse.maven.toolrunner.shared.support.Provisioners;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The JDK tool provider.
 */
public class JdkProvider implements ToolProvider, ToolDetector, ToolExecutor {
    public static final String NAME = "jdk";

    private static final String PREFIX = NAME + ".";
    public static final String HOME = PREFIX + "home";

    public static final String MODE = PREFIX + "mode";
    // if Tool SPI exists, use it, otherwise fork process
    public static final String MODE_AUTO = "auto";
    // always fork process
    public static final String MODE_FORKED = "forked";

    private static final String JAVA_HOME = "java.home";
    private static final String THIS_HOME = System.getProperty(JAVA_HOME);
    private static final String THIS_VERSION = System.getProperty("java.version");

    private static final String VERSION = "version";
    private static final String RUNTIME_NAME = "runtime.name";
    private static final String RUNTIME_VERSION = "runtime.version";
    private static final String VENDOR = "vendor";
    private static final String VENDOR_VERSION = "vendor.version";
    private static final String[] PROPERTIES = {VERSION, RUNTIME_NAME, RUNTIME_VERSION, VENDOR, VENDOR_VERSION};

    // ToolProvider

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public ToolDetector toolDetector() {
        return this;
    }

    @Override
    public Optional<ToolProvisioner> toolProvisioner() {
        return Optional.empty();
    }

    @Override
    public ToolExecutor toolExecutor() {
        return this;
    }

    // ToolDetector

    @Override
    public List<Map<String, String>> detectTool(ToolContext context) throws IOException {
        ArrayList<Map<String, String>> result = new ArrayList<>();

        // "this" is always present
        HashMap<String, String> thisJdk = new HashMap<>();
        thisJdk.put(ToolHandler.TOOL_NAME, NAME);
        thisJdk.put(ToolHandler.TOOL_VERSION, THIS_VERSION);
        thisJdk.put(JdkProvider.HOME, THIS_HOME);
        result.add(thisJdk);

        // discover
        for (Path path : doFindJdks()) {
            tryHome(context, path).ifPresent(result::add);
        }

        return Collections.unmodifiableList(result);
    }

    /**
     * Collects basic information about discovered Maven home.
     */
    protected Optional<Map<String, String>> tryHome(ToolContext context, Path home) throws IOException {
        Optional<Map<String, String>> maybeExisting = Provisioners.loadMetadata(context, home);
        if (maybeExisting.isPresent()) {
            return maybeExisting;
        }

        // execute and discover information
        Map<String, String> metadata = new HashMap<>();
        metadata.put(JdkProvider.HOME, home.toString());
        try {
            ToolHandle.Result result = doExecuteTool(
                    context,
                    metadata,
                    ToolExecution.ofCommand("java")
                            .addArguments("-XshowSettings:properties", "-version")
                            .build());
            if (result.success()) {
                List<String> lines =
                        Arrays.asList(result.stdErrString().orElse("").split("\\R"));
                HashMap<String, String> md = new HashMap<>();
                md.put(ToolHandler.TOOL_NAME, NAME);
                md.put(JdkProvider.HOME, home.toString());

                Stream.of(PROPERTIES).forEach(name -> {
                    lines.stream()
                            .filter(l -> l.contains("java." + name))
                            .map(l -> l.replaceFirst(".*=\\s*(.*)", "$1"))
                            .findFirst()
                            .ifPresent(value -> md.put(name, value));
                });
                if (!md.containsKey(VERSION)) {
                    return Optional.empty();
                } else {
                    md.put(ToolHandler.TOOL_VERSION, md.get(VERSION));
                }

                return Optional.of(md);
            }
            return Optional.empty();
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
    }

    // ToolExecutor

    @Override
    public List<String> commands(ToolContext context, Map<String, String> metadata) {
        String home = metadata.get(JdkProvider.HOME);
        try (Stream<Path> command = Files.list(Paths.get(home).resolve("bin"))) {
            return command.map(p -> p.getFileName().toString())
                    .filter(p -> !IS_WINDOWS || p.endsWith(".exe"))
                    .map(p -> IS_WINDOWS ? p.substring(0, p.length() - 4) : p)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Uses process builder just to quickly invoke maven to test it for version (and general functionality).
     * This is NOT how Maven is invoked with this tool, see {@link #executeTool(ToolContext, Map, ToolExecution)}.
     */
    private ProcessBuilderExecutor.ProcessBuilderToolExecutorResult doExecuteTool(
            ToolContext context, Map<String, String> metadata, ToolExecution execution)
            throws IOException, InterruptedException {
        String command = execution.command();
        String home = metadata.get(JdkProvider.HOME);
        if (home != null) {
            command = Paths.get(home).resolve("bin").resolve(exeName(command)).toString();
        }
        return ProcessBuilderExecutor.execute(
                execution.toBuilder().command(command).build(),
                context.config().maxRunDuration().toMillis());
    }

    @Override
    public ProcessBuilderExecutor.ProcessBuilderToolExecutorResult executeTool(
            ToolContext context, Map<String, String> metadata, ToolExecution execution)
            throws IOException, InterruptedException {
        String command = execution.command();

        String mode = execution.toolRunnerData().orElse(Collections.emptyMap()).getOrDefault(MODE, MODE_AUTO);

        if (!Objects.equals(mode, MODE_FORKED) && JdkTools.supportsTool(context, metadata, execution)) {
            return JdkTools.executeTool(context, metadata, execution);
        }

        String home = metadata.get(JdkProvider.HOME);
        if (home != null) {
            command = Paths.get(home).resolve("bin").resolve(exeName(command)).toString();
        }
        return ProcessBuilderExecutor.execute(
                execution.toBuilder().command(command).build(),
                context.config().maxRunDuration().toMillis());
    }

    private String exeName(String command) {
        return IS_WINDOWS ? command + ".exe" : command;
    }

    /**
     * Find JDKs in known classical locations.
     *
     * @return a set of path where JDKs were found.
     */
    private Set<Path> doFindJdks() {
        List<Path> dirsToTest = new ArrayList<>();

        // add current JDK
        dirsToTest.add(Paths.get(System.getProperty(JAVA_HOME)));

        // check environment variables for JAVA{xx}_HOME
        System.getenv().entrySet().stream()
                .filter(e -> e.getKey().startsWith("JAVA") && e.getKey().endsWith("_HOME"))
                .map(e -> Paths.get(e.getValue()))
                .forEach(dirsToTest::add);

        final Path userHome = FileUtils.discoverUserHomeDirectory();
        List<Path> installedDirs = new ArrayList<>();

        // JDK installed by third-party tool managers
        installedDirs.add(userHome.resolve(".jdks"));
        installedDirs.add(userHome.resolve(".m2").resolve("jdks"));
        installedDirs.add(userHome.resolve(".sdkman").resolve("candidates").resolve("java"));
        installedDirs.add(userHome.resolve(".gradle").resolve("jdks"));
        installedDirs.add(userHome.resolve(".jenv").resolve("versions"));
        installedDirs.add(userHome.resolve(".jbang").resolve("cache").resolve("jdks"));
        installedDirs.add(userHome.resolve(".asdf").resolve("installs").resolve("java"));
        installedDirs.add(userHome.resolve(".jabba").resolve("jdk"));
        installedDirs.add(userHome.resolve(".local")
                .resolve("share")
                .resolve("mise")
                .resolve("installs")
                .resolve("java"));

        // OS related directories
        String osname = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        boolean macos = osname.startsWith("mac");
        boolean win = osname.startsWith("win");
        if (macos) {
            installedDirs.add(Paths.get("/Library/Java/JavaVirtualMachines"));
            installedDirs.add(userHome.resolve("Library/Java/JavaVirtualMachines"));
            installedDirs.add(userHome.resolve("hostedtoolcache"));
        } else if (win) {
            installedDirs.add(Paths.get("C:\\Program Files\\Amazon Corretto\\"));
            installedDirs.add(Paths.get("C:\\Program Files\\BellSoft\\"));
            installedDirs.add(Paths.get("C:\\Program Files\\Eclipse Adoptium\\"));
            installedDirs.add(Paths.get("C:\\Program Files\\Java\\"));
            installedDirs.add(Paths.get("C:\\Program Files\\Zulu\\"));
            installedDirs.add(Paths.get("C:\\hostedtoolcache\\windows\\"));
            Path scoop = userHome.resolve("scoop").resolve("apps");
            if (Files.isDirectory(scoop)) {
                try (Stream<Path> stream = Files.list(scoop)) {
                    stream.forEach(installedDirs::add);
                } catch (IOException e) {
                    // ignore
                }
            }
        } else {
            installedDirs.add(Paths.get("/usr/jdk"));
            installedDirs.add(Paths.get("/usr/java"));
            installedDirs.add(Paths.get("/usr/local/java"));
            installedDirs.add(Paths.get("/opt/java"));
            installedDirs.add(Paths.get("/opt/hostedtoolcache"));
            installedDirs.add(Paths.get("/usr/lib/jvm"));
            installedDirs.add(Paths.get("/usr/lib64/jvm"));
        }

        for (Path dest : installedDirs) {
            if (Files.isDirectory(dest)) {
                try (Stream<Path> stream = Files.list(dest)) {
                    stream.forEach(dir -> {
                        dirsToTest.add(dir);
                        if (macos) {
                            dirsToTest.add(dir.resolve("Contents").resolve("Home"));
                        }
                    });
                } catch (IOException e) {
                    // ignore
                }
            }
        }

        // only keep directories that have a javac file
        return dirsToTest.stream()
                .filter(this::hasJavaC)
                .map(FileUtils::canonicalPath)
                .collect(Collectors.toSet());
    }

    private boolean hasJavaC(Path dir) {
        return Files.exists(dir.resolve(Paths.get("bin", exeName("javac"))));
    }
}
