/*
 * Copyright (c) 2023-2026 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.maven.toolrunner.shared.support;

import static java.util.Objects.requireNonNull;

import eu.maveniverse.maven.toolrunner.shared.spi.ToolProvider;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * OS helpers.
 */
public final class OSTools {
    private OSTools() {}

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
                        paths.add(Path.of(reader.readLine()));
                    }
                    return Optional.of(paths);
                }
            } catch (InterruptedException e) {
                throw new IOException(e);
            }
        }
        return Optional.empty();
    }

    /**
     * Dereferences collection of paths, makes them real path and are returned only if resulting path exists.
     */
    public static Set<Path> dereference(Collection<Path> paths) throws IOException {
        HashSet<Path> dereferenced = new HashSet<>();
        for (Path path : paths) {
            if (Files.exists(path)) {
                path = path.toRealPath();
                if (Files.exists(path)) {
                    dereferenced.add(path);
                }
            }
        }
        return dereferenced;
    }
}
