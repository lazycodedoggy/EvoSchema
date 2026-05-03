package io.github.evoschema.processor.dbscanner;

import io.github.evoschema.processor.exception.EvoSchemaException.ProcesssError;
import javax.sql.DataSource;

public class QueryOnlyJdbcTemplate extends AbstractGuardedJdbcTemplate
{
    public QueryOnlyJdbcTemplate(DataSource dataSource, String context)
    {
        this(dataSource, context, "<unknown>");
    }

    public QueryOnlyJdbcTemplate(DataSource dataSource, String context, String dataSourceKey)
    {
        super(dataSource, context, dataSourceKey);
    }

    @Override
    protected void validateSql(String sql)
    {
        SqlStatementGuard.validateQueryOnly(getContext(), sql, ProcesssError.DML_CONFIRM);
    }

    @Override
    protected ProcesssError getPreparedStatementError()
    {
        return ProcesssError.DML_CONFIRM;
    }
}
