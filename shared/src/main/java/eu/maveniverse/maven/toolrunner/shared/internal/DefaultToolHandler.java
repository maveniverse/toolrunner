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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultToolHandler implements ToolHandler {
    private final Logger log = LoggerFactory.getLogger(this.getClass());
    private final ToolContext toolContext;
    private final ToolProvider toolProvider;

    public DefaultToolHandler(ToolContext toolContext, ToolProvider toolProvider) {
        this.toolContext = requireNonNull(toolContext);
        this.toolProvider = requireNonNull(toolProvider);
    }

    @Override
    public List<Map<String, String>> detectTool() {
        try {
            List<Map<String, String>> result = toolProvider.toolDetector().detectTool(toolContext);
            log.debug("detectTool result={}", result);
            return result;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public Optional<ToolHandle> selectTool(Map<String, String> metadata) {
        requireNonNull(metadata);
        log.debug("selectTool metadata={}", metadata);
        try {
            Map<String, String> selected = null;
            List<Map<String, String>> detected = detectTool();
            for (Map<String, String> tool : detected) {
                // user given conditions should be tested; not map equality
                if (tool.entrySet().containsAll(metadata.entrySet())) {
                    selected = tool;
                    break;
                }
            }
            if (selected == null && toolProvider.toolProvisioner().isPresent()) {
                Optional<Map<String, String>> provisioned = toolProvider
                        .toolProvisioner()
                        .orElseThrow(() -> new NoSuchElementException("No value present"))
                        .provisionTool(toolContext, metadata);
                if (provisioned.isPresent()) {
                    selected = provisioned.orElseThrow(() -> new NoSuchElementException("No value present"));
                    log.debug("Provisioner present, provisioned={}", selected);
                    selected = provisioned.orElseThrow(() -> new NoSuchElementException("No value present"));
                } else {
                    log.debug("Provisioner present, provisioning failed");
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
        return selectTool(Collections.emptyMap())
                .orElseThrow(() -> new IllegalStateException(
                        "No '" + toolProvider.name() + "' Tool detected nor could be provisioned"));
    }
}
