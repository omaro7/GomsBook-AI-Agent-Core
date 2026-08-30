package kr.co.goms.gomsbook.ai.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import kr.co.goms.gomsbook.ai.tool.ToolContext;
import kr.co.goms.gomsbook.ai.tool.xhtml.ApplyXhtmlTool.ApplyXhtmlRequest;


public final class ToolUtil {

    private static final String DEFAULT_TEXT_DIRECTORY = "Text";
    private static final String XHTML_EXTENSION = ".xhtml";
    private static final String BACKUP_EXTENSION = ".bak";
    
	/**
	 * AS-IS : deepCopyValue(value)
	 * TO-BE : ToolUtil.deepCopy(value)
	 */
    public static Object deepCopy(Object value) {

        if (value == null) return null;

        if (value instanceof Path path) return path.normalize();

        if (value instanceof Map<?, ?> map) return copyMap(map);

        if (value instanceof Iterable<?> iterable) return copyIterable(iterable);

        return value;
    }

    private static Map<String, Object> copyMap(Map<?, ?> source) {

        Map<String, Object> copied = new LinkedHashMap<>();

        for (Map.Entry<?, ?> entry : source.entrySet()) {

            if (entry.getKey() == null) throw new IllegalArgumentException("Tool value Map must not contain null keys");

            copied.put(String.valueOf(entry.getKey()), deepCopy(entry.getValue()));
        }

        return Collections.unmodifiableMap(copied);
    }

    private static List<Object> copyIterable(Iterable<?> source) {

        List<Object> copied = new ArrayList<>();

        for (Object item : source) copied.add(deepCopy(item));

        return Collections.unmodifiableList(copied);
    }
    
    public static Path validateProjectRoot(Path value) {

        Path projectRoot = value.toAbsolutePath().normalize();

        if (!Files.exists(projectRoot)) throw new IllegalArgumentException("프로젝트 루트가 존재하지 않습니다: " + projectRoot);
        if (!Files.isDirectory(projectRoot)) throw new IllegalArgumentException("프로젝트 루트가 디렉터리가 아닙니다: " + projectRoot);

        return projectRoot;
    }
    
    public static boolean hasProjectRoot(ApplyXhtmlRequest request, ToolContext toolContext) {

        if (!isBlank(request.getProjectRoot())) return true;
        if (toolContext != null && toolContext.getProjectRoot() != null) return true;
        if (!isBlank(getContextString(toolContext, "projectRoot"))) return true;

        return !isBlank(getContextString(toolContext, "projectPath"));
    }
    
    public static String getContextString( ToolContext context, String key) {

        if (context == null || key == null || key.isBlank()) {

            return null;
        }

        String value =
                context.getAttribute(
                        key,
                        String.class);

        return trimToNull(value);
    }

    public static String resolveRequestedFileName(ApplyXhtmlRequest request) {

        if (!isBlank(request.getRelativePath())) {
            Path relativePath = Path.of(request.getRelativePath());

            Path fileName = relativePath.getFileName();

            return fileName == null
                    ? ""
                    : fileName.toString();
        }

        return defaultIfBlank( request.getFileName(), "");
    }

    public static boolean hasXhtmlExtension(String fileName) {

        return fileName != null
                && fileName.toLowerCase(Locale.ROOT)
                        .endsWith(XHTML_EXTENSION);
    }

    public static String normalizeSeparator(String path) {

        return path.replace('\\', '/');
    }

    public static int lastIndexOfIgnoreCase( String source, String target) {

        if (source == null || target == null) {
            return -1;
        }

        return source.toLowerCase(Locale.ROOT)
                .lastIndexOf(
                        target.toLowerCase(Locale.ROOT));
    }
    
    
    public static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static String trimToNull(String value) {

        if (isBlank(value)) {
            return null;
        }

        return value.trim();
    }

    public static String defaultIfBlank(
            String value,
            String defaultValue) {

        return isBlank(value)
                ? defaultValue
                : value.trim();
    }
    
    public static boolean containsHtmlElement(String xhtml) {

        return containsIgnoreCase(
                xhtml,
                "<html");
    }
    
    public static boolean containsIgnoreCase(
            String source,
            String target) {

        return indexOfIgnoreCase(
                source,
                target) >= 0;
    }

    public static int indexOfIgnoreCase(
            String source,
            String target) {

        if (source == null || target == null) {
            return -1;
        }

        return source.toLowerCase(Locale.ROOT)
                .indexOf(
                        target.toLowerCase(Locale.ROOT));
    }
    
    private ToolUtil() {}
}