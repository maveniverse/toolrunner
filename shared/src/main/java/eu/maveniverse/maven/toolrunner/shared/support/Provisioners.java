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
import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.protocol.HTTP;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.transfer.ArtifactNotFoundException;
import org.eclipse.aether.version.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reusable provisioners.
 */
public final class Provisioners {
    private Provisioners() {}

    private static final String TOOLRUNNER_METADATA = "toolrunner-metadata.properties";

    private static final Logger LOGGER = LoggerFactory.getLogger(Provisioners.class);

    /**
     * Special version, to be used when "latest" is needed to be resolved with {@link #resolveArtifact(ToolContext, String)}.
     */
    public static final String RELEASE_VERSION = "RELEASE";

    /**
     * Resolves a single artifact, and returns the backing file of it. If artifact was not found, returns empty optional.
     * If anything else than "successful" or "not-found" outcome happens, exception is thrown.
     * Supports only {@link #RELEASE_VERSION}, but no ranges and all the fluff.
     *
     * @param toolContext The tool context, must not be {@code null}.
     * @param gav The GAV in form of {@code <groupId>:<artifactId>[:<extension>[:<classifier>]]:<version>}, must not be {@code null}.
     * @return resolved {@link Artifact}, or empty optional, if "not-found".
     */
    public static Optional<Artifact> resolveArtifact(ToolContext toolContext, String gav) throws IOException {
        requireNonNull(toolContext);
        requireNonNull(gav);
        LOGGER.debug("Resolving artifact {}", gav);
        try (Context context = toolContext.createMimaContext()) {
            RepositorySystem repositorySystem = context.repositorySystem();
            RepositorySystemSession session = context.repositorySystemSession();
            try {
                Artifact artifact = new DefaultArtifact(gav);
                if (RELEASE_VERSION.equals(artifact.getVersion())) {
                    artifact = artifact.setVersion("[0,)");
                    VersionRangeResult versionRangeResult = repositorySystem.resolveVersionRange(
                            session, new VersionRangeRequest(artifact, context.remoteRepositories(), "toolrunner"));
                    if (versionRangeResult.getVersions().isEmpty()) {
                        throw new IOException("No versions found for artifact " + gav);
                    }
                    artifact = artifact.setVersion(selectVersion(artifact, versionRangeResult.getVersions()));
                }
                return Optional.of(repositorySystem
                        .resolveArtifact(
                                session, new ArtifactRequest(artifact, context.remoteRepositories(), "toolrunner"))
                        .getArtifact());
            } catch (VersionRangeResolutionException e) {
                throw new IOException("Unable to resolve artifact version " + gav, e);
            } catch (ArtifactResolutionException e) {
                if (!e.getResult().getExceptions().isEmpty()
                        && e.getResult().getExceptions().get(0) instanceof ArtifactNotFoundException) {
                    LOGGER.debug("Artifact {} NOT FOUND", gav);
                    return Optional.empty();
                }
                throw new IOException("Unable to resolve artifact " + gav, e);
            }
        }
    }

    /**
     * Selects gratest, non-snapshot, non-preview version.
     */
    private static String selectVersion(Artifact artifact, List<Version> versionRangeResult) throws IOException {
        Artifact candidate = artifact;
        ArrayList<Version> descending = new ArrayList<>(versionRangeResult);
        Collections.reverse(descending);
        for (Version version : descending) {
            candidate = candidate.setVersion(version.toString());
            if (candidate.isSnapshot()) {
                continue;
            }
            if (isPreviewVersion(version.toString())) {
                continue;
            }
            break;
        }
        if (!candidate.isSnapshot() && !isPreviewVersion(candidate.getVersion())) {
            return candidate.getVersion();
        } else {
            throw new IOException("No suitable tool version found " + versionRangeResult);
        }
    }

    /**
     * Returns {@code true} if version is "preview version".
     */
    private static boolean isPreviewVersion(String version) {
        // most trivial "preview" version is 'a1'
        if (version.length() > 1) {
            String ver = version.toLowerCase(Locale.ENGLISH);
            // simple case: contains any of these
            if (ver.contains("alpha")
                    || ver.contains("beta")
                    || ver.contains("milestone")
                    || ver.contains("rc")
                    || ver.contains("cr")) {
                return true;
            }
            // complex case: contains 'a', 'b' or 'm' followed immediately by number
            for (char ch : new char[] {'a', 'b', 'm'}) {
                int idx = ver.lastIndexOf(ch);
                if (idx > -1 && ver.length() > idx + 1) {
                    if (Character.isDigit(ver.charAt(idx + 1))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Performs a HTTP GET for given URI. Returns a temporary file that caller must close (to clean up).
     */
    public static FileUtils.TempFile httpGet(ToolContext toolContext, String serverId, URI resourceUri)
            throws IOException {
        requireNonNull(toolContext);
        requireNonNull(serverId);
        requireNonNull(resourceUri);
        try (Context context = toolContext.createMimaContext()) {
            HttpClientBuilder builder = new MavenHttpClient4Factory(context)
                    .createResolutionClient(
                            new RemoteRepository.Builder(serverId, "default", resourceUri.toString()).build());

            try (CloseableHttpClient client = builder.build()) {
                HttpGet httpGet = new HttpGet(resourceUri);
                toolContext.config().httpHeaders().forEach(httpGet::addHeader);
                httpGet.addHeader(HTTP.USER_AGENT, toolContext.config().userAgent());
                try (CloseableHttpResponse response = client.execute(httpGet)) {
                    HttpEntity entity = response.getEntity();
                    if (entity != null) {
                        FileUtils.TempFile result = FileUtils.newTempFile(
                                toolContext.config().tmpDirectory(), detectExtension(resourceUri));
                        try (OutputStream out = Files.newOutputStream(result.getPath())) {
                            entity.writeTo(out);
                        }
                        LOGGER.debug("HTTP GET from {} to {}", resourceUri.toASCIIString(), result.getPath());
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
        requireNonNull(toolContext);
        requireNonNull(source);
        requireNonNull(target);
        if (!Files.isRegularFile(source)) {
            throw new IllegalArgumentException("source is not a regular file");
        }
        Files.createDirectories(target);
        UnArchiver.builder().useRoot(useRoot).build().unarchive(source.toFile(), target.toFile());
        LOGGER.debug("Unpack (useRoot={}) from {} to {}", useRoot, source, target);
        tree(target, 0, (p, d) -> {
            String prefix = IntStream.range(0, d).mapToObj(i -> " ").collect(Collectors.joining(""));
            LOGGER.debug("{} {} {}", prefix, Files.isDirectory(p) ? "+" : "-", p.getFileName());
        });
    }

    private static void tree(Path directory, int depth, BiConsumer<Path, Integer> consumer) throws IOException {
        ArrayList<Path> entries = new ArrayList<>();
        try (Stream<Path> stream = Files.list(directory)) {
            stream.forEach(entries::add);
        }
        for (Path entry : entries) {
            consumer.accept(entry, depth);
            if (Files.isDirectory(entry)) {
                tree(entry, depth + 1, consumer);
            }
        }
    }

    public static final class GHRelease {
        private final String name;
        private final String tag;

        private GHRelease(String name, String tag) {
            this.name = name;
            this.tag = tag;
        }

        public String getName() {
            return name;
        }

        public String getTag() {
            return tag;
        }

        @Override
        public String toString() {
            return "GHRelease{" + "name='" + name + '\'' + ", tag='" + tag + '\'' + '}';
        }
    }

    /**
     * GH: discovers latest release of a given {@code owner/repo} project.
     */
    public static GHRelease discoverGHLatest(ToolContext toolContext, String serverId, String owner, String repo)
            throws IOException {
        requireNonNull(toolContext);
        requireNonNull(serverId);
        requireNonNull(owner);
        requireNonNull(repo);
        URI uri = URI.create("https://api.github.com/repos/" + owner + "/" + repo + "/releases/latest");
        try (FileUtils.TempFile tempFile = httpGet(toolContext, serverId, uri);
                JsonReader reader = new JsonReader(Files.newBufferedReader(tempFile.getPath()))) {
            try {
                Map<String, Object> data = new Gson().fromJson(reader, Map.class);
                String name = requireNonNull((String) data.get("name"));
                String tag = requireNonNull((String) data.get("tag_name"));
                return new GHRelease(name, tag);
            } catch (Exception e) {
                throw new IllegalStateException("Unexpected GH response", e);
            }
        }
    }

    public static Optional<Map<String, String>> loadMetadata(ToolContext toolContext, Path toolHome)
            throws IOException {
        requireNonNull(toolContext);
        requireNonNull(toolHome);
        if (!Files.isDirectory(toolHome)) {
            throw new IllegalArgumentException("Tool home is not a directory");
        }
        Path metadataPath = toolHome.resolve(TOOLRUNNER_METADATA);
        if (Files.isRegularFile(metadataPath)) {
            try (InputStream in = Files.newInputStream(metadataPath)) {
                Properties props = new Properties();
                props.load(in);
                LOGGER.debug("loadMetadata from {} loaded {}", toolHome, props);
                return Optional.of(MavenUtils.toMap(props));
            }
        }
        LOGGER.debug("loadMetadata from {} found nothing", toolHome);
        return Optional.empty();
    }

    public static void saveMetadata(ToolContext toolContext, Path toolHome, Map<String, String> metadata)
            throws IOException {
        requireNonNull(toolContext);
        requireNonNull(toolHome);
        requireNonNull(metadata);
        if (!Files.isDirectory(toolHome)) {
            throw new IllegalArgumentException("Tool home is not a directory");
        }
        LOGGER.debug("saveMetadata to {}", toolHome);
        Path metadataPath = toolHome.resolve(TOOLRUNNER_METADATA);
        try (OutputStream out = Files.newOutputStream(metadataPath)) {
            Properties props = new Properties();
            props.putAll(metadata);
            props.store(out, null);
        }
    }
}
