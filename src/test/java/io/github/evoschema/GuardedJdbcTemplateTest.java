package io.github.evoschema;

import io.github.evoschema.processor.dbscanner.QueryOnlyJdbcTemplate;
import io.github.evoschema.processor.dbscanner.RestrictedJdbcTemplate;
import io.github.evoschema.processor.exception.EvoSchemaException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.jdbc.core.PreparedStatementCreator;

public class GuardedJdbcTemplateTest
{
    @Test
    public void shouldRejectDdlInRestrictedJdbcTemplateBeforeTouchingDatasource()
    {
        RestrictedJdbcTemplate template = new RestrictedJdbcTemplate(new UnsupportedDataSource(), "demo.script");

        EvoSchemaException error = Assert.assertThrows(
                EvoSchemaException.class,
                () -> template.execute("ALTER TABLE demo_table ADD COLUMN note VARCHAR(32)")
        );

        Assert.assertEquals(EvoSchemaException.ProcesssError.DML_SCRIPT_ERROR, error.getReason());
    }

    @Test
    public void shouldRejectPreparedStatementCreatorInRestrictedJdbcTemplate()
    {
        RestrictedJdbcTemplate template = new RestrictedJdbcTemplate(new UnsupportedDataSource(), "demo.script");
        PreparedStatementCreator psc = connection -> connection.prepareStatement("SELECT COUNT(1) FROM demo_table");

        EvoSchemaException error = Assert.assertThrows(EvoSchemaException.class, () -> template.update(psc));

        Assert.assertEquals(EvoSchemaException.ProcesssError.DML_SCRIPT_ERROR, error.getReason());
        Assert.assertTrue(error.getMessage().contains("demo.script"));
    }

    @Test
    public void shouldRejectDmlInQueryOnlyJdbcTemplate()
    {
        QueryOnlyJdbcTemplate template = new QueryOnlyJdbcTemplate(new UnsupportedDataSource(), "demo.assert");

        EvoSchemaException error = Assert.assertThrows(
                EvoSchemaException.class,
                () -> template.update("UPDATE demo_table SET status = 'DONE'")
        );

        Assert.assertEquals(EvoSchemaException.ProcesssError.DML_CONFIRM, error.getReason());
    }

    @Test
    public void shouldRejectPreparedStatementCreatorInQueryOnlyJdbcTemplate()
    {
        QueryOnlyJdbcTemplate template = new QueryOnlyJdbcTemplate(new UnsupportedDataSource(), "demo.assert");
        PreparedStatementCreator psc = connection -> connection.prepareStatement("SELECT COUNT(1) FROM demo_table");

        EvoSchemaException error = Assert.assertThrows(EvoSchemaException.class, () -> template.update(psc));

        Assert.assertEquals(EvoSchemaException.ProcesssError.DML_CONFIRM, error.getReason());
        Assert.assertTrue(error.getMessage().contains("demo.assert"));
    }

    private static class UnsupportedDataSource implements DataSource
    {
        @Override
        public Connection getConnection() throws SQLException
        {
            throw new UnsupportedOperationException("No real database should be touched in this test");
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException
        {
            throw new UnsupportedOperationException("No real database should be touched in this test");
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException
        {
            throw new SQLException("unwrap is not supported");
        }

        @Override
        public boolean isWrapperFor(Class<?> iface)
        {
            return false;
        }

        @Override
        public PrintWriter getLogWriter()
        {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out)
        {
        }

        @Override
        public void setLoginTimeout(int seconds)
        {
        }

        @Override
        public int getLoginTimeout()
        {
            return 0;
        }

        @Override
        public Logger getParentLogger()
        {
            return Logger.getGlobal();
        }
    }
}
