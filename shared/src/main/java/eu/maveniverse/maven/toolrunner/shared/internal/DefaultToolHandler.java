/*
 * Copyright (c) 2023-2026 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.maven.toolrunner.shared.internal;

import static java.util.Objects.requireNonNull;

import eu.maveniverse.maven.toolrunner.shared.ToolHandle;
import eu.maveniverse.maven.toolrunner.shared.ToolHandler;
import eu.maveniverse.maven.toolrunner.shared.spi.ToolContext;
import eu.maveniverse.maven.toolrunner.shared.spi.ToolProvider;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class DefaultToolHandler implements ToolHandler {
    private final ToolContext toolContext;
    private final ToolProvider toolProvider;

    public DefaultToolHandler(ToolContext toolContext, ToolProvider toolProvider) {
        this.toolContext = requireNonNull(toolContext);
        this.toolProvider = requireNonNull(toolProvider);
    }

    @Override
    public List<Map<String, String>> detectTool() {
        try {
            return toolProvider.toolDetector().detectTool(toolContext);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public Optional<ToolHandle> selectTool(Map<String, String> metadata) {
        try {
            Map<String, String> selected = null;
            List<Map<String, String>> detected = detectTool();
            for (Map<String, String> tool : detected) {
                if (tool.equals(metadata)) {
                    selected = tool;
                    break;
                }
            }
            if (selected == null && toolProvider.toolProvisioner().isPresent()) {
                Optional<Map<String, String>> provisioned =
                        toolProvider.toolProvisioner().orElseThrow().provisionTool(toolContext, metadata);
                if (provisioned.isPresent()) {
                    selected = provisioned.orElseThrow();
                }
            }
            if (selected == null) {
                if (detected.isEmpty()) {
                    return Optional.empty();
                } else {
                    selected = detected.get(0);
                }
            }
            return Optional.of(new DefaultToolHandle(toolContext, selected, toolProvider.toolExecutor()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public ToolHandle toolHandle() {
        return selectTool(Map.of())
                .orElseThrow(() -> new IllegalStateException("No Tool detected nor could be provisioned"));
    }
}
