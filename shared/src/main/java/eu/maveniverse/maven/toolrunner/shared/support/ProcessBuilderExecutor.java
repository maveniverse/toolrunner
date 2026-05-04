/*
 * Copyright (c) 2023-2026 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.maven.toolrunner.shared.support;

import eu.maveniverse.maven.toolrunner.shared.ToolExecution;
import eu.maveniverse.maven.toolrunner.shared.ToolHandle;
import eu.maveniverse.maven.toolrunner.shared.spi.ToolProvider;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Reusable executor using {@link ProcessBuilder}.
 */
public final class ProcessBuilderExecutor {
    private ProcessBuilderExecutor() {}

    /**
     * The result carrier.
     */
    public static class ProcessBuilderToolExecutorResult implements ToolHandle.Result {
        private final int exitCode;

        public ProcessBuilderToolExecutorResult(int exitCode) {
            this.exitCode = exitCode;
        }

        @Override
        public boolean success() {
            return exitCode == 0;
        }

        public int getExitCode() {
            return exitCode;
        }
    }

    /**
     * Executes given execution.
     */
    public static ProcessBuilderToolExecutorResult execute(ToolExecution execution, long timeoutMillis)
            throws IOException, InterruptedException {
        // Adjust the process invocation to circumvent possible limited buffers
        ArrayList<String> command = new ArrayList<>();
        if (ToolProvider.IS_WINDOWS) {
            command.add("cmd.exe");
            command.add("/c");
        } else {
            command.add("sh");
            command.add("-c");
        }
        command.add("\""
                + Stream.concat(Stream.of(execution.command()), execution.arguments().stream())
                        .map(s -> s.replace("\"", "\\\""))
                        .collect(Collectors.joining("\" \""))
                + "\"");

        ProcessBuilder pb =
                new ProcessBuilder().command(command).directory(execution.cwd().toFile());

        execution.environmentVariables().ifPresent(env -> pb.environment().putAll(env));

        Process process = pb.start();
        try {
            if (pump(process, execution).await(timeoutMillis, TimeUnit.MILLISECONDS)) {
                return new ProcessBuilderToolExecutorResult(process.waitFor());
            } else {
                process.destroyForcibly();
                throw new IOException("Process timeout");
            }
        } catch (InterruptedException e) {
            process.destroyForcibly();
            throw e;
        }
    }

    /**
     * Pumps standard streams of sub-process to execution provided streams.
     */
    public static CountDownLatch pump(Process p, ToolExecution execution) {
        CountDownLatch latch = new CountDownLatch(3);
        String suffix = "-pump-" + p.pid();
        Thread stdoutPump = new Thread(() -> {
            try (OutputStream stdout = execution.stdOut().orElse(OutputStream.nullOutputStream())) {
                p.getInputStream().transferTo(stdout);
                stdout.flush();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } finally {
                latch.countDown();
            }
        });
        stdoutPump.setName("stdout" + suffix);
        stdoutPump.setDaemon(true);
        stdoutPump.start();
        Thread stderrPump = new Thread(() -> {
            try (OutputStream stderr = execution.stdErr().orElse(OutputStream.nullOutputStream())) {
                p.getErrorStream().transferTo(stderr);
                stderr.flush();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } finally {
                latch.countDown();
            }
        });
        stderrPump.setName("stderr" + suffix);
        stderrPump.setDaemon(true);
        stderrPump.start();
        Thread stdinPump = new Thread(() -> {
            try (OutputStream in = p.getOutputStream()) {
                execution.stdIn().orElse(InputStream.nullInputStream()).transferTo(in);
                in.flush();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } finally {
                latch.countDown();
            }
        });
        stdinPump.setName("stdin" + suffix);
        stdinPump.setDaemon(true);
        stdinPump.start();
        return latch;
    }
}
