/*
 * Copyright (c) 2023-2026 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.maven.toolrunner.tools.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.maveniverse.maven.toolrunner.shared.Config;
import eu.maveniverse.maven.toolrunner.shared.ToolExecution;
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

public class GradleProviderTest {
    @Test
    void isolated() throws IOException {
        // transient = true (clean up after yourself)
        // allowPathDetection = false (ignore user installed ones)
        try (ToolManager toolManager = ToolManager.create(Config.builder()
                .installationDirectory(Paths.get("target/installation-directory"))
                .tmpDirectory(Paths.get("target/temp-directory"))
                .isTransient(true)
                .allowOsPathEnvDetection(false)
                .build())) {
            assertTrue(toolManager.supportedToolNames().contains(GradleProvider.NAME));

            Optional<ToolHandler> maybeHandler = toolManager.selectToolByName("gradle");
            assertTrue(maybeHandler.isPresent());

            ToolHandler handler = maybeHandler.orElseThrow(() -> new NoSuchElementException("No value present"));

            // detect Gradle, should find zero
            List<Map<String, String>> detected = handler.detectTool();
            assertEquals(0, detected.size());

            // provision latest JBang
            ToolHandle handle = handler.toolHandle();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            handle.execute(ToolExecution.ofCommand("gradle")
                    .argument("-v")
                    .stdOut(baos)
                    .build());
            assertTrue(
                    baos.toString().trim().contains(handle.toolMetadata().get(ToolHandler.TOOL_VERSION)),
                    baos.toString().trim());

            // detect again, we should have one more provisioned
            detected = handler.detectTool();
            assertEquals(1, detected.size());
        }
    }
}
