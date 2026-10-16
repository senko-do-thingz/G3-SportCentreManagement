package com.sportify.core.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.support.SQLStateSQLExceptionTranslator;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/** Deterministic JDBC protocol regressions; real T-SQL execution remains in the CI suite. */
class SqlServerScriptReplayTest {
    private static final String SCRIPT = "UPDATE fixture SET value = 1; THROW 51072, 'BE-07 conflict', 1;";
    private DataSource dataSource;
    private Connection connection;
    private Statement statement;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = mock(DataSource.class);
        connection = mock(Connection.class);
        statement = mock(Statement.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        jdbc = new JdbcTemplate(dataSource);
        jdbc.setExceptionTranslator(new SQLStateSQLExceptionTranslator());
    }

    @Test
    void legacyStringExecuteReturnsWithoutObservingALaterError() throws Exception {
        when(statement.execute(SCRIPT)).thenReturn(false);
        when(statement.getMoreResults(anyInt())).thenThrow(error(51072));

        assertThatCode(() -> jdbc.execute(SCRIPT)).doesNotThrowAnyException();

        verify(statement).execute(SCRIPT);
        verify(statement, never()).getMoreResults(anyInt());
        verify(statement, never()).getUpdateCount();
        verify(statement).close();
    }

    @ParameterizedTest
    @ValueSource(ints = {51071, 51072, 51073})
    void errorsAfterIntermediateUpdateCountsAreTranslatedAndPropagated(int code) throws Exception {
        SQLException failure = error(code);
        when(statement.execute(SCRIPT)).thenReturn(false);
        when(statement.getUpdateCount()).thenReturn(7, 0);
        when(statement.getMoreResults(Statement.CLOSE_CURRENT_RESULT)).thenReturn(false).thenThrow(failure);

        assertThatThrownBy(() -> SqlServerScriptReplay.execute(jdbc, SCRIPT))
                .isInstanceOf(DataAccessException.class).hasCause(failure);

        verify(statement, times(2)).getMoreResults(Statement.CLOSE_CURRENT_RESULT);
        verify(statement).close();
        verify(connection).close();
        verify(connection, never()).commit();
    }

    @Test
    void mixedResultsAndZeroUpdateCountsAreConsumedUntilTheFinalSentinel() throws Exception {
        var rows = mock(ResultSet.class);
        when(statement.execute(SCRIPT)).thenReturn(false);
        when(statement.getUpdateCount()).thenReturn(7, 0, 3, -1);
        when(statement.getMoreResults(Statement.CLOSE_CURRENT_RESULT)).thenReturn(false, true, false, false);
        when(statement.getResultSet()).thenReturn(rows);
        when(rows.next()).thenReturn(true, true, false);

        SqlServerScriptReplay.execute(jdbc, SCRIPT);

        verify(statement).execute(SCRIPT);
        verify(statement, times(4)).getUpdateCount();
        verify(statement, times(4)).getMoreResults(Statement.CLOSE_CURRENT_RESULT);
        verify(rows, times(3)).next();
        verify(rows).close();
        verify(statement).close();
        verify(connection).close();
    }

    @Test
    void noResultsStopsWithoutAdvancingPastTheEnd() throws Exception {
        when(statement.execute(SCRIPT)).thenReturn(false);
        when(statement.getUpdateCount()).thenReturn(-1);

        SqlServerScriptReplay.execute(jdbc, SCRIPT);

        verify(statement, never()).getMoreResults(anyInt());
        verify(statement).close();
    }

    @Test
    void errorsAfterAResultSetAreNotLostWhenTheRowsAreClosed() throws Exception {
        var rows = mock(ResultSet.class);
        SQLException failure = error(51072);
        when(statement.execute(SCRIPT)).thenReturn(true);
        when(statement.getResultSet()).thenReturn(rows);
        when(rows.next()).thenReturn(true, false);
        when(statement.getMoreResults(Statement.CLOSE_CURRENT_RESULT)).thenThrow(failure);

        assertThatThrownBy(() -> SqlServerScriptReplay.execute(jdbc, SCRIPT))
                .isInstanceOf(DataAccessException.class).hasCause(failure);

        verify(rows).close();
        verify(statement).close();
    }

    @Test
    void errorsWhileStreamingRowsPropagateAndReleaseResources() throws Exception {
        var rows = mock(ResultSet.class);
        SQLException failure = error(51072);
        when(statement.execute(SCRIPT)).thenReturn(true);
        when(statement.getResultSet()).thenReturn(rows);
        when(rows.next()).thenReturn(true).thenThrow(failure);

        assertThatThrownBy(() -> SqlServerScriptReplay.execute(jdbc, SCRIPT))
                .isInstanceOf(DataAccessException.class).hasCause(failure);

        verify(rows).close();
        verify(statement).close();
        verify(connection).close();
    }

    @Test
    void immediateExecuteErrorsStillPropagate() throws Exception {
        SQLException failure = error(51072);
        when(statement.execute(SCRIPT)).thenThrow(failure);

        assertThatThrownBy(() -> SqlServerScriptReplay.execute(jdbc, SCRIPT))
                .isInstanceOf(DataAccessException.class).hasCause(failure);

        verify(statement, never()).getMoreResults(anyInt());
        verify(statement).close();
    }

    @Test
    void errorsReadingTheUpdateCountStillPropagate() throws Exception {
        SQLException failure = error(51072);
        when(statement.execute(SCRIPT)).thenReturn(false);
        when(statement.getUpdateCount()).thenThrow(failure);

        assertThatThrownBy(() -> SqlServerScriptReplay.execute(jdbc, SCRIPT))
                .isInstanceOf(DataAccessException.class).hasCause(failure);

        verify(statement).close();
    }

    @Test
    void lateErrorEscapesTheRollbackOnlyCallbackAndRollsBackTheSameConnection() throws Exception {
        SQLException failure = error(51072);
        when(connection.getAutoCommit()).thenReturn(true);
        when(statement.execute(SCRIPT)).thenReturn(false);
        when(statement.getUpdateCount()).thenReturn(7);
        when(statement.getMoreResults(Statement.CLOSE_CURRENT_RESULT)).thenThrow(failure);
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            status.setRollbackOnly();
            SqlServerScriptReplay.execute(jdbc, SCRIPT);
        })).isInstanceOf(DataAccessException.class).hasCause(failure);

        verify(dataSource).getConnection();
        verify(connection).rollback();
        verify(connection, never()).commit();
        verify(connection).close();
    }

    private SQLException error(int code) {
        return new SQLException("BE-07 expected validation failure", "S0001", code);
    }
}
