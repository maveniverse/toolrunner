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
import javax.inject.Inject;
import org.apache.maven.execution.MavenSession;
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
    @Parameter(property = "toolrunner.allowPathDetection", defaultValue = "false")
    private boolean allowPathDetection;

    /**
     * Optional: sets default installation directory, if default is not good fit.
     */
    @Parameter(property = "toolrunner.installationDirectory")
    private File installationDirectory;

    /**
     * Optional: sets default temporary directory, if default is not good fit.
     */
    @Parameter(property = "toolrunner.tempDirectory")
    private File tempDirectory;

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

    @Inject
    private MavenSession session;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try (ToolManager toolManager = ToolManager.create(Config.builder()
                .isTransient(isTransient)
                .allowPathDetection(allowPathDetection)
                .installationDirectory(installationDirectory != null ? installationDirectory.toPath() : null)
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
                            .cwd(session.getCurrentProject().getBasedir().toPath())
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
