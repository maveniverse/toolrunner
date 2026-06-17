/*
 * Copyright (c) 2023-2026 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.maven.toolrunner.tools.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.maveniverse.maven.toolrunner.shared.Config;
import eu.maveniverse.maven.toolrunner.shared.ToolExecution;
import eu.maveniverse.maven.toolrunner.shared.ToolHandle;
import eu.maveniverse.maven.toolrunner.shared.ToolHandler;
import eu.maveniverse.maven.toolrunner.shared.ToolManager;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

public class MavenProviderTest {
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
            assertTrue(toolManager.supportedToolNames().contains(MavenProvider.NAME));

            Optional<ToolHandler> maybeHandler = toolManager.selectToolByName("maven");
            assertTrue(maybeHandler.isPresent());

            ToolHandler handler = maybeHandler.orElseThrow(() -> new NoSuchElementException("No value present"));

            // detect Maven, should find zero
            List<Map<String, String>> detected = handler.detectTool();
            assertEquals(0, detected.size());

            // provision latest Maven
            ToolHandle handle = handler.toolHandle();
            assertEquals(handle.toolMetadata().get(ToolHandler.TOOL_VERSION), mavenVersion(handle, null));
            assertEquals(
                    handle.toolMetadata().get(ToolHandler.TOOL_VERSION),
                    mavenVersion(handle, MavenProvider.MODE_EMBEDDED));
            assertEquals(
                    handle.toolMetadata().get(ToolHandler.TOOL_VERSION),
                    mavenVersion(handle, MavenProvider.MODE_FORKED));

            // detect again, we should have one more provisioned
            detected = handler.detectTool();
            assertEquals(1, detected.size());
        }
    }

    private String mavenVersion(ToolHandle handle, String mode) {
        Instant now = Instant.now();
        try {
            ToolExecution.Builder builder = ToolExecution.ofCommand("mvn").addArguments("-v", "-q");
            if (mode != null) {
                builder.toolRunnerData(MavenProvider.MODE, mode);
            }
            return handle.execute(builder.build()).stdOutString().orElse("").trim();
        } finally {
            // Just a quick glance between two modes; embedded is two order of magnitudes faster than forked
            // but embedded does not cope with JVM/Env variables and extensions properly.
            // System.out.println("Duration mode=" + mode + " == " + Duration.between(now, Instant.now()));
        }
    }
}
