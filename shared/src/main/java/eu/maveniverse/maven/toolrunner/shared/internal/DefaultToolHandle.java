/*
 * Copyright (c) 2023-2026 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.maven.toolrunner.shared.internal;

import static java.util.Objects.requireNonNull;

import eu.maveniverse.maven.toolrunner.shared.ToolExecution;
import eu.maveniverse.maven.toolrunner.shared.ToolHandle;
import eu.maveniverse.maven.toolrunner.shared.spi.ToolExecutor;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

public class DefaultToolHandle implements ToolHandle {
    private final ToolExecutor toolExecutor;
    private final Map<String, String> metadata;

    public DefaultToolHandle(ToolExecutor toolExecutor, Map<String, String> metadata) {
        this.toolExecutor = requireNonNull(toolExecutor);
        this.metadata = Map.copyOf(metadata);
    }

    @Override
    public Map<String, String> toolMetadata() {
        return metadata;
    }

    @Override
    public ToolExecution.Builder executionTemplate() {
        return toolExecutor.executionTemplate(metadata);
    }

    @Override
    public Result execute(ToolExecution execution) {
        try {
            return toolExecutor.executeTool(metadata, execution);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }
}
