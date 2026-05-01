/*
 * Copyright (c) 2023-2026 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.maven.toolrunner.tools.jbang;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class JBangProviderTest {
    @Test
    void smoke() {
        JBangProvider provider = new JBangProvider();

        assertNotNull(provider);
        assertEquals("jbang", provider.name());
        assertTrue(provider.toolProvisioner().isPresent());
    }

    @Test
    void detect() throws IOException {
        JBangProvider provider = new JBangProvider();
        List<Map<String, String>> detected = provider.toolDetector().detectTool();
        assertNotNull(detected);
    }
}
