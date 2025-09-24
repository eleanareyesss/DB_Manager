package dbmanager;

import java.util.*;
import java.util.regex.*;

/**
 *
 * @author elean
 */
public class SqlTranslator {

    public static boolean isDDL(String sql) {
        String s = sql.trim().toLowerCase(Locale.ROOT);
        return s.startsWith("create ") || s.startsWith("alter ") || s.startsWith("drop ")
                || s.startsWith("truncate ") || s.startsWith("rename ");
    }

    public static boolean isDML(String sql) {
        String s = sql.trim().toLowerCase(Locale.ROOT);
        return s.startsWith("insert ") || s.startsWith("update ") || s.startsWith("delete ");
    }

    public static String quoteFix(String sql) {
        return sql.replace('`', '"');
    }

    public static String mapType(String t) {
        String s = t.toLowerCase(Locale.ROOT);
        if (s.startsWith("tinyint(1)")) {
            return "boolean";
        }
        if (s.startsWith("tinyint")) {
            return "smallint";
        }
        if (s.startsWith("int")) {
            return "integer";
        }
        if (s.startsWith("bigint")) {
            return "bigint";
        }
        if (s.startsWith("double")) {
            return "double precision";
        }
        if (s.startsWith("float")) {
            return "real";
        }
        if (s.startsWith("decimal")) {
            return s;
        }
        if (s.startsWith("datetime")) {
            return "timestamp";
        }
        if (s.startsWith("timestamp")) {
            return "timestamp";
        }
        if (s.startsWith("date")) {
            return "date";
        }
        if (s.startsWith("time")) {
            return "time";
        }
        if (s.startsWith("varchar")) {
            return s;
        }
        if (s.startsWith("text")) {
            return "text";
        }
        if (s.startsWith("char")) {
            return s;
        }
        return t;
    }

    public static String translateCreateTableToPg(String createSql) {
        String sql = createSql.trim();
        sql = quoteFix(sql);
        sql = stripTailTableOptions(sql);
        sql = sql.replaceAll("(?i)\\bAUTO_INCREMENT\\b", "GENERATED ALWAYS AS IDENTITY");
        sql = sql.replaceAll("(?i)\\bUNSIGNED\\b", "");
        sql = remapColumnTypes(sql);
        sql = sql.replaceAll(",\\s*\\)", ")");
        sql = sql.replaceAll("\\s*;\\s*$", "");
        sql = sql + ";";
        sql = sql.replaceAll("[ \\t]+\\)", ")")
                .replaceAll("\\(\\s+", "(")
                .replaceAll("\\s{2,}", " ")
                .trim();
        return sql;
    }

    private static String stripTailTableOptions(String sql) {
        Pattern p = Pattern.compile(
                "(?is)(.*?\\))\\s*(ENGINE\\b|DEFAULT\\s+CHARSET\\b|CHARSET\\b|COLLATE\\b|COMMENT\\b|ROW_FORMAT\\b).*"
        );
        Matcher m = p.matcher(sql);
        if (m.matches()) {
            return m.group(1);
        }
        return sql;
    }

    private static String remapColumnTypes(String sql) {
        Matcher mm = Pattern.compile("(?is)\\((.*)\\)").matcher(sql);
        if (!mm.find()) {
            return sql;
        }
        String body = mm.group(1);
        String[] parts = body.split(",\\s*(?=[^)]*(?:\\(|$))");

        List<String> out = new ArrayList<>();
        Pattern colStart = Pattern.compile("^\\s*\"([^\"]+)\"\\s+([A-Za-z0-9_()]+)(.*)$");
        for (String part : parts) {
            String piece = part;
            Matcher m = colStart.matcher(piece);
            if (m.find()) {
                String col = m.group(1);
                String type = m.group(2);
                String rest = m.group(3);
                String newType = mapType(type);
                piece = "\"" + col + "\" " + newType + rest;
            }
            out.add(piece.trim());
        }

        String newBody = String.join(", ", out);
        return sql.replaceFirst("(?is)\\(.*\\)", "(" + newBody + ")");
    }

    public static String translateInsertToPgUpsert(String insertSql, List<String> pkCols) {
        String sql = quoteFix(insertSql);
        sql = sql.replaceAll("\\s*;\\s*$", "");
        if (pkCols == null || pkCols.isEmpty()) {
            return sql + ";";
        }
        List<String> cols = extractInsertColumns(sql);
        if (cols.isEmpty()) {
            return sql + ";";
        }
        List<String> nonPk = new ArrayList<>(cols);
        for (String pk : pkCols) {
            for (Iterator<String> it = nonPk.iterator(); it.hasNext();) {
                String c = it.next();
                if (c.equalsIgnoreCase(pk)) {
                    it.remove();
                    break;
                }
            }
        }

        if (nonPk.isEmpty()) {
            return sql + " ON CONFLICT DO NOTHING;";
        }

        String conflict = " ON CONFLICT (" + joinQuote(pkCols) + ") DO UPDATE SET ";
        List<String> sets = new ArrayList<>();
        for (String c : nonPk) {
            sets.add("\"" + c + "\"=EXCLUDED.\"" + c + "\"");
        }

        return sql + conflict + String.join(",", sets) + ";";
    }

    public static String translateCreateViewToPg(String createViewSql) {
        String sql = createViewSql.trim();
        sql = sql.replaceAll("\\s*;\\s*$", "");
        sql = sql.replaceAll("(?is)\\bALGORITHM\\s*=\\s*\\w+\\s*", "");
        sql = sql.replaceAll("(?is)\\bDEFINER\\s*=\\s*[^\\s]+\\s*", "");
        sql = sql.replaceAll("(?is)\\bSQL\\s+SECURITY\\s+\\w+\\s*", "");
        sql = quoteFix(sql);
        return sql + ";";
    }

    public static String tableNameFromInsert(String sql) {
        Matcher m = Pattern.compile("(?is)insert\\s+into\\s+`?\"?([a-zA-Z0-9_]+)`?\"?").matcher(sql);
        return m.find() ? m.group(1) : null;
    }

    private static List<String> extractInsertColumns(String sql) {
        Matcher m = Pattern.compile("(?is)insert\\s+into\\s+\"[^\"]+\"\\s*\\(([^)]+)\\)").matcher(sql);
        if (!m.find()) {
            return Collections.emptyList();
        }
        String[] parts = m.group(1).split(",");
        List<String> cols = new ArrayList<>();
        for (String p : parts) {
            cols.add(p.replace("\"", "").trim());
        }
        return cols;
    }

    private static String joinQuote(List<String> cols) {
        List<String> q = new ArrayList<>();
        for (String c : cols) {
            q.add("\"" + c + "\"");
        }
        return String.join(",", q);
    }
}
