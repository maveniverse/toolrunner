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
package eu.maveniverse.maven.toolrunner.extension.packaging;

import eu.maveniverse.maven.shared.core.maven.MavenUtils;
import eu.maveniverse.maven.toolrunner.extension.lifecycle.ToolRunnerLifecycleProvider;
import javax.inject.Named;
import javax.inject.Singleton;

/**
 * {@code tool-runner} packaging plugins bindings provider for {@code tool} lifecycle.
 * As its name says, it is completely blank, left for user to decorate it.
 */
@Named("toolrunner")
@Singleton
public final class ToolRunnerLifecycleMappingProvider extends AbstractLifecycleMappingProvider {
    // START SNIPPET: blank
    private static final String[] BINDINGS = {
        "tool-run",
        "eu.maveniverse.maven.plugins:toolrunner:"
                + MavenUtils.discoverArtifactVersionWithPostOperator(
                        ToolRunnerLifecycleMappingProvider.class.getClassLoader(),
                        "eu.maveniverse.maven.toolrunner",
                        "extension",
                        s -> {
                            if (s == null) {
                                return "";
                            } else {
                                return ":" + s;
                            }
                        })
                + ":run"
    };
    // END SNIPPET: blank

    public ToolRunnerLifecycleMappingProvider() {
        super(ToolRunnerLifecycleProvider.NAME, BINDINGS);
    }
}
