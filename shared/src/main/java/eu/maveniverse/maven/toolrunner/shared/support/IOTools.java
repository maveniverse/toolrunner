/*
 * Copyright (c) 2023-2026 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.maven.toolrunner.shared.support;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * IO helpers.
 */
public final class IOTools {
    private IOTools() {}

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

    private static final int DEFAULT_BUFFER_SIZE = 16384;

    public static long transferTo(InputStream in, OutputStream out) throws IOException {
        Objects.requireNonNull(out, "out");
        long transferred = 0;
        byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
        int read;
        while ((read = in.read(buffer, 0, DEFAULT_BUFFER_SIZE)) >= 0) {
            out.write(buffer, 0, read);
            if (transferred < Long.MAX_VALUE) {
                try {
                    transferred = Math.addExact(transferred, read);
                } catch (ArithmeticException ignore) {
                    transferred = Long.MAX_VALUE;
                }
            }
        }
        return transferred;
    }

    public static InputStream nullInputStream() {
        return new InputStream() {
            private volatile boolean closed;

            private void ensureOpen() throws IOException {
                if (closed) {
                    throw new IOException("Stream closed");
                }
            }

            @Override
            public int available() throws IOException {
                ensureOpen();
                return 0;
            }

            @Override
            public int read() throws IOException {
                ensureOpen();
                return -1;
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                if (len == 0) {
                    return 0;
                }
                ensureOpen();
                return -1;
            }

            @Override
            public long skip(long n) throws IOException {
                ensureOpen();
                return 0L;
            }

            @Override
            public void close() throws IOException {
                closed = true;
            }
        };
    }

    public static OutputStream nullOutputStream() {
        return new OutputStream() {
            private volatile boolean closed;

            private void ensureOpen() throws IOException {
                if (closed) {
                    throw new IOException("Stream closed");
                }
            }

            @Override
            public void write(int b) throws IOException {
                ensureOpen();
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                ensureOpen();
            }

            @Override
            public void close() {
                closed = true;
            }
        };
    }
}
