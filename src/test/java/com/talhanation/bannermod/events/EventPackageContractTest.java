package com.talhanation.bannermod.events;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EventPackageContractTest {
    private static final Pattern SUBSCRIBE_EVENT_ANNOTATION = Pattern.compile("(?m)^\\s*@SubscribeEvent\\b");
    private static final Pattern EVENT_PAYLOAD_DECLARATION = Pattern.compile(
        "(?m)^\\s*(?:(?:public|protected|private|static|abstract|final|sealed|non-sealed|strictfp)\\s+)*"
            + "(?:class|record)\\s+\\w+\\s+extends\\s+Event\\b");

    @Test
    void eventsPackageContainsOnlyHandlersOrEventPayloads() throws IOException {
        Path eventsRoot = Path.of("src", "main", "java", "com", "talhanation", "bannermod", "events");
        List<String> violations = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(eventsRoot)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                .sorted()
                .forEach(path -> {
                    try {
                        String source = Files.readString(path);
                        if (!isEventHandlerOrPayload(source)) {
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

    private static boolean isEventHandlerOrPayload(String source) {
        return SUBSCRIBE_EVENT_ANNOTATION.matcher(source).find()
            || EVENT_PAYLOAD_DECLARATION.matcher(source).find();
    }
}
