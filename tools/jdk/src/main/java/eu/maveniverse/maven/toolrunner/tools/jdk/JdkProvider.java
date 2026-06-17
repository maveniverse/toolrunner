/*
 * Copyright (c) 2023-2026 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.maven.toolrunner.tools.jdk;

import eu.maveniverse.maven.toolrunner.shared.ToolExecution;
import eu.maveniverse.maven.toolrunner.shared.ToolHandler;
import eu.maveniverse.maven.toolrunner.shared.spi.ToolContext;
import eu.maveniverse.maven.toolrunner.shared.spi.ToolDetector;
import eu.maveniverse.maven.toolrunner.shared.spi.ToolExecutor;
import eu.maveniverse.maven.toolrunner.shared.spi.ToolProvider;
import eu.maveniverse.maven.toolrunner.shared.spi.ToolProvisioner;
import eu.maveniverse.maven.toolrunner.shared.support.ProcessBuilderExecutor;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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

    private final String THIS_HOME = System.getProperty("java.home");
    private final String THIS_VERSION = System.getProperty("java.version");

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
    public List<Map<String, String>> detectTool(ToolContext context) {
        HashMap<String, String> thisJdk = new HashMap<>();
        thisJdk.put(ToolHandler.TOOL_NAME, NAME);
        thisJdk.put(ToolHandler.TOOL_VERSION, THIS_VERSION);
        thisJdk.put(JdkProvider.HOME, THIS_HOME);
        return Collections.singletonList(thisJdk);
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
}
