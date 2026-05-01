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

import static java.util.Objects.requireNonNull;

import eu.maveniverse.maven.mima.context.Context;
import java.nio.file.Path;
import java.util.Map;

/**
 * Represents the tool context.
 */
public interface ToolContext {
    /**
     * Effective configuration.
     */
    record EffectiveConfig(
            Path installationDirectory, Path tempDirectory, String userAgent, Map<String, String> httpHeaders) {
        public EffectiveConfig(
                Path installationDirectory, Path tempDirectory, String userAgent, Map<String, String> httpHeaders) {
            this.installationDirectory = requireNonNull(installationDirectory);
            this.tempDirectory = requireNonNull(tempDirectory);
            this.userAgent = requireNonNull(userAgent);
            this.httpHeaders = requireNonNull(httpHeaders);
        }
    }

    EffectiveConfig effectiveConfig();

    Context context();
}
