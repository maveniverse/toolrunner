package eu.maveniverse.maven.toolrunner.shared;

import eu.maveniverse.maven.toolrunner.shared.support.OSTools;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class OSToolTest {
    @Test
    void smoke() throws Exception {
        Optional<Set<Path>> java = OSTools.which("foo");
        System.out.println(java.orElse(null));
        Set<Path> deref = OSTools.dereference(java.orElse(Set.of()));
        System.out.println(deref);
    }
}
