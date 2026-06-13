import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class JdbcSqlRunner {

    public static void main(String[] args) throws Exception {
        if (args.length != 5) {
            throw new IllegalArgumentException(
                    "Usage: JdbcSqlRunner <jdbc-url> <db-user> <db-password> <load-password> <sql-file>"
            );
        }
        String script = Files.readString(Path.of(args[4]), StandardCharsets.UTF_8)
                .lines()
                .filter(line -> !line.stripLeading().startsWith("\\"))
                .collect(Collectors.joining(System.lineSeparator()))
                .replace(":'load_password'", quote(args[3]));

        List<String> statements = splitStatements(script);
        Instant startedAt = Instant.now();
        try (Connection connection = DriverManager.getConnection(args[0], args[1], args[2])) {
            for (int index = 0; index < statements.size(); index++) {
                String sql = statements.get(index);
                Instant statementStartedAt = Instant.now();
                System.out.printf(
                        "[%d/%d] %s%n",
                        index + 1,
                        statements.size(),
                        preview(sql)
                );
                try (Statement statement = connection.createStatement()) {
                    boolean hasResult = statement.execute(sql);
                    if (hasResult) {
                        printResult(statement.getResultSet());
                    } else {
                        System.out.printf(
                                "  affected rows: %d, elapsed: %s%n",
                                statement.getUpdateCount(),
                                elapsed(statementStartedAt)
                        );
                    }
                }
            }
        }
        System.out.println("Completed in " + elapsed(startedAt));
    }

    private static List<String> splitStatements(String script) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inString = false;
        for (int index = 0; index < script.length(); index++) {
            char character = script.charAt(index);
            if (character == '\'') {
                current.append(character);
                if (inString && index + 1 < script.length() && script.charAt(index + 1) == '\'') {
                    current.append(script.charAt(++index));
                } else {
                    inString = !inString;
                }
            } else if (character == ';' && !inString) {
                addStatement(statements, current);
            } else {
                current.append(character);
            }
        }
        addStatement(statements, current);
        return statements;
    }

    private static void addStatement(List<String> statements, StringBuilder current) {
        String sql = current.toString().trim();
        if (!sql.isEmpty()) {
            statements.add(sql);
        }
        current.setLength(0);
    }

    private static void printResult(ResultSet resultSet) throws Exception {
        ResultSetMetaData metadata = resultSet.getMetaData();
        int columns = metadata.getColumnCount();
        while (resultSet.next()) {
            StringBuilder row = new StringBuilder("  ");
            for (int column = 1; column <= columns; column++) {
                if (column > 1) {
                    row.append(" | ");
                }
                row.append(metadata.getColumnLabel(column))
                        .append('=')
                        .append(resultSet.getString(column));
            }
            System.out.println(row);
        }
    }

    private static String quote(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    private static String preview(String sql) {
        String normalized = sql.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 100 ? normalized : normalized.substring(0, 100) + "...";
    }

    private static String elapsed(Instant startedAt) {
        Duration duration = Duration.between(startedAt, Instant.now());
        return duration.toMinutes() + "m " + duration.toSecondsPart() + "s";
    }
}
