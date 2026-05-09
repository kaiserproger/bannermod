package com.talhanation.bannermod.events;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EventPackageContractTest {
    private static final Pattern SUBSCRIBE_EVENT_ANNOTATION = Pattern.compile("(?m)^\\s*@SubscribeEvent\\b");

    @Test
    void eventsPackageContainsOnlySubscribeEventHosts() throws IOException {
        Path eventsRoot = Path.of("src", "main", "java", "com", "talhanation", "bannermod", "events");
        List<String> violations = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(eventsRoot)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                .sorted()
                .forEach(path -> {
                    try {
                        String source = Files.readString(path);
                        if (!SUBSCRIBE_EVENT_ANNOTATION.matcher(source).find()) {
                            violations.add(eventsRoot.relativize(path).toString());
                        }
                    } catch (IOException exception) {
                        throw new IllegalStateException("Unable to read " + path, exception);
                    }
                });
        }

        assertTrue(violations.isEmpty(),
            "events/ may contain only @SubscribeEvent hosts: " + violations);
    }
}
