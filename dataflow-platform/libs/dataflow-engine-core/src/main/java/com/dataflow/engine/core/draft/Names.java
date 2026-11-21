package com.dataflow.engine.core.draft;

import org.apache.spark.sql.Column;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import static org.apache.spark.sql.functions.col;

final class Names {

    private static final Pattern WORDS = Pattern.compile("[^A-Za-z0-9]+");
    private static final String IDENTIFIER = "[A-Za-z_][A-Za-z0-9_]*";

    private Names() {}

    static String camel(String source) {
        String[] parts = WORDS.split(source.strip());
        StringBuilder name = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            boolean allUpper = part.equals(part.toUpperCase(Locale.ROOT));
            String word = allUpper && part.length() > 1 ? part.toLowerCase(Locale.ROOT) : part;
            name.append(name.isEmpty() ? lowerLead(word) : Character.toUpperCase(word.charAt(0)) + word.substring(1));
        }
        String result = name.isEmpty() ? "column" : name.toString();
        return Character.isLetter(result.charAt(0)) ? result : "c" + result;
    }

    static String snake(String name) {
        return name.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
    }

    static String unique(String name, Set<String> taken) {
        String candidate = name;
        for (int i = 2; !taken.add(candidate); i++) {
            candidate = name + i;
        }
        return candidate;
    }

    static boolean identifier(String name) {
        return name.matches(IDENTIFIER);
    }

    static String sql(String source) {
        return identifier(source) ? source : "`" + source.replace("`", "``") + "`";
    }

    static Column column(String source) {
        return col("`" + source.replace("`", "``") + "`");
    }

    private static String lowerLead(String word) {
        int upper = 0;
        while (upper < word.length() && Character.isUpperCase(word.charAt(upper))) {
            upper++;
        }
        int lowered = upper > 1 && upper < word.length() ? upper - 1 : Math.max(upper, 1);
        return word.substring(0, lowered).toLowerCase(Locale.ROOT) + word.substring(lowered);
    }
}
