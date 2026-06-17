/*
 * Copyright (c) 2023-2026 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.maven.toolrunner.plugin;

import eu.maveniverse.maven.shared.plugin.MojoSupport;
import eu.maveniverse.maven.toolrunner.shared.Config;
import eu.maveniverse.maven.toolrunner.shared.ToolExecution;
import eu.maveniverse.maven.toolrunner.shared.ToolHandle;
import eu.maveniverse.maven.toolrunner.shared.ToolHandler;
import eu.maveniverse.maven.toolrunner.shared.ToolManager;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.NoSuchElementException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Runs the selected tool.
 */
@Mojo(name = "run", threadSafe = true)
public class RunMojo extends MojoSupport {
    /**
     * Optional: whether tool manager along with installation directory should be considered "transient" or not.
     * In case of transient, the tools that are provisioned are deleted upon manager is being closed.
     */
    @Parameter(property = "toolrunner.isTransient", defaultValue = "true")
    private boolean isTransient;

    /**
     * Optional: whether tool detection should consider current use {@code $PATH} environment variable as well, to
     * detect tools.
     */
    @Parameter(property = "toolrunner.allowOsPathEnvDetection", defaultValue = "false")
    private boolean allowOsPathEnvDetection;

    /**
     * Optional: sets default installation directory, if default is not good fit.
     */
    @Parameter(property = "toolrunner.installationDirectory")
    private File installationDirectory;

    /**
     * Optional: sets default temporary directory, if default is not good fit.
     */
    @Parameter(property = "toolrunner.tmpDirectory")
    private File tmpDirectory;

    /**
     * Mandatory: The tool name execution should invoke. Note: the tool provider should be added to plugin dependency.
     */
    @Parameter(property = "toolrunner.toolName", required = true)
    private String toolName;

    /**
     * Optional: the tool version, if "latest" (as interpreted by tool provider) is not appropriate.
     */
    @Parameter(property = "toolrunner.toolVersion")
    private String toolVersion;

    /**
     * Optional: the command, if it differs from the command that tool provider pre-sets.
     */
    @Parameter(property = "toolrunner.command")
    private String command;

    /**
     * Mandatory: The command arguments, as array of strings.
     */
    @Parameter(property = "toolrunner.arguments", required = true)
    private String[] arguments;

    @Override
    public void executeMojo() throws MojoExecutionException, MojoFailureException {
        try (ToolManager toolManager = ToolManager.create(Config.builder()
                .isTransient(isTransient)
                .allowOsPathEnvDetection(allowOsPathEnvDetection)
                .installationDirectory(installationDirectory != null ? installationDirectory.toPath() : null)
                .tmpDirectory(tmpDirectory != null ? tmpDirectory.toPath() : null)
                .build())) {
            ToolHandler handler = toolManager
                    .selectToolByName(toolName)
                    .orElseThrow(() -> new MojoExecutionException("Tool " + toolName + " not supported."));
            ToolHandle handle;
            if (toolVersion != null) {
                HashMap<String, String> metadata = new HashMap<>();
                metadata.put(ToolHandler.TOOL_NAME, toolName);
                metadata.put(ToolHandler.TOOL_VERSION, toolVersion);
                handle = handler.selectTool(metadata)
                        .orElseThrow(() -> new MojoFailureException(
                                "Could not provision tool : " + toolName + " version " + toolVersion));
            } else {
                handle = handler.toolHandle();
            }
            ToolExecution.Builder execution = ToolExecution.ofCommand(
                            command != null
                                    ? command
                                    : handle.commands().iterator().next())
                    .addArguments(arguments)
                    .cwd(mavenSession.getCurrentProject().getBasedir().toPath());
            if (command != null) {
                execution.command(command);
            }
            ToolHandle.Result result = handle.execute(execution.build());
            if (result.success()) {
                logger.info(result.stdOutString()
                        .orElseThrow(() -> new NoSuchElementException("No value present"))
                        .trim());
            } else {
                String stdout = result.stdOutString()
                        .orElseThrow(() -> new NoSuchElementException("No value present"))
                        .trim();
                String stderr = result.stdErrString()
                        .orElseThrow(() -> new NoSuchElementException("No value present"))
                        .trim();
                if (!stderr.isEmpty()) {
                    throw new MojoFailureException("Failed to execute tool: " + stdout + " (err: " + stderr + ")");
                } else {
                    throw new MojoFailureException("Failed to execute tool: " + stdout);
                }
            }
        } catch (IOException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    @Override
    protected String skipPrefix() {
        return "toolrunner";
    }
}
