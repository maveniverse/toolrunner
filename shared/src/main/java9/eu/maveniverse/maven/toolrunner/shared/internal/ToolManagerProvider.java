/*
 * Copyright (c) 2023-2026 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.maven.toolrunner.shared.internal;

import eu.maveniverse.maven.toolrunner.shared.Config;
import eu.maveniverse.maven.toolrunner.shared.ToolHandle;
import eu.maveniverse.maven.toolrunner.shared.ToolManager;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;
import java.util.spi.ToolProvider;

/**
 * JDK ToolProvider that delegates to ToolManager. This allows to run tools from JDK Tool API, and delegate to
 * ToolManager to select and execute supported tool. Because of this, the invocation is a bit different:
 * <ul>
 *     <li>ToolProvider name is "toolrunner"</li>
 *     <li>First argument is the tool name</li>
 *     <li>Second argument is the command of the tool</li>
 *     <li>Rest of arguments are command arguments</li>
 * </ul>
 * Hence, there must be at least two arguments (tool name and command) when executing ToolRunner via this
 * ToolProvider.
 */
public class ToolManagerProvider implements ToolProvider {
    private final ToolManager toolManager;

    public ToolManagerProvider() throws IOException {
        this.toolManager = ToolManager.create(Config.builder().build());
    }

    @Override
    public String name() {
        return "toolrunner";
    }

    @Override
    public int run(PrintWriter out, PrintWriter err, String... args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("There must be at least two arguments: <toolName> <command> [command args...]");
        }
        String toolName = args[0];
        String toolCommand = args[1];
        List<String> toolArgs = Arrays.asList(Arrays.copyOfRange(args, 2, args.length));

        ToolHandle handle = toolManager.selectToolByName(toolName)
                .orElseThrow(() -> new IllegalArgumentException(String.format("Tool %s not found", toolName)))
                .toolHandle();
        ToolHandle.Result result =
                handle.execute(handle.executionTemplate().command(toolCommand).arguments(toolArgs).build());
        if (!result.stdOutString().orElse("").trim().isEmpty()) {
            out.println(result.stdOutString().orElse(""));
        }
        if (!result.stdErrString().orElse("").trim().isEmpty()) {
            err.println(result.stdErrString().orElse(""));
        }
        return result.exitCode().orElse(result.success() ? 0 : 1);
    }
}
