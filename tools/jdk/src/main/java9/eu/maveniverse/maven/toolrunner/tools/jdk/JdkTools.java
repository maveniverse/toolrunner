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
import eu.maveniverse.maven.toolrunner.shared.spi.ToolProvisioner;
import eu.maveniverse.maven.toolrunner.shared.support.IOTools;
import eu.maveniverse.maven.toolrunner.shared.support.ProcessBuilderExecutor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.spi.ToolProvider;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The JDK tools for Java 9+.
 */
class JdkTools {
    /**
     * Returns {@code true} if command is supported. Moreover, if execution wants to set environment variables,
     * it will be refused and must be executed as forked process.
     */
    static boolean supportsTool(ToolContext context, Map<String, String> metadata, ToolExecution execution) {
        if (execution.environmentVariables().isPresent()) {
            return false;
        }
        return ToolProvider.findFirst(execution.command()).isPresent();
    }

    /**
     * Executes supported tool.
     */
    static ProcessBuilderExecutor.ProcessBuilderToolExecutorResult executeTool(
            ToolContext context, Map<String, String> metadata, ToolExecution execution)
            throws IOException, InterruptedException {
        ToolProvider toolProvider = ToolProvider.findFirst(execution.command()).orElseThrow(() -> new IllegalStateException("Bug!"));
        OutputStream stdOut;
        OutputStream stdErr;
        if (execution.grabOutputAsString()) {
            stdOut = new ByteArrayOutputStream();
            stdErr = new ByteArrayOutputStream();
        } else {
            stdOut = execution.stdOut().orElse(IOTools.nullOutputStream());
            stdErr = execution.stdErr().orElse(IOTools.nullOutputStream());
        }
        PrintStream out = stdOut instanceof PrintStream ? (PrintStream) stdOut : new PrintStream(stdOut);
        PrintStream err = stdErr instanceof PrintStream ? (PrintStream) stdErr : new PrintStream(stdErr);
        int exitCode = toolProvider.run(out, err, execution.arguments().toArray(new String[0]));
        String stdOutString = null;
        String stdErrString = null;
        if (execution.grabOutputAsString()) {
            stdOutString = stdOut.toString();
            stdErrString = stdErr.toString();
        }
        return new ProcessBuilderExecutor.ProcessBuilderToolExecutorResult(exitCode, stdOutString, stdErrString);
    }
}
