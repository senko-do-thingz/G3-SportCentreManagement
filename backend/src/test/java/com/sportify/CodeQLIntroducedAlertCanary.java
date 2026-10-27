package com.sportify;

import jakarta.servlet.http.HttpServletRequest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/** Non-executed security detection control. This draft branch must never be merged. */
public final class CodeQLIntroducedAlertCanary {
    public ResultSet query(HttpServletRequest request, Statement statement) throws SQLException {
        return statement.executeQuery("SELECT name FROM sys.databases WHERE name = '"
                + request.getParameter("name") + "'");
    }
}
