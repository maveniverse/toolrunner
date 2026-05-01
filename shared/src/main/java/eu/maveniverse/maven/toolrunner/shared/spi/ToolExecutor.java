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

import eu.maveniverse.maven.toolrunner.shared.ToolExecution;
import eu.maveniverse.maven.toolrunner.shared.ToolHandle;
import java.io.IOException;
import java.util.Map;

/**
 * Represents a tool executor.
 */
public interface ToolExecutor {
    /**
     * Creates a "template" {@link ToolExecution} specific for this tool. Caller should set all the needed things,
     * at least the arguments. This method never returns {@code null}.
     */
    ToolExecution.Builder executionTemplate(ToolContext context, Map<String, String> metadata);

    /**
     * Executes the tool with given execution. If tool was not detected, this call will install it as well, and then
     * execute. This method blocks, until tool execution finishes.
     */
    ToolHandle.Result executeTool(ToolContext context, Map<String, String> metadata, ToolExecution execution)
            throws IOException, InterruptedException;
}
