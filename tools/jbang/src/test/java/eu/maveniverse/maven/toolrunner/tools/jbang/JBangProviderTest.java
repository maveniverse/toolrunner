/*
 * Copyright (c) 2023-2026 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.maven.toolrunner.tools.jbang;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.maveniverse.maven.toolrunner.shared.Config;
import eu.maveniverse.maven.toolrunner.shared.ToolHandle;
import eu.maveniverse.maven.toolrunner.shared.ToolHandler;
import eu.maveniverse.maven.toolrunner.shared.ToolManager;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

public class JBangProviderTest {
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
            assertTrue(toolManager.supportedToolNames().contains(JBangProvider.NAME));

            Optional<ToolHandler> maybeHandler = toolManager.selectToolByName("jbang");
            assertTrue(maybeHandler.isPresent());

            ToolHandler handler = maybeHandler.orElseThrow();

            // detect JBang, should find zero
            List<Map<String, String>> detected = handler.detectTool();
            assertEquals(0, detected.size());

            // provision latest JBang
            ToolHandle handle = handler.toolHandle();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            handle.execute(
                    handle.executionTemplate().argument("version").stdOut(baos).build());
            assertEquals(
                    handle.toolMetadata().get(ToolHandler.TOOL_VERSION),
                    baos.toString().trim());

            // detect again, we should have one more provisioned
            detected = handler.detectTool();
            assertEquals(1, detected.size());
        }
    }
}
