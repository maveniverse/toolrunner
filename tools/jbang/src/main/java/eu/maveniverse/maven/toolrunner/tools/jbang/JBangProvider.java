/*
 * Copyright (c) 2023-2026 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.maven.toolrunner.tools.jbang;

import eu.maveniverse.maven.toolrunner.shared.spi.ToolDetector;
import eu.maveniverse.maven.toolrunner.shared.spi.ToolExecutor;
import eu.maveniverse.maven.toolrunner.shared.spi.ToolProvider;
import eu.maveniverse.maven.toolrunner.shared.spi.ToolProvisioner;
import java.util.Optional;

/**
 * The JBang tool provider.
 */
public class JBangProvider implements ToolProvider {
    public static final String NAME = "jbang";
    private static final String PREFIX = NAME + ".";

    public static final String HOME = PREFIX + "home";
    public static final String CACHE = PREFIX + "cache";
    public static final String CONFIG = PREFIX + "config";

    static final String EXE_NAME = IS_WINDOWS ? NAME + ".cmd" : NAME;

    private final JBangDetector jbangDetector;
    private final JBangProvisioner jbangProvisioner;
    private final JBangExecutor jbangExecutor;

    public JBangProvider() {
        this.jbangExecutor = new JBangExecutor();
        this.jbangDetector = new JBangDetector(this.jbangExecutor);
        this.jbangProvisioner = new JBangProvisioner();
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public ToolDetector toolDetector() {
        return jbangDetector;
    }

    @Override
    public Optional<ToolProvisioner> toolProvisioner() {
        return Optional.of(jbangProvisioner);
    }

    @Override
    public ToolExecutor toolExecutor() {
        return jbangExecutor;
    }
}
