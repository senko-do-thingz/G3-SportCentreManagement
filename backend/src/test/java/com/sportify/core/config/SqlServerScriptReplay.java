package com.sportify.core.config;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.StatementCallback;

import java.sql.Statement;

/** Executes a whole T-SQL batch and observes errors after intermediate JDBC results. */
final class SqlServerScriptReplay {
    static void execute(JdbcTemplate jdbc, String sql) {
        jdbc.execute((StatementCallback<Void>) statement -> {
            boolean resultSet = statement.execute(sql);
            while (true) {
                if (resultSet) {
                    try (var rows = statement.getResultSet()) {
                        while (rows.next()) {
                            // Consume rows so streaming errors are observed before releasing the statement.
                        }
                    }
                } else if (statement.getUpdateCount() == -1) {
                    break;
                }
                // false can mean another update count (including zero), not the end of the batch.
                resultSet = statement.getMoreResults(Statement.CLOSE_CURRENT_RESULT);
            }
            return null;
        });
    }

    private SqlServerScriptReplay() {
    }
}
