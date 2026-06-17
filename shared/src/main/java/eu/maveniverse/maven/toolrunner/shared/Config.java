/*
 * Copyright (c) 2023-2026 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.maven.toolrunner.shared;

import static java.util.Objects.requireNonNull;

import eu.maveniverse.maven.shared.core.fs.FileUtils;
import eu.maveniverse.maven.shared.core.maven.MavenUtils;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The Configuration.
 */
public interface Config {
    /**
     * If {@code true}, the manager should be considered transient, and will clean up once closed (remove all
     * installations happened during lifetime of it). Default value is {@code false}.
     */
    boolean isTransient();

    /**
     * If {@code true}, the tool providers are allowed to discover tools from OS path environment too. Otherwise, only the
     * {@link #installationDirectory()} is allowed. Default value is {@code true}.
     */
    boolean allowOsPathEnvDetection();

    /**
     * Returns the version of the toolrunner.
     */
    String toolRunnerVersion();

    /**
     * The directory where tools should be provisioned.
     * If {@link #isTransient()}, upon closing tool manager, this directory will be deleted.
     */
    Path installationDirectory();

    /**
     * The directory where temporary stuff should go.
     * If {@link #isTransient()}, upon closing tool manager, this directory will be deleted.
     */
    Path tmpDirectory();

    /**
     * In case of download, the HTTP User Agent header to use. This value will override potentially
     * present {@code User-Agent} value present in {@link #httpHeaders()}.
     */
    String userAgent();

    /**
     * Optional HTTP headers to use for HTTP requests.
     */
    Map<String, String> httpHeaders();

    /**
     * The maximum allowed duration to run tool, or in other words, the maximum runtime of the tool. By default, 60 minutes.
     */
    Duration maxRunDuration();

    /**
     * Creates new empty builder.
     */
    static Builder builder() {
        return new Builder();
    }

    class Builder {
        private boolean isTransient;
        private boolean allowOsPathEnvDetection;
        private Path installationDirectory;
        private Path tmpDirectory;
        private String userAgent;
        private Map<String, String> httpHeaders;
        private Duration maxRunDuration;

        private boolean installationDirectoryConfigured;
        private boolean tmpDirectoryConfigured;

        private Builder() {
            this.isTransient = false;
            this.allowOsPathEnvDetection = true;
            this.installationDirectory = FileUtils.discoverCanonicalDirectoryFromSystemProperty(
                    "maveniverse.toolrunner.installationDirectory", ".toolrunner");
            this.tmpDirectory = FileUtils.discoverCanonicalDirectoryFromSystemProperty(
                    "maveniverse.toolrunner.tmpDirectory", ".toolrunner/tmp");
            this.userAgent = null;
            this.httpHeaders = null;
            this.maxRunDuration = null;

            this.installationDirectoryConfigured = false;
            this.tmpDirectoryConfigured = false;
        }

        public Builder isTransient(boolean isTransient) {
            this.isTransient = isTransient;
            if (isTransient && (!installationDirectoryConfigured || !tmpDirectoryConfigured)) {
                // move directories under some random tmp directory
                Path transientInstallationDirectory = FileUtils.canonicalPath(
                                Paths.get(System.getProperty("java.io.tmpdir")))
                        .resolve("toolrunner-" + UUID.randomUUID());
                if (!installationDirectoryConfigured) {
                    this.installationDirectory = transientInstallationDirectory;
                }
                if (!tmpDirectoryConfigured) {
                    this.tmpDirectory = this.installationDirectory.resolve("tmp");
                }
            }
            return this;
        }

        public Builder allowOsPathEnvDetection(boolean allowOsPathEnvDetection) {
            this.allowOsPathEnvDetection = allowOsPathEnvDetection;
            return this;
        }

        public Builder installationDirectory(Path installationDirectory) {
            this.installationDirectory = requireNonNull(installationDirectory);
            this.installationDirectoryConfigured = true;
            if (isTransient && !tmpDirectoryConfigured) {
                this.tmpDirectory = this.installationDirectory.resolve("tmp");
            }
            return this;
        }

        public Builder tmpDirectory(Path tmpDirectory) {
            this.tmpDirectory = requireNonNull(tmpDirectory);
            this.tmpDirectoryConfigured = true;
            return this;
        }

        public Builder userAgent(String userAgent) {
            this.userAgent = requireNonNull(userAgent);
            return this;
        }

        public Builder httpHeaders(Map<String, String> httpHeaders) {
            this.httpHeaders = httpHeaders == null ? Collections.emptyMap() : httpHeaders;
            return this;
        }

        public Builder maxRunDuration(Duration maxRunDuration) {
            if (maxRunDuration != null && (maxRunDuration.isZero() || maxRunDuration.isNegative())) {
                throw new IllegalArgumentException("maxRunDuration must be positive");
            }
            this.maxRunDuration = maxRunDuration;
            return this;
        }

        public Config build() {
            return new Impl(
                    isTransient,
                    allowOsPathEnvDetection,
                    installationDirectory,
                    tmpDirectory,
                    userAgent,
                    httpHeaders,
                    maxRunDuration);
        }

        private static class Impl implements Config {
            private final boolean isTransient;
            private final boolean allowOsPathEnvDetection;
            private final String toolRunnerVersion;
            private final Path installationDirectory;
            private final Path tmpDirectory;
            private final String userAgent;
            private final Map<String, String> httpHeaders;
            private final Duration maxRunDuration;

            private Impl(
                    boolean isTransient,
                    boolean allowOsPathEnvDetection,
                    Path installationDirectory,
                    Path tmpDirectory,
                    String userAgent,
                    Map<String, String> httpHeaders,
                    Duration maxRunDuration) {
                this.toolRunnerVersion = MavenUtils.discoverArtifactVersion(
                        getClass().getClassLoader(), "eu.maveniverse.maven.toolrunner", "shared", "UNKNOWN");

                this.isTransient = isTransient;
                this.allowOsPathEnvDetection = allowOsPathEnvDetection;
                this.installationDirectory = FileUtils.normalizePath(installationDirectory);
                this.tmpDirectory = FileUtils.normalizePath(tmpDirectory);
                this.userAgent = userAgent == null ? "ToolRunner/" + this.toolRunnerVersion : userAgent;
                this.httpHeaders = httpHeaders == null
                        ? Collections.emptyMap()
                        : Collections.unmodifiableMap(new HashMap<>(httpHeaders));
                this.maxRunDuration = maxRunDuration == null ? Duration.ofHours(1L) : maxRunDuration;
            }

            @Override
            public boolean isTransient() {
                return isTransient;
            }

            @Override
            public boolean allowOsPathEnvDetection() {
                return allowOsPathEnvDetection;
            }

            public String toolRunnerVersion() {
                return toolRunnerVersion;
            }

            @Override
            public Path installationDirectory() {
                return installationDirectory;
            }

            @Override
            public Path tmpDirectory() {
                return tmpDirectory;
            }

            @Override
            public String userAgent() {
                return userAgent;
            }

            @Override
            public Map<String, String> httpHeaders() {
                return httpHeaders;
            }

            @Override
            public Duration maxRunDuration() {
                return maxRunDuration;
            }
        }
    }
}
