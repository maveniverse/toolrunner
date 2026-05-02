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
import eu.maveniverse.maven.toolrunner.shared.spi.ToolContext;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
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
public class Provisioners {
    private Provisioners() {}

    /**
     * Resolves a single artifact, and returns the backing file of it.
     *
     * @param context The MIMA context, must not be {@code null}.
     * @param artifact The GAV in form of {@code <groupId>:<artifactId>[:<extension>[:<classifier>]]:<version>}, must not be {@code null}.
     */
    public static Optional<Path> resolveArtifact(Context context, String artifact) throws IOException {
        requireNonNull(context);
        requireNonNull(artifact);
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

    /**
     * Performs a HTTP GET for given URI. Returns a temporary file that caller must delete.
     */
    public static Path httpGet(ToolContext toolContext, String serverId, URI serverUri) throws IOException {
        HttpClientBuilder builder = new MavenHttpClient4Factory(toolContext.context())
                .createResolutionClient(
                        new RemoteRepository.Builder(serverId, "default", serverUri.toString()).build());

        try (CloseableHttpClient client = builder.build()) {
            HttpGet httpGet = new HttpGet(serverUri);
            toolContext.effectiveConfig().httpHeaders().forEach(httpGet::addHeader);
            httpGet.addHeader(HTTP.USER_AGENT, toolContext.effectiveConfig().userAgent());
            try (CloseableHttpResponse response = client.execute(httpGet)) {
                HttpEntity entity = response.getEntity();
                if (entity != null) {
                    Path target =
                            Files.createTempFile(toolContext.effectiveConfig().tempDirectory(), "dl", "tmp");
                    try (OutputStream out = Files.newOutputStream(target)) {
                        entity.writeTo(out);
                    }
                    return target;
                } else {
                    throw new IOException("Unable to download file from " + serverUri);
                }
            }
        }
    }

    /**
     * Unpacks file to given directory. Supports ZIP and TAR (GZ + XZ).
     */
    public static void unpack(Path source, Path target) throws IOException {
        UnArchiver.builder().build().unarchive(source.toFile(), target.toFile());
    }
}
