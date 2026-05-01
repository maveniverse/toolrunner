/*
 * Copyright (c) 2023-2026 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.maven.toolrunner.shared;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/**
 * The Configuration.
 */
public interface Config {
    /**
     * If {@code true}, the manager should be considered transient, and will clean up once closed.
     */
    boolean isTransient();

    /**
     * The directory where tools should be provisioned.
     */
    Optional<Path> installationDirectory();

    /**
     * The directory where temporary stuff should go.
     */
    Optional<Path> tempDirectory();

    /**
     * In case of download, the HTTP User Agent header to use. This value will override potentially
     * present {@code User-Agent} value present in {@link #httpHeaders()}.
     */
    Optional<String> userAgent();

    /**
     * Optional HTTP headers to use for HTTP requests.
     * Never returns {@code null}.
     */
    Optional<Map<String, String>> httpHeaders();

    /**
     * Creates new empty builder.
     */
    static Builder builder() {
        return new Builder();
    }

    class Builder {
        private boolean isTransient;
        private Path installationDirectory;
        private Path tempDirectory;
        private String userAgent;
        private Map<String, String> httpHeaders;

        private Builder() {
            this.isTransient = true;
            this.installationDirectory = null;
            this.tempDirectory = null;
            this.userAgent = null;
            this.httpHeaders = null;
        }

        public Builder isTransient(boolean isTransient) {
            this.isTransient = isTransient;
            return this;
        }

        public Builder installationDirectory(Path installationDirectory) {
            this.installationDirectory = installationDirectory;
            return this;
        }

        public Builder tempDirectory(Path tempDirectory) {
            this.tempDirectory = tempDirectory;
            return this;
        }

        public Builder userAgent(String userAgent) {
            this.userAgent = userAgent;
            return this;
        }

        public Builder httpHeaders(Map<String, String> httpHeaders) {
            this.httpHeaders = httpHeaders;
            return this;
        }

        public Config build() {
            return new Impl(isTransient, installationDirectory, tempDirectory, userAgent, httpHeaders);
        }

        private static class Impl implements Config {
            private final boolean isTransient;
            private final Path installationDirectory;
            private final Path tempDirectory;
            private final String userAgent;
            private final Map<String, String> httpHeaders;

            private Impl(
                    boolean isTransient,
                    Path installationDirectory,
                    Path tempDirectory,
                    String userAgent,
                    Map<String, String> httpHeaders) {
                this.isTransient = isTransient;
                this.installationDirectory = installationDirectory;
                this.tempDirectory = tempDirectory;
                this.userAgent = userAgent;
                this.httpHeaders = httpHeaders;
            }

            @Override
            public boolean isTransient() {
                return isTransient;
            }

            @Override
            public Optional<Path> installationDirectory() {
                return Optional.ofNullable(installationDirectory);
            }

            @Override
            public Optional<Path> tempDirectory() {
                return Optional.ofNullable(tempDirectory);
            }

            @Override
            public Optional<String> userAgent() {
                return Optional.ofNullable(userAgent);
            }

            @Override
            public Optional<Map<String, String>> httpHeaders() {
                return Optional.ofNullable(httpHeaders);
            }
        }
    }
}
