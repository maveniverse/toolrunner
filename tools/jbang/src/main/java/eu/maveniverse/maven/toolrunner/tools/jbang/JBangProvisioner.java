/*
 * Copyright (c) 2023-2026 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.maven.toolrunner.tools.jbang;

import eu.maveniverse.maven.toolrunner.shared.spi.ToolProvisioner;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;

/**
 * The JBang tool provider.
 */
public class JBangProvisioner implements ToolProvisioner {
    @Override
    public Optional<Map<String, String>> provisionTool(Map<String, String> metadata) throws IOException {
        return Optional.empty();
    }
}
