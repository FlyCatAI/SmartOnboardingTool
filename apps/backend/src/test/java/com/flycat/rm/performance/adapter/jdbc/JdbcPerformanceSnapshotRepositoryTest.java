package com.flycat.rm.performance.adapter.jdbc;

import com.flycat.rm.performance.domain.PeriodType;
import com.flycat.rm.performance.spi.PerformanceSnapshot;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Adapter tests for {@link JdbcPerformanceSnapshotRepository}.
 *
 * <p>Uses a hand-rolled in-memory JDBC test double so the test stays free of
 * external DB drivers (the build tool is still TBD — see {@code
 * BUILD_TOOL_TBD.md}). The double records the prepared SQL, captures bound
 * parameters, and returns a fixed result set so we can assert both the SQL
 * shape and the row-mapper output.
 */
class JdbcPerformanceSnapshotRepositoryTest {

    private static final ZoneOffset SH = ZoneOffset.ofHours(8);

    @Test
    void returns_empty_when_no_row() {
        FakeDataSource ds = new FakeDataSource();
        ds.script = (sql, params) -> StubResultSet.empty();
        JdbcPerformanceSnapshotRepository repo = new JdbcPerformanceSnapshotRepository(ds);

        Optional<PerformanceSnapshot> got = repo.findLatest("RM-X", PeriodType.CURRENT_YEAR);

        assertTrue(got.isEmpty());
    }

    @Test
    void selects_from_serving_view_with_employee_and_period_params() {
        FakeDataSource ds = new FakeDataSource();
        ds.script = (sql, params) -> StubResultSet.empty();
        JdbcPerformanceSnapshotRepository repo = new JdbcPerformanceSnapshotRepository(ds);

        repo.findLatest("RM-A", PeriodType.ALL_TIME);

        assertEquals(1, ds.queries.size(), "should issue exactly one SQL query");
        String sql = ds.queries.get(0).sql.toLowerCase();
        assertTrue(sql.contains("rm_latest_perf_summary_snapshot"),
                "must read the latest serving view, not the base table; got: " + sql);
        assertTrue(sql.contains("employee_id = ?"), "must bind employee_id parameter; got: " + sql);
        assertTrue(sql.contains("period_type = ?"), "must bind period_type parameter; got: " + sql);
        assertEquals("RM-A", ds.queries.get(0).params.get(1));
        assertEquals("all_time", ds.queries.get(0).params.get(2));
    }

    @Test
    void maps_row_into_performance_snapshot() {
        FakeDataSource ds = new FakeDataSource();
        StubResultSet rs = new StubResultSet();
        rs.row("employee_id", "RM-A");
        rs.row("period_type", "current_year");
        rs.row("biz_date", java.sql.Date.valueOf(LocalDate.of(2026, 5, 19)));
        rs.row("new_merchants", 12L);
        rs.row("qualified_merchants", 8L);
        rs.row("active_merchants", 6L);
        rs.row("income_amount", new BigDecimal("1234567.89"));
        rs.row("batch_finished_at",
                Timestamp.from(OffsetDateTime.of(2026, 5, 20, 2, 30, 0, 0, SH).toInstant()));
        rs.row("is_delayed", false);
        ds.script = (sql, params) -> rs;
        JdbcPerformanceSnapshotRepository repo = new JdbcPerformanceSnapshotRepository(ds);

        Optional<PerformanceSnapshot> got = repo.findLatest("RM-A", PeriodType.CURRENT_YEAR);

        assertTrue(got.isPresent());
        PerformanceSnapshot s = got.get();
        assertEquals("RM-A", s.employeeId());
        assertEquals(PeriodType.CURRENT_YEAR, s.periodType());
        assertEquals(LocalDate.of(2026, 5, 19), s.bizDate());
        assertEquals(12L, s.newMerchants());
        assertEquals(8L, s.qualifiedMerchants());
        assertEquals(6L, s.activeMerchants());
        assertEquals(new BigDecimal("1234567.89"), s.income());
        assertFalse(s.isDelayed());
        // batch_finished_at must round-trip with offset preserved as +08:00 wall clock.
        assertEquals(0, s.batchFinishedAt().toInstant()
                .compareTo(OffsetDateTime.of(2026, 5, 20, 2, 30, 0, 0, SH).toInstant()));
    }

    @Test
    void delayed_flag_passes_through() {
        FakeDataSource ds = new FakeDataSource();
        StubResultSet rs = new StubResultSet();
        rs.row("employee_id", "RM-A");
        rs.row("period_type", "all_time");
        rs.row("biz_date", java.sql.Date.valueOf(LocalDate.of(2026, 5, 18)));
        rs.row("new_merchants", 0L);
        rs.row("qualified_merchants", 0L);
        rs.row("active_merchants", 0L);
        rs.row("income_amount", new BigDecimal("0.00"));
        rs.row("batch_finished_at",
                Timestamp.from(OffsetDateTime.of(2026, 5, 20, 3, 0, 0, 0, SH).toInstant()));
        rs.row("is_delayed", true);
        ds.script = (sql, params) -> rs;
        JdbcPerformanceSnapshotRepository repo = new JdbcPerformanceSnapshotRepository(ds);

        PerformanceSnapshot s = repo.findLatest("RM-A", PeriodType.ALL_TIME).orElseThrow();

        assertTrue(s.isDelayed());
    }

    @Test
    void rejects_unknown_period_type_value_from_db() {
        FakeDataSource ds = new FakeDataSource();
        StubResultSet rs = new StubResultSet();
        rs.row("employee_id", "RM-A");
        rs.row("period_type", "monthly");
        rs.row("biz_date", java.sql.Date.valueOf(LocalDate.of(2026, 5, 19)));
        rs.row("new_merchants", 0L);
        rs.row("qualified_merchants", 0L);
        rs.row("active_merchants", 0L);
        rs.row("income_amount", new BigDecimal("0.00"));
        rs.row("batch_finished_at",
                Timestamp.from(OffsetDateTime.of(2026, 5, 20, 2, 30, 0, 0, SH).toInstant()));
        rs.row("is_delayed", false);
        ds.script = (sql, params) -> rs;
        JdbcPerformanceSnapshotRepository repo = new JdbcPerformanceSnapshotRepository(ds);

        // Per DATA_SOURCE_MAPPING.md §4: adapter rejects unexpected values rather than
        // building a half-formed snapshot.
        assertThrows(IllegalStateException.class,
                () -> repo.findLatest("RM-A", PeriodType.CURRENT_YEAR));
    }

    @Test
    void wraps_sql_exception_as_unchecked() {
        FakeDataSource ds = new FakeDataSource();
        ds.script = (sql, params) -> {
            throw new SQLException("connection reset");
        };
        JdbcPerformanceSnapshotRepository repo = new JdbcPerformanceSnapshotRepository(ds);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> repo.findLatest("RM-A", PeriodType.CURRENT_YEAR));
        assertTrue(ex.getCause() instanceof SQLException, "should wrap SQLException as cause");
    }

    /* ============================== Test doubles ============================== */

    interface SqlScript {
        StubResultSet run(String sql, Map<Integer, Object> params) throws SQLException;
    }

    static final class FakeDataSource implements DataSource {
        SqlScript script = (sql, params) -> StubResultSet.empty();
        final List<CapturedQuery> queries = new ArrayList<>();

        @Override
        public Connection getConnection() {
            return new FakeConnection(this);
        }

        @Override public Connection getConnection(String u, String p) { return getConnection(); }
        @Override public java.io.PrintWriter getLogWriter() { return null; }
        @Override public void setLogWriter(java.io.PrintWriter out) {}
        @Override public void setLoginTimeout(int seconds) {}
        @Override public int getLoginTimeout() { return 0; }
        @Override public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
        }
        @Override public <T> T unwrap(Class<T> iface) { return null; }
        @Override public boolean isWrapperFor(Class<?> iface) { return false; }
    }

    static final class CapturedQuery {
        final String sql;
        final Map<Integer, Object> params;
        CapturedQuery(String sql, Map<Integer, Object> params) {
            this.sql = sql;
            this.params = params;
        }
    }

    static final class FakeConnection implements Connection {
        final FakeDataSource ds;
        FakeConnection(FakeDataSource ds) { this.ds = ds; }

        @Override
        public PreparedStatement prepareStatement(String sql) {
            return new FakePreparedStatement(ds, sql);
        }

        // ---- unused JDBC surface ----
        @Override public java.sql.Statement createStatement() { return null; }
        @Override public java.sql.CallableStatement prepareCall(String sql) { return null; }
        @Override public String nativeSQL(String sql) { return sql; }
        @Override public void setAutoCommit(boolean autoCommit) {}
        @Override public boolean getAutoCommit() { return true; }
        @Override public void commit() {}
        @Override public void rollback() {}
        @Override public void close() {}
        @Override public boolean isClosed() { return false; }
        @Override public java.sql.DatabaseMetaData getMetaData() { return null; }
        @Override public void setReadOnly(boolean readOnly) {}
        @Override public boolean isReadOnly() { return false; }
        @Override public void setCatalog(String catalog) {}
        @Override public String getCatalog() { return null; }
        @Override public void setTransactionIsolation(int level) {}
        @Override public int getTransactionIsolation() { return 0; }
        @Override public java.sql.SQLWarning getWarnings() { return null; }
        @Override public void clearWarnings() {}
        @Override public java.sql.Statement createStatement(int rt, int rc) { return null; }
        @Override public PreparedStatement prepareStatement(String s, int rt, int rc) { return prepareStatement(s); }
        @Override public java.sql.CallableStatement prepareCall(String s, int rt, int rc) { return null; }
        @Override public Map<String, Class<?>> getTypeMap() { return null; }
        @Override public void setTypeMap(Map<String, Class<?>> map) {}
        @Override public void setHoldability(int holdability) {}
        @Override public int getHoldability() { return 0; }
        @Override public java.sql.Savepoint setSavepoint() { return null; }
        @Override public java.sql.Savepoint setSavepoint(String name) { return null; }
        @Override public void rollback(java.sql.Savepoint savepoint) {}
        @Override public void releaseSavepoint(java.sql.Savepoint savepoint) {}
        @Override public java.sql.Statement createStatement(int rt, int rc, int rh) { return null; }
        @Override public PreparedStatement prepareStatement(String s, int rt, int rc, int rh) { return prepareStatement(s); }
        @Override public java.sql.CallableStatement prepareCall(String s, int rt, int rc, int rh) { return null; }
        @Override public PreparedStatement prepareStatement(String s, int[] columnIndexes) { return prepareStatement(s); }
        @Override public PreparedStatement prepareStatement(String s, String[] columnNames) { return prepareStatement(s); }
        @Override public PreparedStatement prepareStatement(String s, int autoGenerated) { return prepareStatement(s); }
        @Override public java.sql.Clob createClob() { return null; }
        @Override public java.sql.Blob createBlob() { return null; }
        @Override public java.sql.NClob createNClob() { return null; }
        @Override public java.sql.SQLXML createSQLXML() { return null; }
        @Override public boolean isValid(int timeout) { return true; }
        @Override public void setClientInfo(String name, String value) {}
        @Override public void setClientInfo(java.util.Properties properties) {}
        @Override public String getClientInfo(String name) { return null; }
        @Override public java.util.Properties getClientInfo() { return null; }
        @Override public java.sql.Array createArrayOf(String typeName, Object[] elements) { return null; }
        @Override public java.sql.Struct createStruct(String typeName, Object[] attributes) { return null; }
        @Override public void setSchema(String schema) {}
        @Override public String getSchema() { return null; }
        @Override public void abort(java.util.concurrent.Executor executor) {}
        @Override public void setNetworkTimeout(java.util.concurrent.Executor e, int ms) {}
        @Override public int getNetworkTimeout() { return 0; }
        @Override public <T> T unwrap(Class<T> iface) { return null; }
        @Override public boolean isWrapperFor(Class<?> iface) { return false; }
    }

    static final class FakePreparedStatement implements PreparedStatement {
        final FakeDataSource ds;
        final String sql;
        final Map<Integer, Object> params = new HashMap<>();

        FakePreparedStatement(FakeDataSource ds, String sql) {
            this.ds = ds;
            this.sql = sql;
        }

        @Override
        public ResultSet executeQuery() throws SQLException {
            ds.queries.add(new CapturedQuery(sql, new HashMap<>(params)));
            return ds.script.run(sql, params);
        }

        @Override public void setString(int p, String x) { params.put(p, x); }
        @Override public void setObject(int p, Object x) { params.put(p, x); }
        @Override public void setObject(int p, Object x, int targetSqlType) { params.put(p, x); }
        @Override public void setInt(int p, int x) { params.put(p, x); }
        @Override public void setLong(int p, long x) { params.put(p, x); }
        @Override public void setBoolean(int p, boolean x) { params.put(p, x); }

        // ---- unused PreparedStatement surface ----
        @Override public void close() {}
        @Override public int executeUpdate() { return 0; }
        @Override public void setNull(int parameterIndex, int sqlType) { params.put(parameterIndex, null); }
        @Override public void setByte(int p, byte x) { params.put(p, x); }
        @Override public void setShort(int p, short x) { params.put(p, x); }
        @Override public void setFloat(int p, float x) { params.put(p, x); }
        @Override public void setDouble(int p, double x) { params.put(p, x); }
        @Override public void setBigDecimal(int p, BigDecimal x) { params.put(p, x); }
        @Override public void setBytes(int p, byte[] x) { params.put(p, x); }
        @Override public void setDate(int p, java.sql.Date x) { params.put(p, x); }
        @Override public void setTime(int p, java.sql.Time x) { params.put(p, x); }
        @Override public void setTimestamp(int p, java.sql.Timestamp x) { params.put(p, x); }
        @Override public void setAsciiStream(int p, java.io.InputStream x, int l) {}
        @Override public void setUnicodeStream(int p, java.io.InputStream x, int l) {}
        @Override public void setBinaryStream(int p, java.io.InputStream x, int l) {}
        @Override public void clearParameters() { params.clear(); }
        @Override public void setObject(int p, Object x, int t, int sc) { params.put(p, x); }
        @Override public boolean execute() { return false; }
        @Override public void addBatch() {}
        @Override public void setCharacterStream(int p, java.io.Reader r, int l) {}
        @Override public void setRef(int p, java.sql.Ref x) {}
        @Override public void setBlob(int p, java.sql.Blob x) {}
        @Override public void setClob(int p, java.sql.Clob x) {}
        @Override public void setArray(int p, java.sql.Array x) {}
        @Override public java.sql.ResultSetMetaData getMetaData() { return null; }
        @Override public void setDate(int p, java.sql.Date x, java.util.Calendar c) {}
        @Override public void setTime(int p, java.sql.Time x, java.util.Calendar c) {}
        @Override public void setTimestamp(int p, java.sql.Timestamp x, java.util.Calendar c) {}
        @Override public void setNull(int p, int t, String tn) {}
        @Override public void setURL(int p, java.net.URL x) {}
        @Override public java.sql.ParameterMetaData getParameterMetaData() { return null; }
        @Override public void setRowId(int p, java.sql.RowId x) {}
        @Override public void setNString(int p, String value) {}
        @Override public void setNCharacterStream(int p, java.io.Reader v, long l) {}
        @Override public void setNClob(int p, java.sql.NClob v) {}
        @Override public void setClob(int p, java.io.Reader r, long l) {}
        @Override public void setBlob(int p, java.io.InputStream is, long l) {}
        @Override public void setNClob(int p, java.io.Reader r, long l) {}
        @Override public void setSQLXML(int p, java.sql.SQLXML x) {}
        @Override public void setAsciiStream(int p, java.io.InputStream x, long l) {}
        @Override public void setBinaryStream(int p, java.io.InputStream x, long l) {}
        @Override public void setCharacterStream(int p, java.io.Reader r, long l) {}
        @Override public void setAsciiStream(int p, java.io.InputStream x) {}
        @Override public void setBinaryStream(int p, java.io.InputStream x) {}
        @Override public void setCharacterStream(int p, java.io.Reader r) {}
        @Override public void setNCharacterStream(int p, java.io.Reader value) {}
        @Override public void setClob(int p, java.io.Reader reader) {}
        @Override public void setBlob(int p, java.io.InputStream is) {}
        @Override public void setNClob(int p, java.io.Reader reader) {}
        @Override public ResultSet executeQuery(String sql) { return null; }
        @Override public int executeUpdate(String sql) { return 0; }
        @Override public int getMaxFieldSize() { return 0; }
        @Override public void setMaxFieldSize(int max) {}
        @Override public int getMaxRows() { return 0; }
        @Override public void setMaxRows(int max) {}
        @Override public void setEscapeProcessing(boolean enable) {}
        @Override public int getQueryTimeout() { return 0; }
        @Override public void setQueryTimeout(int seconds) {}
        @Override public void cancel() {}
        @Override public java.sql.SQLWarning getWarnings() { return null; }
        @Override public void clearWarnings() {}
        @Override public void setCursorName(String name) {}
        @Override public boolean execute(String sql) { return false; }
        @Override public ResultSet getResultSet() { return null; }
        @Override public int getUpdateCount() { return -1; }
        @Override public boolean getMoreResults() { return false; }
        @Override public void setFetchDirection(int direction) {}
        @Override public int getFetchDirection() { return ResultSet.FETCH_FORWARD; }
        @Override public void setFetchSize(int rows) {}
        @Override public int getFetchSize() { return 0; }
        @Override public int getResultSetConcurrency() { return ResultSet.CONCUR_READ_ONLY; }
        @Override public int getResultSetType() { return ResultSet.TYPE_FORWARD_ONLY; }
        @Override public void addBatch(String sql) {}
        @Override public void clearBatch() {}
        @Override public int[] executeBatch() { return new int[0]; }
        @Override public Connection getConnection() { return null; }
        @Override public boolean getMoreResults(int current) { return false; }
        @Override public ResultSet getGeneratedKeys() { return null; }
        @Override public int executeUpdate(String sql, int autoGenKeys) { return 0; }
        @Override public int executeUpdate(String sql, int[] columnIndexes) { return 0; }
        @Override public int executeUpdate(String sql, String[] columnNames) { return 0; }
        @Override public boolean execute(String sql, int autoGenKeys) { return false; }
        @Override public boolean execute(String sql, int[] columnIndexes) { return false; }
        @Override public boolean execute(String sql, String[] columnNames) { return false; }
        @Override public int getResultSetHoldability() { return 0; }
        @Override public boolean isClosed() { return false; }
        @Override public void setPoolable(boolean poolable) {}
        @Override public boolean isPoolable() { return false; }
        @Override public void closeOnCompletion() {}
        @Override public boolean isCloseOnCompletion() { return false; }
        @Override public <T> T unwrap(Class<T> iface) { return null; }
        @Override public boolean isWrapperFor(Class<?> iface) { return false; }
    }

    static final class StubResultSet implements ResultSet {
        private final Map<String, Object> row = new HashMap<>();
        private boolean hasRow = true;
        private boolean read = false;

        static StubResultSet empty() {
            StubResultSet rs = new StubResultSet();
            rs.hasRow = false;
            return rs;
        }

        void row(String column, Object value) { row.put(column, value); }

        @Override
        public boolean next() {
            if (!hasRow || read) return false;
            read = true;
            return true;
        }

        @Override public void close() {}
        @Override public String getString(String columnLabel) {
            Object v = row.get(columnLabel);
            return v == null ? null : v.toString();
        }
        @Override public long getLong(String columnLabel) {
            Object v = row.get(columnLabel);
            if (v == null) return 0L;
            if (v instanceof Long l) return l;
            if (v instanceof Integer i) return (long) i;
            if (v instanceof Number n) return n.longValue();
            return Long.parseLong(v.toString());
        }
        @Override public BigDecimal getBigDecimal(String columnLabel) {
            Object v = row.get(columnLabel);
            if (v == null) return null;
            if (v instanceof BigDecimal b) return b;
            return new BigDecimal(v.toString());
        }
        @Override public java.sql.Date getDate(String columnLabel) {
            Object v = row.get(columnLabel);
            return v == null ? null : (java.sql.Date) v;
        }
        @Override public Timestamp getTimestamp(String columnLabel) {
            Object v = row.get(columnLabel);
            return v == null ? null : (Timestamp) v;
        }
        @Override public boolean getBoolean(String columnLabel) {
            Object v = row.get(columnLabel);
            if (v == null) return false;
            return (Boolean) v;
        }
        @Override public Object getObject(String columnLabel) { return row.get(columnLabel); }
        @Override public boolean wasNull() { return false; }

        // ---- unused ResultSet surface ----
        @Override public String getString(int columnIndex) { return null; }
        @Override public boolean getBoolean(int columnIndex) { return false; }
        @Override public byte getByte(int columnIndex) { return 0; }
        @Override public short getShort(int columnIndex) { return 0; }
        @Override public int getInt(int columnIndex) { return 0; }
        @Override public long getLong(int columnIndex) { return 0; }
        @Override public float getFloat(int columnIndex) { return 0; }
        @Override public double getDouble(int columnIndex) { return 0; }
        @Override public BigDecimal getBigDecimal(int columnIndex, int scale) { return null; }
        @Override public byte[] getBytes(int columnIndex) { return new byte[0]; }
        @Override public java.sql.Date getDate(int columnIndex) { return null; }
        @Override public java.sql.Time getTime(int columnIndex) { return null; }
        @Override public Timestamp getTimestamp(int columnIndex) { return null; }
        @Override public java.io.InputStream getAsciiStream(int columnIndex) { return null; }
        @Override public java.io.InputStream getUnicodeStream(int columnIndex) { return null; }
        @Override public java.io.InputStream getBinaryStream(int columnIndex) { return null; }
        @Override public byte getByte(String columnLabel) { return 0; }
        @Override public short getShort(String columnLabel) { return 0; }
        @Override public int getInt(String columnLabel) { return 0; }
        @Override public float getFloat(String columnLabel) { return 0; }
        @Override public double getDouble(String columnLabel) { return 0; }
        @Override public BigDecimal getBigDecimal(String columnLabel, int scale) { return null; }
        @Override public byte[] getBytes(String columnLabel) { return new byte[0]; }
        @Override public java.sql.Time getTime(String columnLabel) { return null; }
        @Override public java.io.InputStream getAsciiStream(String columnLabel) { return null; }
        @Override public java.io.InputStream getUnicodeStream(String columnLabel) { return null; }
        @Override public java.io.InputStream getBinaryStream(String columnLabel) { return null; }
        @Override public java.sql.SQLWarning getWarnings() { return null; }
        @Override public void clearWarnings() {}
        @Override public String getCursorName() { return null; }
        @Override public ResultSetMetaData getMetaData() { return null; }
        @Override public Object getObject(int columnIndex) { return null; }
        @Override public int findColumn(String columnLabel) { return 0; }
        @Override public java.io.Reader getCharacterStream(int columnIndex) { return null; }
        @Override public java.io.Reader getCharacterStream(String columnLabel) { return null; }
        @Override public BigDecimal getBigDecimal(int columnIndex) { return null; }
        @Override public boolean isBeforeFirst() { return false; }
        @Override public boolean isAfterLast() { return false; }
        @Override public boolean isFirst() { return false; }
        @Override public boolean isLast() { return false; }
        @Override public void beforeFirst() {}
        @Override public void afterLast() {}
        @Override public boolean first() { return false; }
        @Override public boolean last() { return false; }
        @Override public int getRow() { return 0; }
        @Override public boolean absolute(int row) { return false; }
        @Override public boolean relative(int rows) { return false; }
        @Override public boolean previous() { return false; }
        @Override public void setFetchDirection(int direction) {}
        @Override public int getFetchDirection() { return ResultSet.FETCH_FORWARD; }
        @Override public void setFetchSize(int rows) {}
        @Override public int getFetchSize() { return 0; }
        @Override public int getType() { return ResultSet.TYPE_FORWARD_ONLY; }
        @Override public int getConcurrency() { return ResultSet.CONCUR_READ_ONLY; }
        @Override public boolean rowUpdated() { return false; }
        @Override public boolean rowInserted() { return false; }
        @Override public boolean rowDeleted() { return false; }
        @Override public void updateNull(int columnIndex) {}
        @Override public void updateBoolean(int columnIndex, boolean x) {}
        @Override public void updateByte(int columnIndex, byte x) {}
        @Override public void updateShort(int columnIndex, short x) {}
        @Override public void updateInt(int columnIndex, int x) {}
        @Override public void updateLong(int columnIndex, long x) {}
        @Override public void updateFloat(int columnIndex, float x) {}
        @Override public void updateDouble(int columnIndex, double x) {}
        @Override public void updateBigDecimal(int columnIndex, BigDecimal x) {}
        @Override public void updateString(int columnIndex, String x) {}
        @Override public void updateBytes(int columnIndex, byte[] x) {}
        @Override public void updateDate(int columnIndex, java.sql.Date x) {}
        @Override public void updateTime(int columnIndex, java.sql.Time x) {}
        @Override public void updateTimestamp(int columnIndex, java.sql.Timestamp x) {}
        @Override public void updateAsciiStream(int columnIndex, java.io.InputStream x, int length) {}
        @Override public void updateBinaryStream(int columnIndex, java.io.InputStream x, int length) {}
        @Override public void updateCharacterStream(int columnIndex, java.io.Reader x, int length) {}
        @Override public void updateObject(int columnIndex, Object x, int scaleOrLength) {}
        @Override public void updateObject(int columnIndex, Object x) {}
        @Override public void updateNull(String columnLabel) {}
        @Override public void updateBoolean(String columnLabel, boolean x) {}
        @Override public void updateByte(String columnLabel, byte x) {}
        @Override public void updateShort(String columnLabel, short x) {}
        @Override public void updateInt(String columnLabel, int x) {}
        @Override public void updateLong(String columnLabel, long x) {}
        @Override public void updateFloat(String columnLabel, float x) {}
        @Override public void updateDouble(String columnLabel, double x) {}
        @Override public void updateBigDecimal(String columnLabel, BigDecimal x) {}
        @Override public void updateString(String columnLabel, String x) {}
        @Override public void updateBytes(String columnLabel, byte[] x) {}
        @Override public void updateDate(String columnLabel, java.sql.Date x) {}
        @Override public void updateTime(String columnLabel, java.sql.Time x) {}
        @Override public void updateTimestamp(String columnLabel, java.sql.Timestamp x) {}
        @Override public void updateAsciiStream(String columnLabel, java.io.InputStream x, int length) {}
        @Override public void updateBinaryStream(String columnLabel, java.io.InputStream x, int length) {}
        @Override public void updateCharacterStream(String columnLabel, java.io.Reader r, int length) {}
        @Override public void updateObject(String columnLabel, Object x, int scaleOrLength) {}
        @Override public void updateObject(String columnLabel, Object x) {}
        @Override public void insertRow() {}
        @Override public void updateRow() {}
        @Override public void deleteRow() {}
        @Override public void refreshRow() {}
        @Override public void cancelRowUpdates() {}
        @Override public void moveToInsertRow() {}
        @Override public void moveToCurrentRow() {}
        @Override public java.sql.Statement getStatement() { return null; }
        @Override public Object getObject(int columnIndex, Map<String, Class<?>> map) { return null; }
        @Override public java.sql.Ref getRef(int columnIndex) { return null; }
        @Override public java.sql.Blob getBlob(int columnIndex) { return null; }
        @Override public java.sql.Clob getClob(int columnIndex) { return null; }
        @Override public java.sql.Array getArray(int columnIndex) { return null; }
        @Override public Object getObject(String columnLabel, Map<String, Class<?>> map) { return null; }
        @Override public java.sql.Ref getRef(String columnLabel) { return null; }
        @Override public java.sql.Blob getBlob(String columnLabel) { return null; }
        @Override public java.sql.Clob getClob(String columnLabel) { return null; }
        @Override public java.sql.Array getArray(String columnLabel) { return null; }
        @Override public java.sql.Date getDate(int columnIndex, java.util.Calendar cal) { return null; }
        @Override public java.sql.Date getDate(String columnLabel, java.util.Calendar cal) { return null; }
        @Override public java.sql.Time getTime(int columnIndex, java.util.Calendar cal) { return null; }
        @Override public java.sql.Time getTime(String columnLabel, java.util.Calendar cal) { return null; }
        @Override public Timestamp getTimestamp(int columnIndex, java.util.Calendar cal) { return null; }
        @Override public Timestamp getTimestamp(String columnLabel, java.util.Calendar cal) { return null; }
        @Override public java.net.URL getURL(int columnIndex) { return null; }
        @Override public java.net.URL getURL(String columnLabel) { return null; }
        @Override public void updateRef(int columnIndex, java.sql.Ref x) {}
        @Override public void updateRef(String columnLabel, java.sql.Ref x) {}
        @Override public void updateBlob(int columnIndex, java.sql.Blob x) {}
        @Override public void updateBlob(String columnLabel, java.sql.Blob x) {}
        @Override public void updateClob(int columnIndex, java.sql.Clob x) {}
        @Override public void updateClob(String columnLabel, java.sql.Clob x) {}
        @Override public void updateArray(int columnIndex, java.sql.Array x) {}
        @Override public void updateArray(String columnLabel, java.sql.Array x) {}
        @Override public java.sql.RowId getRowId(int columnIndex) { return null; }
        @Override public java.sql.RowId getRowId(String columnLabel) { return null; }
        @Override public void updateRowId(int columnIndex, java.sql.RowId x) {}
        @Override public void updateRowId(String columnLabel, java.sql.RowId x) {}
        @Override public int getHoldability() { return 0; }
        @Override public boolean isClosed() { return false; }
        @Override public void updateNString(int columnIndex, String nString) {}
        @Override public void updateNString(String columnLabel, String nString) {}
        @Override public void updateNClob(int columnIndex, java.sql.NClob nClob) {}
        @Override public void updateNClob(String columnLabel, java.sql.NClob nClob) {}
        @Override public java.sql.NClob getNClob(int columnIndex) { return null; }
        @Override public java.sql.NClob getNClob(String columnLabel) { return null; }
        @Override public java.sql.SQLXML getSQLXML(int columnIndex) { return null; }
        @Override public java.sql.SQLXML getSQLXML(String columnLabel) { return null; }
        @Override public void updateSQLXML(int columnIndex, java.sql.SQLXML xmlObject) {}
        @Override public void updateSQLXML(String columnLabel, java.sql.SQLXML xmlObject) {}
        @Override public String getNString(int columnIndex) { return null; }
        @Override public String getNString(String columnLabel) { return null; }
        @Override public java.io.Reader getNCharacterStream(int columnIndex) { return null; }
        @Override public java.io.Reader getNCharacterStream(String columnLabel) { return null; }
        @Override public void updateNCharacterStream(int columnIndex, java.io.Reader x, long length) {}
        @Override public void updateNCharacterStream(String columnLabel, java.io.Reader r, long length) {}
        @Override public void updateAsciiStream(int columnIndex, java.io.InputStream x, long length) {}
        @Override public void updateBinaryStream(int columnIndex, java.io.InputStream x, long length) {}
        @Override public void updateCharacterStream(int columnIndex, java.io.Reader x, long length) {}
        @Override public void updateAsciiStream(String columnLabel, java.io.InputStream x, long length) {}
        @Override public void updateBinaryStream(String columnLabel, java.io.InputStream x, long length) {}
        @Override public void updateCharacterStream(String columnLabel, java.io.Reader r, long length) {}
        @Override public void updateBlob(int columnIndex, java.io.InputStream is, long length) {}
        @Override public void updateBlob(String columnLabel, java.io.InputStream is, long length) {}
        @Override public void updateClob(int columnIndex, java.io.Reader reader, long length) {}
        @Override public void updateClob(String columnLabel, java.io.Reader reader, long length) {}
        @Override public void updateNClob(int columnIndex, java.io.Reader reader, long length) {}
        @Override public void updateNClob(String columnLabel, java.io.Reader reader, long length) {}
        @Override public void updateNCharacterStream(int columnIndex, java.io.Reader x) {}
        @Override public void updateNCharacterStream(String columnLabel, java.io.Reader r) {}
        @Override public void updateAsciiStream(int columnIndex, java.io.InputStream x) {}
        @Override public void updateBinaryStream(int columnIndex, java.io.InputStream x) {}
        @Override public void updateCharacterStream(int columnIndex, java.io.Reader x) {}
        @Override public void updateAsciiStream(String columnLabel, java.io.InputStream x) {}
        @Override public void updateBinaryStream(String columnLabel, java.io.InputStream x) {}
        @Override public void updateCharacterStream(String columnLabel, java.io.Reader r) {}
        @Override public void updateBlob(int columnIndex, java.io.InputStream is) {}
        @Override public void updateBlob(String columnLabel, java.io.InputStream is) {}
        @Override public void updateClob(int columnIndex, java.io.Reader reader) {}
        @Override public void updateClob(String columnLabel, java.io.Reader reader) {}
        @Override public void updateNClob(int columnIndex, java.io.Reader reader) {}
        @Override public void updateNClob(String columnLabel, java.io.Reader reader) {}
        @Override public <T> T getObject(int columnIndex, Class<T> type) { return null; }
        @Override public <T> T getObject(String columnLabel, Class<T> type) { return null; }
        @Override public <T> T unwrap(Class<T> iface) { return null; }
        @Override public boolean isWrapperFor(Class<?> iface) { return false; }
    }
}
