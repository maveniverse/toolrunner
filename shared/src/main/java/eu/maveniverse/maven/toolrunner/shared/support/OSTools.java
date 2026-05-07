/*
 * Copyright (c) 2023-2026 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.maven.toolrunner.shared.support;

import static java.util.Objects.requireNonNull;

import eu.maveniverse.maven.shared.core.fs.FileUtils;
import eu.maveniverse.maven.toolrunner.shared.spi.ToolProvider;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OS helpers.
 */
public final class OSTools {
    private OSTools() {}

    private static final Logger LOGGER = LoggerFactory.getLogger(OSTools.class);

    /**
     * Performs a simple path search using {@code which} or {@code where} commands.
     */
    public static Optional<Set<Path>> which(String executable) throws IOException {
        requireNonNull(executable);
        if (!executable.trim().isEmpty()) {
            ArrayList<String> command = new ArrayList<>();
            if (ToolProvider.IS_WINDOWS) {
                command.add("cmd.exe");
                command.add("/c");
                command.add("where " + executable);
            } else {
                command.add("sh");
                command.add("-c");
                command.add("which -a " + executable);
            }
            Process proc = new ProcessBuilder(command).start();
            try {
                int errCode = proc.waitFor();
                if (errCode == 0) {
                    HashSet<Path> paths = new HashSet<>();
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                        String line = reader.readLine();
                        while (line != null) {
                            paths.add(FileUtils.normalizePath(Paths.get(line)));
                            line = reader.readLine();
                        }
                    }
                    LOGGER.debug("which: '{}' found paths: {}", executable, paths);
                    return Optional.of(paths);
                }
            } catch (InterruptedException e) {
                throw new IOException(e);
            }
        }
        LOGGER.debug("which: '{}' found no paths", executable);
        return Optional.empty();
    }

    /**
     * Dereferences collection of paths, makes them real path and are returned only if resulting path exists.
     *
     * @deprecated Use {@link IOTools#dereference(Collection)} instead.
     */
    @Deprecated
    public static Set<Path> dereference(Collection<Path> paths) throws IOException {
        return IOTools.dereference(paths);
    }
}
