package com.talhanation.bannermod.events;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EventPackageContractTest {

    @Test
    void eventsPackageContainsOnlyHandlersOrEventPayloads() throws IOException {
        Path eventsRoot = Path.of("src/main/java/com/talhanation/bannermod/events");
        List<String> violations = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(eventsRoot)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                .forEach(path -> {
                    try {
                        String source = Files.readString(path);
                        if (!source.contains("@SubscribeEvent") && !source.contains("extends Event")) {
                            violations.add(eventsRoot.relativize(path).toString());
                        }
                    } catch (IOException exception) {
                        throw new IllegalStateException("Unable to read " + path, exception);
                    }
                });
        }

        assertTrue(violations.isEmpty(),
            "events/ may contain only @SubscribeEvent hosts or NeoForge Event payloads: " + violations);
    }
}
