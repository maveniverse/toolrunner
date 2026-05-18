/*
 * Copyright (c) 2023-2026 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.maven.toolrunner.tools.minisign;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.maveniverse.maven.toolrunner.shared.Config;
import eu.maveniverse.maven.toolrunner.shared.ToolHandle;
import eu.maveniverse.maven.toolrunner.shared.ToolHandler;
import eu.maveniverse.maven.toolrunner.shared.ToolManager;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

public class MinisignProviderTest {
    @Test
    void isolated() throws IOException {
        // transient = true (clean up after yourself)
        // allowPathDetection = false (ignore user installed ones)
        try (ToolManager toolManager = ToolManager.create(Config.builder()
                .installationDirectory(Paths.get("target/installation-directory"))
                .tempDirectory(Paths.get("target/temp-directory"))
                .isTransient(true)
                .allowPathDetection(false)
                .build())) {
            assertTrue(toolManager.supportedToolNames().contains(MinisignProvider.NAME));

            Optional<ToolHandler> maybeHandler = toolManager.selectToolByName("minisign");
            assertTrue(maybeHandler.isPresent());

            ToolHandler handler = maybeHandler.orElseThrow(() -> new NoSuchElementException("No value present"));

            // detect tool, should find zero
            List<Map<String, String>> detected = handler.detectTool();
            assertEquals(0, detected.size());

            // provision latest
            ToolHandle handle = handler.toolHandle();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            handle.execute(handle.executionTemplate().argument("-v").stdOut(out).build());
            assertTrue(out.toString().trim().contains(handle.toolMetadata().get(ToolHandler.TOOL_VERSION)));

            // detect again, we should have one more provisioned
            detected = handler.detectTool();
            assertEquals(1, detected.size());
        }
    }
}
