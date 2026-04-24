package io.github.evoschema.processor.dbscanner;

import io.github.evoschema.processor.exception.EvoSchemaException;
import io.github.evoschema.processor.exception.EvoSchemaException.ProcesssError;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;

public final class SqlStatementGuard
{
    private static final Set<String> DML_KEYWORDS = toUpperSet("INSERT", "UPDATE", "DELETE");
    private static final Set<String> SCRIPT_DML_KEYWORDS = toUpperSet("INSERT", "UPDATE", "DELETE", "REPLACE", "MERGE", "CALL");
    private static final Set<String> QUERY_KEYWORDS = toUpperSet("SELECT", "SHOW", "EXPLAIN", "DESCRIBE", "DESC");
    private static final Set<String> DML_PLUS_QUERY_KEYWORDS = combineSets(SCRIPT_DML_KEYWORDS, QUERY_KEYWORDS);
    private static final Set<String> DDL_KEYWORDS = toUpperSet("CREATE", "ALTER", "DROP", "TRUNCATE", "RENAME", "GRANT",
            "REVOKE", "COMMENT");

    private SqlStatementGuard()
    {
    }

    public static void validateDmlOnly(String context, String sql)
    {
        validateDmlOnly(context, sql, ProcesssError.DML_SCRIPT_ERROR);
    }

    public static void validateDmlPlusQuery(String context, String sql)
    {
        validateDmlPlusQuery(context, sql, ProcesssError.DML_SCRIPT_ERROR);
    }

    public static void validateQueryOnly(String context, String sql)
    {
        validateQueryOnly(context, sql, ProcesssError.DML_SCRIPT_ERROR);
    }

    public static void validateDmlOnly(String context, String sql, ProcesssError error)
    {
        validateStatements(context, sql, DML_KEYWORDS, error);
    }

    public static void validateDmlPlusQuery(String context, String sql, ProcesssError error)
    {
        validateStatements(context, sql, DML_PLUS_QUERY_KEYWORDS, error);
    }

    public static void validateQueryOnly(String context, String sql, ProcesssError error)
    {
        validateStatements(context, sql, QUERY_KEYWORDS, error);
    }

    private static void validateStatements(String context, String sql, Set<String> allowedFirstKeywords, ProcesssError error)
    {
        if (StringUtils.isBlank(sql)) {
            throw new EvoSchemaException(error, "sql is blank: " + context);
        }
        for (String statement : splitSqlStatements(sql)) {
            if (StringUtils.isBlank(statement)) {
                continue;
            }
            List<String> tokens = extractTokens(statement);
            if (tokens.isEmpty()) {
                continue;
            }
            String first = tokens.get(0);
            int startIndex = 1;
            if ("WITH".equals(first)) {
                first = findFirstAllowedKeyword(tokens, allowedFirstKeywords);
                startIndex = 0;
            }
            if (first == null || !allowedFirstKeywords.contains(first)) {
                throwInvalidSql(context, "sql contains non-allowed statement", statement, error);
            }
            for (int i = startIndex; i < tokens.size(); i++) {
                String token = tokens.get(i);
                if (DDL_KEYWORDS.contains(token)) {
                    throwInvalidSql(context, "sql contains ddl keyword " + token, statement, error);
                }
            }
        }
    }

    private static void throwInvalidSql(String context, String message, String statement, ProcesssError error)
    {
        throw new EvoSchemaException(
                error,
                message + ": " + context + ": " + normalizeSqlSnippet(statement)
        );
    }

    private static String findFirstAllowedKeyword(List<String> tokens, Set<String> allowedFirstKeywords)
    {
        for (String token : tokens) {
            if (allowedFirstKeywords.contains(token)) {
                return token;
            }
        }
        return null;
    }

    private static String normalizeSqlSnippet(String sql)
    {
        String normalized = sql.replaceAll("\\s+", " ").trim();
        if (normalized.length() > 240) {
            return normalized.substring(0, 240);
        }
        return normalized;
    }

    private static Set<String> toUpperSet(String... values)
    {
        Set<String> set = new HashSet<>();
        for (String value : values) {
            set.add(value.toUpperCase());
        }
        return set;
    }

    private static Set<String> combineSets(Set<String> first, Set<String> second)
    {
        Set<String> combined = new HashSet<>(first);
        combined.addAll(second);
        return combined;
    }

    private static List<String> splitSqlStatements(String sql)
    {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingle = false;
        boolean inDouble = false;
        boolean inBacktick = false;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (!inDouble && !inBacktick && c == '\'') {
                if (inSingle && i + 1 < sql.length() && sql.charAt(i + 1) == '\'') {
                    current.append(c).append(c);
                    i++;
                    continue;
                }
                inSingle = !inSingle;
                current.append(c);
                continue;
            }
            if (!inSingle && !inBacktick && c == '"') {
                inDouble = !inDouble;
                current.append(c);
                continue;
            }
            if (!inSingle && !inDouble && c == '`') {
                inBacktick = !inBacktick;
                current.append(c);
                continue;
            }
            if (!inSingle && !inDouble && !inBacktick && c == ';') {
                statements.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        if (current.length() > 0) {
            statements.add(current.toString());
        }
        return statements;
    }

    private static List<String> extractTokens(String sql)
    {
        List<String> tokens = new ArrayList<>();
        StringBuilder token = new StringBuilder();
        boolean inSingle = false;
        boolean inDouble = false;
        boolean inBacktick = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            char next = i + 1 < sql.length() ? sql.charAt(i + 1) : '\0';

            if (inLineComment) {
                if (c == '\n' || c == '\r') {
                    inLineComment = false;
                }
                continue;
            }
            if (inBlockComment) {
                if (c == '*' && next == '/') {
                    inBlockComment = false;
                    i++;
                }
                continue;
            }

            if (!inSingle && !inDouble && !inBacktick) {
                if (c == '-' && next == '-') {
                    inLineComment = true;
                    flushToken(token, tokens);
                    i++;
                    continue;
                }
                if (c == '#') {
                    inLineComment = true;
                    flushToken(token, tokens);
                    continue;
                }
                if (c == '/' && next == '*') {
                    inBlockComment = true;
                    flushToken(token, tokens);
                    i++;
                    continue;
                }
            }

            if (!inDouble && !inBacktick && c == '\'') {
                if (inSingle && next == '\'') {
                    i++;
                    continue;
                }
                inSingle = !inSingle;
                flushToken(token, tokens);
                continue;
            }
            if (!inSingle && !inBacktick && c == '"') {
                inDouble = !inDouble;
                flushToken(token, tokens);
                continue;
            }
            if (!inSingle && !inDouble && c == '`') {
                inBacktick = !inBacktick;
                flushToken(token, tokens);
                continue;
            }

            if (inSingle || inDouble || inBacktick) {
                continue;
            }

            if (Character.isLetter(c) || c == '_') {
                token.append(c);
            } else {
                flushToken(token, tokens);
            }
        }
        flushToken(token, tokens);
        return tokens;
    }

    private static void flushToken(StringBuilder token, List<String> tokens)
    {
        if (token.length() == 0) {
            return;
        }
        tokens.add(token.toString().toUpperCase());
        token.setLength(0);
    }
}
