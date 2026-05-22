/*
 * Copyright (c) 2023-2026 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.maven.toolrunner.shared;

import eu.maveniverse.maven.shared.core.fs.FileUtils;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * The Configuration.
 */
public interface Config {
    /**
     * If {@code true}, the manager should be considered transient, and will clean up once closed (remove all
     * installations happened during lifetime of it).
     */
    boolean isTransient();

    /**
     * If {@code true}, the tool providers are allowed to discover tools from path too. Otherwise, only the
     * {@link #installationDirectory()} is allowed.
     */
    boolean allowPathDetection();

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
     * The timeout to run tool, or in other words, the maximum runtime of the tool. By default, 60 minutes.
     */
    Optional<Long> timeout();

    /**
     * Creates new empty builder.
     */
    static Builder builder() {
        return new Builder();
    }

    class Builder {
        private boolean isTransient;
        private boolean allowPathDetection;
        private Path installationDirectory;
        private Path tempDirectory;
        private String userAgent;
        private Map<String, String> httpHeaders;
        private Long timeout;

        private Builder() {
            this.isTransient = Boolean.parseBoolean(
                    System.getProperty("maveniverse.toolrunner.isTransient", Boolean.FALSE.toString()));
            this.allowPathDetection = Boolean.parseBoolean(
                    System.getProperty("maveniverse.toolrunner.allowPathDetection", Boolean.TRUE.toString()));
            this.installationDirectory = FileUtils.discoverCanonicalDirectoryFromSystemProperty(
                    "maveniverse.toolrunner.installationDirectory", ".toolrunner");
            this.tempDirectory = null;
            this.userAgent = null;
            this.httpHeaders = null;
            this.timeout = null;
        }

        public Builder isTransient(boolean isTransient) {
            this.isTransient = isTransient;
            return this;
        }

        public Builder allowPathDetection(boolean allowPathDetection) {
            this.allowPathDetection = allowPathDetection;
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

        public Builder timeout(long timeout, TimeUnit timeUnit) {
            this.timeout = timeUnit.toMillis(timeout);
            return this;
        }

        public Config build() {
            return new Impl(
                    isTransient,
                    allowPathDetection,
                    installationDirectory,
                    tempDirectory,
                    userAgent,
                    httpHeaders,
                    timeout);
        }

        private static class Impl implements Config {
            private final boolean isTransient;
            private final boolean allowPathDetection;
            private final Path installationDirectory;
            private final Path tempDirectory;
            private final String userAgent;
            private final Map<String, String> httpHeaders;
            private final Long timeout;

            private Impl(
                    boolean isTransient,
                    boolean allowPathDetection,
                    Path installationDirectory,
                    Path tempDirectory,
                    String userAgent,
                    Map<String, String> httpHeaders,
                    Long timeout) {
                this.isTransient = isTransient;
                this.allowPathDetection = allowPathDetection;
                this.installationDirectory = installationDirectory;
                this.tempDirectory = tempDirectory;
                this.userAgent = userAgent;
                this.httpHeaders = httpHeaders;
                this.timeout = timeout;
            }

            @Override
            public boolean isTransient() {
                return isTransient;
            }

            @Override
            public boolean allowPathDetection() {
                return allowPathDetection;
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

            @Override
            public Optional<Long> timeout() {
                return Optional.ofNullable(timeout);
            }
        }
    }
}
