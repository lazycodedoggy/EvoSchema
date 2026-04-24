package io.github.evoschema.processor.dbscanner;

import io.github.evoschema.processor.exception.EvoSchemaException;
import io.github.evoschema.processor.exception.EvoSchemaException.ProcesssError;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;

public abstract class AbstractGuardedJdbcTemplate extends JdbcTemplate
{
    private final String context;

    protected AbstractGuardedJdbcTemplate(DataSource dataSource, String context)
    {
        super(dataSource);
        this.context = context;
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
        super.execute(sql);
    }

    @Override
    public int update(String sql)
    {
        validateSql(sql);
        return super.update(sql);
    }

    @Override
    public int update(String sql, Object... args)
    {
        validateSql(sql);
        return super.update(sql, args);
    }

    @Override
    public int[] batchUpdate(String... sql)
    {
        for (String statement : sql) {
            validateSql(statement);
        }
        return super.batchUpdate(sql);
    }

    @Override
    public int[] batchUpdate(String sql, List<Object[]> batchArgs)
    {
        validateSql(sql);
        return super.batchUpdate(sql, batchArgs);
    }

    @Override
    public <T> T queryForObject(String sql, Class<T> requiredType)
    {
        validateSql(sql);
        return super.queryForObject(sql, requiredType);
    }

    @Override
    public Map<String, Object> queryForMap(String sql)
    {
        validateSql(sql);
        return super.queryForMap(sql);
    }

    @Override
    public List<Map<String, Object>> queryForList(String sql)
    {
        validateSql(sql);
        return super.queryForList(sql);
    }

    @Override
    public int update(PreparedStatementCreator psc)
    {
        throw new EvoSchemaException(getPreparedStatementError(), "PreparedStatementCreator is not allowed: " + context);
    }
}

