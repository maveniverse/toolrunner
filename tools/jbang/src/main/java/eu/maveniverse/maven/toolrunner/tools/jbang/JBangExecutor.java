/*
 * Copyright (c) 2023-2026 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.maven.toolrunner.tools.jbang;

import eu.maveniverse.maven.toolrunner.shared.ToolExecution;
import eu.maveniverse.maven.toolrunner.shared.spi.ToolExecutor;
import eu.maveniverse.maven.toolrunner.shared.support.ProcessBuilderExecutor;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

/**
 * The JBang tool provider.
 */
public class JBangExecutor implements ToolExecutor {
    @Override
    public ToolExecution.Builder executionTemplate(Map<String, String> metadata) {
        ToolExecution.Builder builder;
        String jbangHome = metadata.get(JBangProvider.HOME);
        if (jbangHome != null) {
            builder = ToolExecution.ofCommand(
                    Path.of(jbangHome).resolve("bin/" + JBangProvider.EXE_NAME).toString());
            builder.environmentVariable("JBANG_HOME", jbangHome);
        } else {
            builder = ToolExecution.ofCommand(JBangProvider.EXE_NAME);
        }

        if (metadata.containsKey(JBangProvider.CACHE)) {
            builder.environmentVariable("JBANG_CACHE_DIR", metadata.get(JBangProvider.CACHE));
        }

        if (metadata.containsKey(JBangProvider.CONFIG)) {
            builder.environmentVariable("JBANG_CONFIG", metadata.get(JBangProvider.CONFIG));
        }

        return builder;
    }

    @Override
    public ProcessBuilderExecutor.ProcessBuilderToolExecutorResult executeTool(
            Map<String, String> metadata, ToolExecution execution) throws IOException, InterruptedException {
        return ProcessBuilderExecutor.execute(execution);
    }
}
