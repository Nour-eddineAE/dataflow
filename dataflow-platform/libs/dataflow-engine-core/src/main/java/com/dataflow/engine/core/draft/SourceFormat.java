package com.dataflow.engine.core.draft;

import com.dataflow.engine.core.draft.Inference.Reason;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

record SourceFormat(String format, Map<String, String> options) {

    private static final int FIRST_LINE_BYTES = 64 * 1024;
    private static final String PARQUET_MAGIC = "PAR1";
    private static final String JSON = "json";
    private static final String CSV = "csv";
    private static final String TRUE = "true";
    private static final char COMMA = ',';
    private static final char TAB = '\t';
    private static final char[] OTHER_SEPARATORS = {';', TAB, '|'};

    static SourceFormat of(SourceLocation location, Path local, List<Reason> reasons) {
        String format = location.format() != null ? location.format() : sniff(local);
        reasons.add(new Reason(
                "format: " + format,
                location.format() != null
                        ? "the file name ends like a " + format + " file"
                        : "the first bytes of the file look like " + format));
        return new SourceFormat(format, options(format, local, reasons));
    }

    private static String sniff(Path local) {
        try (InputStream in = Files.newInputStream(local)) {
            byte[] head = in.readNBytes(PARQUET_MAGIC.length());
            if (new String(head, StandardCharsets.US_ASCII).equals(PARQUET_MAGIC)) {
                return "parquet";
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        String first = firstLine(local).stripLeading();
        return first.startsWith("{") || first.startsWith("[") ? JSON : CSV;
    }

    private static Map<String, String> options(String format, Path local, List<Reason> reasons) {
        Map<String, String> options = new LinkedHashMap<>();
        if (format.equals("csv")) {
            options.put("header", TRUE);
            char sep = separator(firstLine(local));
            if (sep != COMMA) {
                options.put("sep", String.valueOf(sep));
                reasons.add(new Reason(
                        "sep: " + (sep == TAB ? "tab" : String.valueOf(sep)), "the header line splits best on it"));
            }
        }
        if (format.equals(JSON) && firstLine(local).stripLeading().startsWith("[")) {
            options.put("multiLine", TRUE);
            reasons.add(new Reason("multiLine: true", "the file is one JSON array, not one object per line"));
        }
        return options;
    }

    private static char separator(String header) {
        char sep = COMMA;
        int best = occurrences(header, COMMA);
        for (char candidate : OTHER_SEPARATORS) {
            int count = occurrences(header, candidate);
            if (count > best) {
                best = count;
                sep = candidate;
            }
        }
        return sep;
    }

    private static String firstLine(Path local) {
        try (InputStream in = Files.newInputStream(local)) {
            String text = new String(in.readNBytes(FIRST_LINE_BYTES), StandardCharsets.UTF_8);
            int end = text.indexOf('\n');
            return end < 0 ? text : text.substring(0, end);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static int occurrences(String text, char wanted) {
        return (int) text.chars().filter(c -> c == wanted).count();
    }

    boolean textual() {
        return format.equals(CSV) || format.equals(JSON);
    }

    Map<String, String> reading() {
        Map<String, String> reading = new LinkedHashMap<>(options);
        if (format.equals("csv")) {
            reading.put("inferSchema", TRUE);
        }
        return reading;
    }
}
