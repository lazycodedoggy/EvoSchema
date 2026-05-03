package io.github.evoschema.processor.dbscanner;

import io.github.evoschema.processor.exception.EvoSchemaException;
import io.github.evoschema.processor.exception.EvoSchemaException.ProcesssError;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;

public abstract class AbstractGuardedJdbcTemplate extends JdbcTemplate
{
    private static final Logger logger = LoggerFactory.getLogger(AbstractGuardedJdbcTemplate.class);

    private final String context;

    private final String dataSourceKey;

    protected AbstractGuardedJdbcTemplate(DataSource dataSource, String context, String dataSourceKey)
    {
        super(dataSource);
        this.context = context;
        this.dataSourceKey = dataSourceKey;
    }

    protected String getContext()
    {
        return context;
    }

    protected abstract void validateSql(String sql);

    protected abstract ProcesssError getPreparedStatementError();

    @Override
    public void execute(String sql)
    {
        validateSql(sql);
        logSqlStart("JDBC-EXECUTE", sql);
        try {
            super.execute(sql);
            logSqlSuccess("JDBC-EXECUTE", sql);
        } catch (RuntimeException error) {
            logSqlFailure("JDBC-EXECUTE", sql, error);
            throw error;
        }
    }

    @Override
    public int update(String sql)
    {
        validateSql(sql);
        logSqlStart("JDBC-UPDATE", sql);
        try {
            int affectedRows = super.update(sql);
            logger.info(
                    "{} affectedRows={}",
                    ExecutionLogHelper.sqlEvent("SUCCESS", "JDBC-UPDATE", context, context, dataSourceKey, sql),
                    affectedRows
            );
            return affectedRows;
        } catch (RuntimeException error) {
            logSqlFailure("JDBC-UPDATE", sql, error);
            throw error;
        }
    }

    @Override
    public int update(String sql, Object... args)
    {
        validateSql(sql);
        logSqlStart("JDBC-UPDATE", sql);
        try {
            int affectedRows = super.update(sql, args);
            logger.info(
                    "{} affectedRows={}",
                    ExecutionLogHelper.sqlEvent("SUCCESS", "JDBC-UPDATE", context, context, dataSourceKey, sql),
                    affectedRows
            );
            return affectedRows;
        } catch (RuntimeException error) {
            logSqlFailure("JDBC-UPDATE", sql, error);
            throw error;
        }
    }

    @Override
    public int[] batchUpdate(String... sql)
    {
        for (String statement : sql) {
            validateSql(statement);
        }
        logger.info(
                "{}",
                ExecutionLogHelper.methodEvent(
                        "START",
                        "JDBC-BATCH",
                        context,
                        context,
                        dataSourceKey
                )
        );
        try {
            int[] result = super.batchUpdate(sql);
            logger.info(
                    "{}",
                    ExecutionLogHelper.methodEvent(
                            "SUCCESS",
                            "JDBC-BATCH",
                            context,
                            context,
                            dataSourceKey
                    )
            );
            return result;
        } catch (RuntimeException error) {
            logSqlFailure("JDBC-BATCH", sql.length > 0 ? sql[0] : null, error);
            throw error;
        }
    }

    @Override
    public int[] batchUpdate(String sql, List<Object[]> batchArgs)
    {
        validateSql(sql);
        logSqlStart("JDBC-BATCH", sql);
        try {
            int[] result = super.batchUpdate(sql, batchArgs);
            logger.info(
                    "{} batchSize={}",
                    ExecutionLogHelper.sqlEvent("SUCCESS", "JDBC-BATCH", context, context, dataSourceKey, sql),
                    batchArgs.size()
            );
            return result;
        } catch (RuntimeException error) {
            logSqlFailure("JDBC-BATCH", sql, error);
            throw error;
        }
    }

    @Override
    public <T> T queryForObject(String sql, Class<T> requiredType)
    {
        validateSql(sql);
        logSqlStart("JDBC-QUERY", sql);
        try {
            T result = super.queryForObject(sql, requiredType);
            logSqlSuccess("JDBC-QUERY", sql);
            return result;
        } catch (RuntimeException error) {
            logSqlFailure("JDBC-QUERY", sql, error);
            throw error;
        }
    }

    @Override
    public <T> List<T> queryForList(String sql, Class<T> elementType)
    {
        validateSql(sql);
        logSqlStart("JDBC-QUERY", sql);
        try {
            List<T> result = super.queryForList(sql, elementType);
            logSqlSuccess("JDBC-QUERY", sql);
            return result;
        } catch (RuntimeException error) {
            logSqlFailure("JDBC-QUERY", sql, error);
            throw error;
        }
    }

    @Override
    public Map<String, Object> queryForMap(String sql)
    {
        validateSql(sql);
        logSqlStart("JDBC-QUERY", sql);
        try {
            Map<String, Object> result = super.queryForMap(sql);
            logSqlSuccess("JDBC-QUERY", sql);
            return result;
        } catch (RuntimeException error) {
            logSqlFailure("JDBC-QUERY", sql, error);
            throw error;
        }
    }

    @Override
    public List<Map<String, Object>> queryForList(String sql)
    {
        validateSql(sql);
        logSqlStart("JDBC-QUERY", sql);
        try {
            List<Map<String, Object>> result = super.queryForList(sql);
            logSqlSuccess("JDBC-QUERY", sql);
            return result;
        } catch (RuntimeException error) {
            logSqlFailure("JDBC-QUERY", sql, error);
            throw error;
        }
    }

    @Override
    public int update(PreparedStatementCreator psc)
    {
        throw new EvoSchemaException(getPreparedStatementError(), "PreparedStatementCreator is not allowed: " + context);
    }

    private void logSqlStart(String phase, String sql)
    {
        ExecutionLogHelper.overrideCurrentDataSource(dataSourceKey);
        logger.info("{}", ExecutionLogHelper.sqlEvent("START", phase, context, context, dataSourceKey, sql));
    }

    private void logSqlSuccess(String phase, String sql)
    {
        logger.info("{}", ExecutionLogHelper.sqlEvent("SUCCESS", phase, context, context, dataSourceKey, sql));
    }

    private void logSqlFailure(String phase, String sql, Throwable error)
    {
        Throwable rootCause = ExecutionLogHelper.unwrap(error);
        logger.error("{}", ExecutionLogHelper.failureEvent(phase, context, context, dataSourceKey, sql, rootCause), rootCause);
    }
}
