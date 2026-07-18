package com.example.mindmap.export;

public final class CsvUtils {
    private CsvUtils() {}

    /** 转义逗号、换行和双引号，保证导出的 CSV 可被 Excel/WPS 正确读取。 */
    public static String escape(String value) {
        if (value == null) {
            return "";
        }
        boolean needsQuotes = value.contains(",") || value.contains("\n") || value.contains("\r") || value.contains("\"");
        String escaped = value.replace("\"", "\"\"");
        return needsQuotes ? "\"" + escaped + "\"" : escaped;
    }
}
