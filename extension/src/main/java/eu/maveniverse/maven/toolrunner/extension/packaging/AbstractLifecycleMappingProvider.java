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

import static java.util.Objects.requireNonNull;

import java.util.Collections;
import java.util.HashMap;
import javax.inject.Provider;
import org.apache.maven.lifecycle.mapping.DefaultLifecycleMapping;
import org.apache.maven.lifecycle.mapping.Lifecycle;
import org.apache.maven.lifecycle.mapping.LifecycleMapping;
import org.apache.maven.lifecycle.mapping.LifecyclePhase;

/**
 * Base lifecycle mapping provider, ie per-packaging plugin bindings for given lifecycle.
 */
public abstract class AbstractLifecycleMappingProvider implements Provider<LifecycleMapping> {
    protected static final String DEFAULT = "default";
    private final LifecycleMapping lifecycleMapping;

    protected AbstractLifecycleMappingProvider(String lifecycleId, String[] pluginBindings) {
        requireNonNull(lifecycleId);
        requireNonNull(pluginBindings);
        final int len = pluginBindings.length;
        if (len % 2 != 0) {
            throw new IllegalArgumentException("Plugin bindings must have even count of elements");
        }
        HashMap<String, LifecyclePhase> lifecyclePhaseBindings = new HashMap<>(len / 2);
        for (int i = 0; i < len; i = i + 2) {
            lifecyclePhaseBindings.put(pluginBindings[i], new LifecyclePhase(pluginBindings[i + 1]));
        }
        Lifecycle lifecycle = new Lifecycle();
        lifecycle.setId(lifecycleId);
        lifecycle.setLifecyclePhases(Collections.unmodifiableMap(lifecyclePhaseBindings));
        this.lifecycleMapping = new DefaultLifecycleMapping(Collections.singletonList(lifecycle));
    }

    @Override
    public LifecycleMapping get() {
        return lifecycleMapping;
    }
}
