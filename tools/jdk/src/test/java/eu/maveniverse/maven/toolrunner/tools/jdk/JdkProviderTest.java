/*
 * Copyright (c) 2023-2026 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.maven.toolrunner.tools.jdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.maveniverse.maven.toolrunner.shared.Config;
import eu.maveniverse.maven.toolrunner.shared.ToolHandle;
import eu.maveniverse.maven.toolrunner.shared.ToolHandler;
import eu.maveniverse.maven.toolrunner.shared.ToolManager;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

public class JdkProviderTest {
    @Test
    void isolated() throws IOException {
        // transient = true (clean up after yourself)
        // allowPathDetection = false (ignore user installed ones)
        try (ToolManager toolManager = ToolManager.create(Config.builder()
                .installationDirectory(Path.of("target/installation-directory"))
                .tempDirectory(Path.of("target/temp-directory"))
                .isTransient(true)
                .allowPathDetection(false)
                .build())) {
            assertTrue(toolManager.supportedToolNames().contains(JdkProvider.NAME));

            Optional<ToolHandler> maybeHandler = toolManager.selectToolByName("jdk");
            assertTrue(maybeHandler.isPresent());

            ToolHandler handler = maybeHandler.orElseThrow();

            // detect jdk, it is always supported, should find 1
            List<Map<String, String>> detected = handler.detectTool();
            assertEquals(1, detected.size());

            // provision latest jdk; same as detected
            Optional<ToolHandle> maybeHandle = handler.selectTool(detected.get(0));
            assertTrue(maybeHandle.isPresent());
            ToolHandle handle = maybeHandle.orElseThrow();
            assertEquals(detected.get(0), handle.toolMetadata());

            // detect again, we should have one more provisioned
            detected = handler.detectTool();
            assertEquals(1, detected.size());
        }
    }
}
