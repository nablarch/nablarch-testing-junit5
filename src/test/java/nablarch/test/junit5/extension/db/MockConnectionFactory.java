package nablarch.test.junit5.extension.db;

import nablarch.core.db.connection.ConnectionFactory;
import nablarch.core.db.connection.TransactionManagerConnection;
import nablarch.core.db.dialect.Dialect;
import nablarch.core.db.statement.ParameterizedSqlPStatement;
import nablarch.core.db.statement.SelectOption;
import nablarch.core.db.statement.SqlCStatement;
import nablarch.core.db.statement.SqlPStatement;
import nablarch.core.db.statement.SqlStatement;
import nablarch.core.db.transaction.JdbcTransactionTimeoutHandler;

import java.sql.Connection;

/**
 * {@link ConnectionFactory}のモック。
 *
 * @author Tanaka Tomoyuki
 */
public class MockConnectionFactory implements ConnectionFactory {
    @Override
    public TransactionManagerConnection getConnection(String connectionName) {
        return new TransactionManagerConnection() {
            @Override
            public void initialize() {
                
            }

            @Override
            public void commit() {

            }

            @Override
            public void rollback() {

            }

            @Override
            public void terminate() {

            }

            @Override
            public void setIsolationLevel(int level) {

            }

            @Override
            public void setJdbcTransactionTimeoutHandler(JdbcTransactionTimeoutHandler jdbcTransactionTimeoutHandler) {

            }

            @Override
            public Connection getConnection() {
                return null;
            }

            @Override
            public Dialect getDialect() {
                return null;
            }

            @Override
            public void removeStatement(SqlStatement statement) {

            }

            @Override
            public SqlPStatement prepareStatement(String sql) {
                return null;
            }

            @Override
            public SqlPStatement prepareStatement(String sql, SelectOption selectOption) {
                return null;
            }

            @Override
            public SqlPStatement prepareStatement(String sql, int autoGeneratedKeys) {
                return null;
            }

            @Override
            public SqlPStatement prepareStatement(String sql, int[] columnIndexes) {
                return null;
            }

            @Override
            public SqlPStatement prepareStatement(String sql, String[] columnNames) {
                return null;
            }

            @Override
            public SqlPStatement prepareStatementBySqlId(String sqlId) {
                return null;
            }

            @Override
            public SqlPStatement prepareStatementBySqlId(String sqlId, SelectOption selectOption) {
                return null;
            }

            @Override
            public ParameterizedSqlPStatement prepareParameterizedSqlStatement(String sql) {
                return null;
            }

            @Override
            public ParameterizedSqlPStatement prepareParameterizedSqlStatement(String sql, SelectOption selectOption) {
                return null;
            }

            @Override
            public ParameterizedSqlPStatement prepareParameterizedSqlStatementBySqlId(String sqlId) {
                return null;
            }

            @Override
            public ParameterizedSqlPStatement prepareParameterizedSqlStatementBySqlId(String sqlId, SelectOption selectOption) {
                return null;
            }

            @Override
            public ParameterizedSqlPStatement prepareParameterizedSqlStatement(String sql, Object condition) {
                return null;
            }

            @Override
            public ParameterizedSqlPStatement prepareParameterizedSqlStatement(String sql, Object condition, SelectOption selectOption) {
                return null;
            }

            @Override
            public ParameterizedSqlPStatement prepareParameterizedSqlStatementBySqlId(String sqlId, Object condition) {
                return null;
            }

            @Override
            public ParameterizedSqlPStatement prepareParameterizedSqlStatementBySqlId(String sqlId, Object condition, SelectOption selectOption) {
                return null;
            }

            @Override
            public ParameterizedSqlPStatement prepareParameterizedCountSqlStatementBySqlId(String sqlId, Object condition) {
                return null;
            }

            @Override
            public SqlPStatement prepareCountStatementBySqlId(String sqlId) {
                return null;
            }

            @Override
            public SqlCStatement prepareCall(String sql) {
                return null;
            }

            @Override
            public SqlCStatement prepareCallBySqlId(String sqlId) {
                return null;
            }
        };
    }
}
