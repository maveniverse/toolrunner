/*
 * Copyright (c) 2023-2026 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.maven.toolrunner.shared;

import java.util.Map;
import java.util.Optional;

/**
 * The Tool Handle: this handle represents single installation of tool.
 */
public interface ToolHandle {
    /**
     * The tool metadata. Depending on tool, this may be some capability specifics and so on.
     * Two keys are always present:
     * <ul>
     *     <li>{@code tool.name} - the name of the tool represented by this handle</li>
     *     <li>{@code tool.version} - the version of the tool represented by this handle</li>
     * </ul>
     * This method never returns {@code null}.
     */
    Map<String, String> toolMetadata();

    /**
     * Creates a "template" {@link ToolExecution} specific for this tool. Caller should set all the needed things,
     * at least the arguments. This method never returns {@code null}.
     */
    ToolExecution.Builder executionTemplate();

    /**
     * Simplest result, as interpreted by tool provider. The actual result may be much more than just this,
     * depends on provider.
     */
    interface Result {
        /**
         * The outcome of execution.
         */
        boolean success();

        /**
         * The exit code, if available (ie running the tool happened in a way it did produce exit code).
         */
        default Optional<Integer> exitCode() {
            return Optional.empty();
        }

        /**
         * If {@link ToolExecution#grabOutputAsString()} was {@code true}, then the {@link String} containing
         * STDOUT of tool. Never {@code null}, but maybe empty string. Otherwise, empty.
         */
        default Optional<String> stdOutString() {
            return Optional.empty();
        }

        /**
         * If {@link ToolExecution#grabOutputAsString()} was {@code true}, then the {@link String} containing
         * STDERR of tool. Never {@code null}, but maybe empty string. Otherwise, empty.
         */
        default Optional<String> stdErrString() {
            return Optional.empty();
        }
    }

    /**
     * Executes the tool with given execution. If tool was not detected, this call will install it as well, and then
     * execute. This method blocks, until tool execution finishes.
     */
    Result execute(ToolExecution execution);
}
