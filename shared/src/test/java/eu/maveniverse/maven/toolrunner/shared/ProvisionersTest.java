package eu.maveniverse.maven.toolrunner.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import eu.maveniverse.maven.toolrunner.shared.internal.DefaultToolManager;
import eu.maveniverse.maven.toolrunner.shared.support.Provisioners;
import java.io.IOException;
import java.net.URI;
import org.junit.jupiter.api.Test;

public class ProvisionersTest {
    @Test
    void detectExtension() {
        String ext;

        ext = Provisioners.detectExtension(URI.create("file:///"));
        assertEquals(".tmp", ext);
        ext = Provisioners.detectExtension(URI.create("https://www.server.com/foo"));
        assertEquals(".tmp", ext);
        ext = Provisioners.detectExtension(URI.create("https://www.server.com/foo.txt"));
        assertEquals(".txt", ext);
        ext = Provisioners.detectExtension(URI.create("https://www.server.com/foo.zip?color=blue"));
        assertEquals(".zip", ext);
        ext = Provisioners.detectExtension(URI.create("https://www.server.com/foo.tar?color=blue"));
        assertEquals(".tar", ext);
        ext = Provisioners.detectExtension(URI.create("https://www.server.com/foo.tar.gz?color=blue"));
        assertEquals(".tar.gz", ext);
    }

    @Test
    void discoverGHLatest() throws IOException {
        try (DefaultToolManager manager =
                new DefaultToolManager(Config.builder().isTransient(true).build())) {
            Provisioners.GHRelease rel = Provisioners.discoverGHLatest(manager, "github", "jbangdev", "jbang");
            assertNotNull(rel);
            assertNotNull(rel.getName());
            assertNotNull(rel.getTag());
        }
    }
}
