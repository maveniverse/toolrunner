/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package eu.maveniverse.maven.toolrunner.shared.spi;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Represents a tool specific provider.
 */
public interface ToolProvider {
    /**
     * Flag for detection of Windows OS.
     */
    boolean IS_WINDOWS =
            System.getProperty("os.name", "unknown").toLowerCase(Locale.ENGLISH).startsWith("windows");

    /**
     * This instance of metadata (check for instance equality) represents "do your best" in cases like selection
     * or provisioning.
     */
    Map<String, String> DEFAULT = Map.of();

    /**
     * The tool prefix and keys in metadata.
     */
    String TOOL_PREFIX = "tool.";

    /**
     * Metadata key for tool name.
     */
    String TOOL_NAME = TOOL_PREFIX + "name";

    /**
     * Metadata key for tool version.
     */
    String TOOL_VERSION = TOOL_PREFIX + "version";

    /**
     * Returns the tool name this provider supports. It must return non-empty string, never {@code null}.
     */
    String name();

    ToolDetector toolDetector();

    Optional<ToolProvisioner> toolProvisioner();

    ToolExecutor toolExecutor();
}
