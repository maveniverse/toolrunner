/*
 * Copyright (c) 2023-2026 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.maven.toolrunner.shared.support;

import static java.util.Objects.requireNonNull;

import ca.vanzyl.provisio.archive.UnArchiver;
import eu.maveniverse.maven.mima.context.Context;
import eu.maveniverse.maven.mima.extensions.mhc4.MavenHttpClient4Factory;
import eu.maveniverse.maven.shared.core.fs.FileUtils;
import eu.maveniverse.maven.shared.core.maven.MavenUtils;
import eu.maveniverse.maven.toolrunner.shared.spi.ToolContext;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.protocol.HTTP;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.transfer.ArtifactNotFoundException;

/**
 * Reusable provisioners.
 */
public final class Provisioners {
    private Provisioners() {}

    private static final String TOOLRUNNER_METADATA = "toolrunner-metadata.properties";

    /**
     * Resolves a single artifact, and returns the backing file of it. If artifact was not found, returns empty optional.
     * If anything else than "successful" or "not-found" outcome happens, exception is thrown.
     *
     * @param toolContext The tool context, must not be {@code null}.
     * @param artifact The GAV in form of {@code <groupId>:<artifactId>[:<extension>[:<classifier>]]:<version>}, must not be {@code null}.
     */
    public static Optional<Path> resolveArtifact(ToolContext toolContext, String artifact) throws IOException {
        requireNonNull(toolContext);
        requireNonNull(artifact);
        try (Context context = toolContext.createMimaContext()) {
            try {
                return Optional.of(context.repositorySystem()
                        .resolveArtifact(
                                context.repositorySystemSession(),
                                new ArtifactRequest(
                                        new DefaultArtifact(artifact), context.remoteRepositories(), "toolrunner"))
                        .getArtifact()
                        .getFile()
                        .toPath());
            } catch (ArtifactResolutionException e) {
                if (e.getResult().getExceptions().get(0) instanceof ArtifactNotFoundException) {
                    return Optional.empty();
                }
                throw new IOException(e);
            }
        }
    }

    /**
     * Performs a HTTP GET for given URI. Returns a temporary file that caller must close (to clean up).
     */
    public static FileUtils.TempFile httpGet(ToolContext toolContext, String serverId, URI resourceUri)
            throws IOException {
        try (Context context = toolContext.createMimaContext()) {
            HttpClientBuilder builder = new MavenHttpClient4Factory(context)
                    .createResolutionClient(
                            new RemoteRepository.Builder(serverId, "default", resourceUri.toString()).build());

            try (CloseableHttpClient client = builder.build()) {
                HttpGet httpGet = new HttpGet(resourceUri);
                toolContext.httpHeaders().forEach(httpGet::addHeader);
                httpGet.addHeader(HTTP.USER_AGENT, toolContext.userAgent());
                try (CloseableHttpResponse response = client.execute(httpGet)) {
                    HttpEntity entity = response.getEntity();
                    if (entity != null) {
                        FileUtils.TempFile result =
                                FileUtils.newTempFile(toolContext.tempDirectory(), detectExtension(resourceUri));
                        try (OutputStream out = Files.newOutputStream(result.getPath())) {
                            entity.writeTo(out);
                        }
                        return result;
                    } else {
                        throw new IOException("Unable to download file from " + resourceUri);
                    }
                }
            }
        }
    }

    /**
     * Returns detected and known, most common extensions, if detected, otherwise {@code ".tmp"}.
     * Leading dot is always present.
     */
    public static String detectExtension(URI resourceUri) {
        String path = resourceUri.getPath();
        if (path != null) {
            int lastSlash = path.lastIndexOf('/');
            if (lastSlash >= 0) {
                String filename = path.substring(lastSlash + 1);
                int lastDot = filename.lastIndexOf('.');
                if (lastDot >= 0) {
                    int tar = filename.indexOf(".tar");
                    if (tar > 0) {
                        return filename.substring(tar);
                    } else {
                        return filename.substring(lastDot);
                    }
                }
            }
        }

        return ".tmp";
    }

    /**
     * Unpacks file to given directory. Supports ZIP and TAR (GZ + XZ).
     */
    public static void unpack(ToolContext toolContext, Path source, Path target, boolean useRoot) throws IOException {
        requireNonNull(source);
        requireNonNull(target);
        if (!Files.isRegularFile(source)) {
            throw new IllegalArgumentException("source is not a regular file");
        }
        Files.createDirectories(target);
        UnArchiver.builder().useRoot(useRoot).build().unarchive(source.toFile(), target.toFile());
    }

    public static Optional<Map<String, String>> loadMetadata(ToolContext toolContext, Path toolHome)
            throws IOException {
        requireNonNull(toolHome);
        if (!Files.isDirectory(toolHome)) {
            throw new IllegalArgumentException("Tool home is not a directory");
        }
        Path metadataPath = toolHome.resolve(TOOLRUNNER_METADATA);
        if (Files.isRegularFile(metadataPath)) {
            try (InputStream in = Files.newInputStream(metadataPath)) {
                Properties props = new Properties();
                props.load(in);
                return Optional.of(MavenUtils.toMap(props));
            }
        }
        return Optional.empty();
    }

    public static void saveMetadata(ToolContext toolContext, Path toolHome, Map<String, String> metadata)
            throws IOException {
        requireNonNull(toolHome);
        requireNonNull(metadata);
        if (!Files.isDirectory(toolHome)) {
            throw new IllegalArgumentException("Tool home is not a directory");
        }
        Path metadataPath = toolHome.resolve(TOOLRUNNER_METADATA);
        try (OutputStream out = Files.newOutputStream(metadataPath)) {
            Properties props = new Properties();
            props.putAll(metadata);
            props.store(out, null);
        }
    }
}
