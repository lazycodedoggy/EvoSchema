package io.github.evoschema.processor.dbscanner;

import io.github.evoschema.processor.exception.EvoSchemaException.ProcesssError;
import javax.sql.DataSource;

public class RestrictedJdbcTemplate extends AbstractGuardedJdbcTemplate
{
    public RestrictedJdbcTemplate(DataSource dataSource, String context)
    {
        this(dataSource, context, "<unknown>");
    }

    public RestrictedJdbcTemplate(DataSource dataSource, String context, String dataSourceKey)
    {
        super(dataSource, context, dataSourceKey);
    }

    @Override
    protected void validateSql(String sql)
    {
        SqlStatementGuard.validateDmlPlusQuery(getContext(), sql);
    }

    @Override
    protected ProcesssError getPreparedStatementError()
    {
        return ProcesssError.DML_SCRIPT_ERROR;
    }
}
