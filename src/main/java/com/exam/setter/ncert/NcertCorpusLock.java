package com.exam.setter.ncert;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/** PostgreSQL session lock preventing two corpus workers from indexing the same document concurrently. */
public final class NcertCorpusLock implements AutoCloseable {
    private final Connection connection;

    private NcertCorpusLock(Connection connection) {
        this.connection = connection;
    }

    public static NcertCorpusLock acquire(DataSource dataSource, String documentKey) throws SQLException {
        Connection connection = dataSource.getConnection();
        try (PreparedStatement ps = connection.prepareStatement("SELECT pg_advisory_lock(hashtextextended(?, 0))")) {
            ps.setString(1, "ncert:" + documentKey);
            ps.execute();
            return new NcertCorpusLock(connection);
        } catch (SQLException ex) {
            try { connection.close(); } catch (SQLException ignored) { }
            throw ex;
        }
    }

    @Override
    public void close() throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT pg_advisory_unlock(hashtextextended(?, 0))")) {
            ps.setString(1, "ncert:" + "unused");
        } finally {
            connection.close();
        }
    }
}
