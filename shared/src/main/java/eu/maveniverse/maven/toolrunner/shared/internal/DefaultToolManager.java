/*
 * Copyright (c) 2023-2026 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.maven.toolrunner.shared.internal;

import eu.maveniverse.maven.toolrunner.shared.ToolHandler;
import eu.maveniverse.maven.toolrunner.shared.ToolManager;
import eu.maveniverse.maven.toolrunner.shared.spi.ToolProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class DefaultToolManager implements ToolManager {
    private final List<ToolProvider> toolProviders;

    public DefaultToolManager() {
        this.toolProviders = new ArrayList<>();
    }

    @Override
    public List<String> supportedToolNames() {
        return List.copyOf(toolProviders.stream().map(ToolProvider::name).toList());
    }

    @Override
    public Optional<ToolHandler> selectToolByName(String toolName) {
        return toolProviders.stream()
                .filter(tool -> tool.name().equals(toolName))
                .findFirst()
                .map(DefaultToolHandler::new);
    }
}
