/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 *
 * Project: GomsBook AI
 * AI-powered EPUB authoring, validation, accessibility, and publishing automation.
 */
package kr.co.goms.gomsbook.ai.epub.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import kr.co.goms.gomsbook.ai.epub.model.EpubCheckMessage;
import kr.co.goms.gomsbook.ai.epub.model.EpubCheckResult;

/**
 * EPUBCheck 배포본을 외부 Java Process로 실행합니다.
 *
 * <p>Agent OSGi Bundle에서 EPUBCheck Java API를 직접 로딩하지 않고,
 * epubcheck.jar와 lib/*를 별도 Process classpath로 사용합니다.</p>
 */
public final class EpubCheckRunner {

    private static final String EPUBCHECK_JAR_PREFIX = "epubcheck-";
    private static final String EPUBCHECK_JAR_SUFFIX = ".jar";
    private static final String EPUBCHECK_LIB_DIRECTORY = "lib";
    private static final String EPUBCHECK_MAIN_CLASS = "com.adobe.epubcheck.tool.Checker";

    private final Path epubCheckDirectory;
    private final String epubCheckVersion;

    public EpubCheckRunner(Path epubCheckDirectory, String epubCheckVersion) {

        if (epubCheckDirectory == null) throw new IllegalArgumentException("epubCheckDirectory must not be null.");

        this.epubCheckDirectory = epubCheckDirectory.toAbsolutePath().normalize();
        this.epubCheckVersion = normalizeVersion(epubCheckVersion);

        validateEpubCheckRuntime();
    }

    public EpubCheckResult run(Path epubFile) {

        validateEpubFile(epubFile);

        Path normalizedEpubFile = epubFile.toAbsolutePath().normalize();
        Path reportFile = null;

        try {

            reportFile = Files.createTempFile("gomsbook-epubcheck-", ".json");

            ProcessResult processResult = execute(normalizedEpubFile, reportFile);

            validateProcessResult(processResult, reportFile);

            List<EpubCheckMessage> messages = readMessages(reportFile);

            return new EpubCheckResult(normalizedEpubFile.getFileName().toString(), epubCheckVersion, messages);

        } catch (IOException exception) {

            throw new IllegalStateException("Failed to execute EPUBCheck: " + normalizedEpubFile, exception);

        } catch (InterruptedException exception) {

            Thread.currentThread().interrupt();

            throw new IllegalStateException("EPUBCheck execution was interrupted: " + normalizedEpubFile, exception);

        } finally {

            deleteQuietly(reportFile);
        }
    }

    private ProcessResult execute(Path epubFile, Path reportFile) throws IOException, InterruptedException {

        String classpath = createClasspath();

        ProcessBuilder builder = new ProcessBuilder(
                "java",
                "-Dfile.encoding=UTF-8",
                "-cp",
                classpath,
                EPUBCHECK_MAIN_CLASS,
                epubFile.toString(),
                "--json",
                reportFile.toString());

        builder.directory(epubCheckDirectory.toFile());
        builder.redirectErrorStream(true);

        Process process = builder.start();

        String output = readProcessOutput(process);

        int exitCode = process.waitFor();

        return new ProcessResult(exitCode, output);
    }

    private String getEpubCheckJarName() {
        return EPUBCHECK_JAR_PREFIX + epubCheckVersion + EPUBCHECK_JAR_SUFFIX;
    }

    private String createClasspath() {

        String epubCheckJar = epubCheckDirectory.resolve(getEpubCheckJarName()).toString();
        String epubCheckLib = epubCheckDirectory.resolve(EPUBCHECK_LIB_DIRECTORY).toString() + File.separator + "*";

        return String.join(File.pathSeparator, epubCheckJar, epubCheckLib);
    }

    private String readProcessOutput(Process process) throws IOException {

        StringBuilder output = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {

            String line;

            while ((line = reader.readLine()) != null) output.append(line).append(System.lineSeparator());
        }

        return output.toString().trim();
    }

    private void validateProcessResult(ProcessResult processResult, Path reportFile) {

        /*
         * EPUBCheck는 검증 오류가 발견된 경우 exit code 1을 반환할 수 있습니다.
         * 이것은 Tool 실행 실패가 아니라 EPUB 검증 결과입니다.
         */
        if (processResult.getExitCode() > 1) {

            throw new IllegalStateException(
                    "EPUBCheck process failed with exit code "
                            + processResult.getExitCode()
                            + ": "
                            + processResult.getOutput());
        }

        if (!Files.exists(reportFile)) throw new IllegalStateException("EPUBCheck JSON report was not created: " + processResult.getOutput());

        try {

            if (Files.size(reportFile) == 0) throw new IllegalStateException("EPUBCheck JSON report is empty: " + processResult.getOutput());

        } catch (IOException exception) {

            throw new IllegalStateException("Failed to inspect EPUBCheck JSON report: " + reportFile, exception);
        }
    }

    private List<EpubCheckMessage> readMessages(Path reportFile) {

        try {

            String json = Files.readString(reportFile, StandardCharsets.UTF_8);

            JsonElement rootElement = JsonParser.parseString(json);

            if (!rootElement.isJsonObject()) throw new IllegalStateException("EPUBCheck JSON report root must be an object.");

            JsonObject root = rootElement.getAsJsonObject();

            JsonArray messages = readArray(root, "messages");

            if (messages == null) return List.of();

            List<EpubCheckMessage> results = new ArrayList<>();

            for (JsonElement element : messages) {

                if (!element.isJsonObject()) continue;

                results.addAll(readMessages(element.getAsJsonObject()));
            }

            return List.copyOf(results);

        } catch (IOException exception) {

            throw new IllegalStateException("Failed to read EPUBCheck JSON report: " + reportFile, exception);

        } catch (RuntimeException exception) {

            throw new IllegalStateException("Failed to parse EPUBCheck JSON report: " + reportFile, exception);
        }
    }

    /**
     * EPUBCheck JSON message 하나를 location 단위의 EpubCheckMessage 목록으로 변환합니다.
     *
     * <p>동일한 EPUBCheck 오류 코드와 메시지라도 locations가 여러 개이면
     * 각각 독립적인 검증 오류로 보존합니다.</p>
     */
    private List<EpubCheckMessage> readMessages(JsonObject object) {

        String id = readString(object, "ID");

        if (id == null) id = readString(object, "id");

        String severity = readString(object, "severity");
        String message = readString(object, "message");

        if (severity == null || message == null) return List.of();

        JsonArray locations = readArray(object, "locations");

        if (locations == null || locations.isEmpty()) {

            return List.of(new EpubCheckMessage(
                    id,
                    severity,
                    message,
                    null,
                    null,
                    null));
        }

        List<EpubCheckMessage> results = new ArrayList<>();

        for (JsonElement element : locations) {

            if (!element.isJsonObject()) continue;

            JsonObject location = element.getAsJsonObject();

            String file = normalizePath(readString(location, "path"));
            Integer line = readInteger(location, "line");
            Integer column = readInteger(location, "column");

            results.add(new EpubCheckMessage(
                    id,
                    severity,
                    message,
                    file,
                    line,
                    column));
        }

        if (results.isEmpty()) {

            return List.of(new EpubCheckMessage(
                    id,
                    severity,
                    message,
                    null,
                    null,
                    null));
        }

        return List.copyOf(results);
    }

    private JsonArray readArray(JsonObject object, String name) {

        if (object == null || name == null) return null;

        JsonElement element = object.get(name);

        if (element == null || !element.isJsonArray()) return null;

        return element.getAsJsonArray();
    }

    private String readString(JsonObject object, String name) {

        if (object == null || name == null) return null;

        JsonElement element = object.get(name);

        if (element == null || element.isJsonNull()) return null;

        if (!element.isJsonPrimitive()) return null;

        String value = element.getAsString();

        return value == null || value.isBlank() ? null : value.trim();
    }

    private Integer readInteger(JsonObject object, String name) {

        if (object == null || name == null) return null;

        JsonElement element = object.get(name);

        if (element == null || element.isJsonNull()) return null;

        try {

            return element.getAsInt();

        } catch (RuntimeException exception) {

            return null;
        }
    }

    private String normalizePath(String path) {

        if (path == null) return null;

        String normalized = path.trim().replace('\\', '/');

        return normalized.isEmpty() ? null : normalized;
    }

    private void validateEpubCheckRuntime() {

        if (!Files.exists(epubCheckDirectory)) throw new IllegalStateException("EPUBCheck directory does not exist: " + epubCheckDirectory);

        if (!Files.isDirectory(epubCheckDirectory)) throw new IllegalStateException("EPUBCheck path is not a directory: " + epubCheckDirectory);

        Path epubCheckJar = epubCheckDirectory.resolve(getEpubCheckJarName());

        if (!Files.isRegularFile(epubCheckJar)) throw new IllegalStateException("epubcheck.jar was not found: " + epubCheckJar);

        Path libraryDirectory = epubCheckDirectory.resolve(EPUBCHECK_LIB_DIRECTORY);

        if (!Files.isDirectory(libraryDirectory)) throw new IllegalStateException("EPUBCheck lib directory was not found: " + libraryDirectory);
    }

    private void validateEpubFile(Path epubFile) {

        if (epubFile == null) throw new IllegalArgumentException("epubFile must not be null.");

        Path normalized = epubFile.toAbsolutePath().normalize();

        if (!Files.exists(normalized)) throw new IllegalStateException("EPUB file does not exist: " + normalized);

        if (!Files.isRegularFile(normalized)) throw new IllegalStateException("EPUB path is not a regular file: " + normalized);

        if (!Files.isReadable(normalized)) throw new IllegalStateException("EPUB file is not readable: " + normalized);

        Path fileNamePath = normalized.getFileName();

        if (fileNamePath == null) throw new IllegalStateException("EPUB file name is not available.");

        String fileName = fileNamePath.toString().toLowerCase(Locale.ROOT);

        if (!fileName.endsWith(".epub")) throw new IllegalStateException("EPUBCheck target must use the .epub extension: " + normalized);
    }

    private String normalizeVersion(String value) {

        if (value == null || value.isBlank()) return "";

        String normalized = value.trim();

        if (normalized.toLowerCase(Locale.ROOT).startsWith("epubcheck-")) return normalized.substring("epubcheck-".length());

        return normalized;
    }

    private void deleteQuietly(Path file) {

        if (file == null) return;

        try {

            Files.deleteIfExists(file);

        } catch (IOException exception) {

            /*
             * Temporary report cleanup failure is ignored.
             */
        }
    }

    private static final class ProcessResult {

        private final int exitCode;
        private final String output;

        private ProcessResult(int exitCode, String output) {

            this.exitCode = exitCode;
            this.output = output;
        }

        private int getExitCode() {
            return exitCode;
        }

        private String getOutput() {
            return output;
        }
    }
}