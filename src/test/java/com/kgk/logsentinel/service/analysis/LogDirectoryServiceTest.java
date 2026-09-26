package com.kgk.logsentinel.service.analysis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogDirectoryServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void listsActiveAndRolledFilesSortedOldestFirst() throws Exception {
        Path active = tempDir.resolve("log-sentinel-app.log");
        Path rolled1 = tempDir.resolve("log-sentinel-app.log.1");
        Path rolled2 = tempDir.resolve("log-sentinel-app.log.2");
        Files.writeString(active, "active\n");
        Files.writeString(rolled1, "one\n");
        Files.writeString(rolled2, "two\n");

        LogDirectoryService service = new LogDirectoryService(active.toString(), 3);
        var entries = service.listLogFiles();

        assertEquals(3, entries.size());
        assertEquals("log-sentinel-app.log.2", entries.get(0).name());
        assertEquals("log-sentinel-app.log.1", entries.get(1).name());
        assertEquals("log-sentinel-app.log", entries.get(2).name());
        assertTrue(entries.get(0).size() > 0);
    }
}
