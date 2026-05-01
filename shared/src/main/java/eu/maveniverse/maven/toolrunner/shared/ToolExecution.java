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
package eu.maveniverse.maven.toolrunner.shared;

import static java.util.Objects.requireNonNull;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Represents a request to execute a tool with command-line arguments.
 */
public interface ToolExecution {
    /**
     * The command to execute, ie "jbang".
     */
    String command();

    /**
     * The immutable list of arguments to pass to the command.
     */
    List<String> arguments();

    /**
     * Returns the current working directory for the Maven execution.
     * This is typically the directory from which Maven was invoked.
     *
     * @return the current working directory path
     */
    Path cwd();

    /**
     * Returns the map of environment variables to set before executing process.
     * This property is used ONLY by executors that spawn a new JVM.
     *
     * @return an Optional containing the map of environment variables, or empty if not specified
     */
    Optional<Map<String, String>> environmentVariables();

    /**
     * Optional provider for STD in of the Maven. If given, this provider will be piped into std input of
     * Maven.
     *
     * @return an Optional containing the stdin provider, or empty if not specified.
     */
    Optional<InputStream> stdIn();

    /**
     * Optional consumer for STD out of the Maven. If given, this consumer will get all output from the std out of
     * Maven. Note: whether consumer gets to consume anything depends on invocation arguments passed in
     * {@link #arguments()}, as if log file is set, not much will go to stdout.
     *
     * @return an Optional containing the stdout consumer, or empty if not specified.
     */
    Optional<OutputStream> stdOut();

    /**
     * Optional consumer for STD err of the Maven. If given, this consumer will get all output from the std err of
     * Maven. Note: whether consumer gets to consume anything depends on invocation arguments passed in
     * {@link #arguments()}, as if log file is set, not much will go to stderr.
     *
     * @return an Optional containing the stderr consumer, or empty if not specified.
     */
    Optional<OutputStream> stdErr();

    /**
     * Simplest result, as interpreted by tool provider. The actual result may be much more than just this,
     * depends on provider.
     */
    interface Result {
        boolean success();
    }

    /**
     * Returns {@link Builder} created from this instance.
     */
    default Builder toBuilder() {
        return new Builder(
                command(),
                arguments(),
                cwd(),
                environmentVariables().orElse(null),
                stdIn().orElse(null),
                stdOut().orElse(null),
                stdErr().orElse(null));
    }

    /**
     * Returns new builder pre-set to run command. The discovery of user cwd also discovered by standard means.
     */
    static Builder ofCommand(String command) {
        return new Builder(
                command, null, getCanonicalPath(Path.of(System.getProperty("user.dir"))), null, null, null, null);
    }

    class Builder {
        private String command;
        private List<String> arguments;
        private Path cwd;
        private Map<String, String> environmentVariables;
        private InputStream stdIn;
        private OutputStream stdOut;
        private OutputStream stdErr;

        private Builder() {}

        private Builder(
                String command,
                List<String> arguments,
                Path cwd,
                Map<String, String> environmentVariables,
                InputStream stdIn,
                OutputStream stdOut,
                OutputStream stdErr) {
            this.command = command;
            this.arguments = arguments;
            this.cwd = cwd;
            this.environmentVariables = environmentVariables;
            this.stdIn = stdIn;
            this.stdOut = stdOut;
            this.stdErr = stdErr;
        }

        public Builder command(String command) {
            this.command = requireNonNull(command, "command");
            return this;
        }

        public Builder arguments(String... arguments) {
            return arguments(Arrays.asList(arguments));
        }

        public Builder arguments(List<String> arguments) {
            this.arguments = new ArrayList<>(requireNonNull(arguments, "arguments"));
            return this;
        }

        public Builder argument(String argument) {
            return addArguments(List.of(argument));
        }

        public Builder addArguments(String... arguments) {
            return addArguments(Arrays.asList(arguments));
        }

        public Builder addArguments(List<String> arguments) {
            if (this.arguments == null) {
                this.arguments = new ArrayList<>();
            }
            this.arguments.addAll(requireNonNull(arguments, "argument"));
            return this;
        }

        public Builder cwd(Path cwd) {
            this.cwd = getCanonicalPath(requireNonNull(cwd, "cwd"));
            return this;
        }

        public Builder environmentVariables(Map<String, String> environmentVariables) {
            this.environmentVariables = environmentVariables;
            return this;
        }

        public Builder environmentVariable(String key, String value) {
            requireNonNull(key, "env key");
            requireNonNull(value, "env value");
            if (environmentVariables == null) {
                this.environmentVariables = new HashMap<>();
            }
            this.environmentVariables.put(key, value);
            return this;
        }

        public Builder stdIn(InputStream stdIn) {
            this.stdIn = stdIn;
            return this;
        }

        public Builder stdOut(OutputStream stdOut) {
            this.stdOut = stdOut;
            return this;
        }

        public Builder stdErr(OutputStream stdErr) {
            this.stdErr = stdErr;
            return this;
        }

        public ToolExecution build() {
            return new Impl(command, arguments, cwd, environmentVariables, stdIn, stdOut, stdErr);
        }

        private static class Impl implements ToolExecution {
            private final String command;
            private final List<String> arguments;
            private final Path cwd;
            private final Map<String, String> environmentVariables;
            private final InputStream stdIn;
            private final OutputStream stdOut;
            private final OutputStream stdErr;

            private Impl(
                    String command,
                    List<String> arguments,
                    Path cwd,
                    Map<String, String> environmentVariables,
                    InputStream stdIn,
                    OutputStream stdOut,
                    OutputStream stdErr) {
                this.command = requireNonNull(command);
                this.arguments = arguments == null ? Collections.emptyList() : new ArrayList<>(arguments);
                this.cwd = getCanonicalPath(requireNonNull(cwd));
                this.environmentVariables = environmentVariables != null && !environmentVariables.isEmpty()
                        ? Collections.unmodifiableMap(new HashMap<>(environmentVariables))
                        : null;
                this.stdIn = stdIn;
                this.stdOut = stdOut;
                this.stdErr = stdErr;
            }

            @Override
            public String command() {
                return command;
            }

            @Override
            public List<String> arguments() {
                return arguments;
            }

            @Override
            public Path cwd() {
                return cwd;
            }

            @Override
            public Optional<Map<String, String>> environmentVariables() {
                return Optional.ofNullable(environmentVariables);
            }

            @Override
            public Optional<InputStream> stdIn() {
                return Optional.ofNullable(stdIn);
            }

            @Override
            public Optional<OutputStream> stdOut() {
                return Optional.ofNullable(stdOut);
            }

            @Override
            public Optional<OutputStream> stdErr() {
                return Optional.ofNullable(stdErr);
            }
        }
    }

    static Path discoverUserHomeDirectory() {
        String userHome = System.getProperty("user.home");
        if (userHome == null) {
            throw new IllegalStateException("Requires user.home Java System Property set");
        }
        return getCanonicalPath(Path.of(userHome));
    }

    static Path getCanonicalPath(Path path) {
        requireNonNull(path, "path");
        return path.toAbsolutePath().normalize();
    }
}
