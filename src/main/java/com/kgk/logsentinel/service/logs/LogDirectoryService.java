package com.kgk.logsentinel.service.logs;

import com.kgk.logsentinel.dto.LogFileEntry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Service
public class LogDirectoryService {

    private static final Pattern ROLLED_SUFFIX = Pattern.compile("\\.(\\d+)$");

    private final Path activeLogFile;
    private final int maxRollIndex;

    public LogDirectoryService(
            @Value("${logsentinel.log-file}") String logFilePath,
            @Value("${logsentinel.log-rotation.max-index:3}") int maxRollIndex) {
        this.activeLogFile = Path.of(logFilePath).toAbsolutePath().normalize();
        this.maxRollIndex = maxRollIndex;
    }

    public Path activeLogFile() {
        return activeLogFile;
    }

    public List<LogFileEntry> listLogFiles() throws IOException {
        Path dir = activeLogFile.getParent();
        if (dir == null || !Files.isDirectory(dir)) {
            return List.of();
        }
        String baseName = activeLogFile.getFileName().toString();
        List<LogFileEntry> entries = new ArrayList<>();
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> isManagedLogFile(p, baseName))
                    .forEach(p -> entries.add(toEntry(p)));
        }
        entries.sort(Comparator.comparingInt((LogFileEntry e) -> rollIndex(Path.of(e.path()), baseName))
                .reversed()
                .thenComparing(LogFileEntry::name));
        return entries;
    }

    static int rollIndex(Path path, String baseFileName) {
        String fileName = path.getFileName().toString();
        if (fileName.equals(baseFileName)) {
            return 0;
        }
        if (!fileName.startsWith(baseFileName + ".")) {
            return -1;
        }
        String tail = fileName.substring(baseFileName.length() + 1);
        if (tail.matches("\\d+")) {
            return Integer.parseInt(tail);
        }
        return -1;
    }

    private boolean isManagedLogFile(Path path, String baseName) {
        String fileName = path.getFileName().toString();
        if (fileName.equals(baseName)) {
            return true;
        }
        if (!fileName.startsWith(baseName + ".")) {
            return false;
        }
        String tail = fileName.substring(baseName.length() + 1);
        if (!tail.matches("\\d+")) {
            return false;
        }
        int index = Integer.parseInt(tail);
        return index >= 1 && index <= maxRollIndex;
    }

    private static LogFileEntry toEntry(Path path) {
        try {
            return new LogFileEntry(
                    path.toString(),
                    path.getFileName().toString(),
                    Files.size(path),
                    Files.getLastModifiedTime(path).toMillis());
        } catch (IOException e) {
            return new LogFileEntry(path.toString(), path.getFileName().toString(), -1L, 0L);
        }
    }
}
