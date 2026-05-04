/*
 * Copyright (c) 2023-2026 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.maven.toolrunner.plugin;

import eu.maveniverse.maven.toolrunner.shared.Config;
import eu.maveniverse.maven.toolrunner.shared.ToolExecution;
import eu.maveniverse.maven.toolrunner.shared.ToolHandle;
import eu.maveniverse.maven.toolrunner.shared.ToolHandler;
import eu.maveniverse.maven.toolrunner.shared.ToolManager;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Optional;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs the selected tool.
 */
@Mojo(name = "run", threadSafe = true)
public class RunMojo extends AbstractMojo {
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Parameter(property = "toolrunner.isTransient", defaultValue = "true")
    private boolean isTransient;

    @Parameter(property = "toolrunner.allowPathDetection", defaultValue = "false")
    private boolean allowPathDetection;

    @Parameter(property = "toolrunner.installationDirectory", defaultValue = "${project.build.directory}/toolrunner")
    private File installationDirectory;

    @Parameter(property = "toolrunner.tempDirectory")
    private File tempDirectory;

    @Parameter(property = "toolrunner.toolName", required = true)
    private String toolName;

    @Parameter(property = "toolrunner.toolVersion")
    private String toolVersion;

    @Parameter(property = "toolrunner.command")
    private String command;

    @Parameter(property = "toolrunner.arguments", required = true)
    private String[] arguments;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try (ToolManager toolManager = ToolManager.create(Config.builder()
                .isTransient(isTransient)
                .allowPathDetection(allowPathDetection)
                .installationDirectory(installationDirectory.toPath())
                .tempDirectory(tempDirectory != null ? tempDirectory.toPath() : null)
                .build())) {
            Optional<ToolHandler> maybeHandler = toolManager.selectToolByName(toolName);
            if (maybeHandler.isPresent()) {
                ToolHandler handler = maybeHandler.orElseThrow();
                HashMap<String, String> metadata = new HashMap<>();
                metadata.put(ToolHandler.TOOL_NAME, toolName);
                if (toolVersion != null) {
                    metadata.put(ToolHandler.TOOL_VERSION, toolVersion);
                }
                Optional<ToolHandle> maybeHandle = handler.selectTool(metadata);
                if (maybeHandle.isPresent()) {
                    ToolHandle handle = maybeHandle.orElseThrow();
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    ByteArrayOutputStream err = new ByteArrayOutputStream();
                    ToolExecution.Builder execution = handle.executionTemplate()
                            .addArguments(arguments)
                            .stdOut(out)
                            .stdErr(err);
                    if (command != null) {
                        execution.command(command);
                    }
                    ToolHandle.Result result = handle.execute(execution.build());
                    if (result.success()) {
                        log.info(out.toString().trim());
                    } else {
                        String stdout = out.toString().trim();
                        String stderr = err.size() > 0 ? err.toString().trim() : null;
                        if (stderr != null) {
                            throw new MojoFailureException(
                                    "Failed to execute tool: " + stdout + " (err: " + stderr + ")");
                        } else {
                            throw new MojoFailureException("Failed to execute tool: " + stdout);
                        }
                    }
                } else {
                    // provisioning support is optional; if tool not already present, and provisioner not present
                    if (toolVersion != null) {
                        throw new MojoFailureException(
                                "Could not provision tool : " + toolName + " version " + toolVersion);
                    } else {
                        throw new MojoFailureException("Could not provision tool : " + toolName);
                    }
                }
            } else {
                throw new MojoFailureException("Unsupported tool: " + toolName);
            }
        } catch (IOException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }
}
