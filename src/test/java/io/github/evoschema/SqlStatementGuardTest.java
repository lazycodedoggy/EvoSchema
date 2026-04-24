package io.github.evoschema;

import io.github.evoschema.processor.dbscanner.SqlStatementGuard;
import io.github.evoschema.processor.exception.EvoSchemaException;
import org.junit.Assert;
import org.junit.Test;

public class SqlStatementGuardTest
{
    @Test
    public void shouldAllowStandardDmlForDbdml()
    {
        SqlStatementGuard.validateDmlOnly("dbdml.insert", "INSERT INTO demo_table(id) VALUES (1)");
        SqlStatementGuard.validateDmlOnly("dbdml.update", "UPDATE demo_table SET status = 'DONE' WHERE id = 1");
        SqlStatementGuard.validateDmlOnly("dbdml.delete", "DELETE FROM demo_table WHERE id = 1");
    }

    @Test
    public void shouldRejectReplaceAndCallForDbdml()
    {
        EvoSchemaException replaceError = Assert.assertThrows(
                EvoSchemaException.class,
                () -> SqlStatementGuard.validateDmlOnly("dbdml.replace", "REPLACE INTO demo_table(id) VALUES (1)")
        );
        EvoSchemaException callError = Assert.assertThrows(
                EvoSchemaException.class,
                () -> SqlStatementGuard.validateDmlOnly("dbdml.call", "CALL sync_demo_data()")
        );

        Assert.assertEquals(EvoSchemaException.ProcesssError.DML_SCRIPT_ERROR, replaceError.getReason());
        Assert.assertEquals(EvoSchemaException.ProcesssError.DML_SCRIPT_ERROR, callError.getReason());
    }

    @Test
    public void shouldAllowDmlAndQueryForDbScript()
    {
        SqlStatementGuard.validateDmlPlusQuery("dbscript.select", "SELECT id FROM demo_table");
        SqlStatementGuard.validateDmlPlusQuery("dbscript.call", "CALL sync_demo_data()");
        SqlStatementGuard.validateDmlPlusQuery(
                "dbscript.with",
                "WITH ready_orders AS (SELECT id FROM demo_table) SELECT id FROM ready_orders"
        );
    }

    @Test
    public void shouldRejectDdlForDbScript()
    {
        EvoSchemaException error = Assert.assertThrows(
                EvoSchemaException.class,
                () -> SqlStatementGuard.validateDmlPlusQuery("dbscript.ddl", "ALTER TABLE demo_table ADD COLUMN note VARCHAR(32)")
        );

        Assert.assertEquals(EvoSchemaException.ProcesssError.DML_SCRIPT_ERROR, error.getReason());
    }

    @Test
    public void shouldAllowQueryOnlyForDmlAssert()
    {
        SqlStatementGuard.validateQueryOnly("assert.select", "SELECT COUNT(1) FROM demo_table");
        SqlStatementGuard.validateQueryOnly("assert.show", "SHOW COLUMNS FROM demo_table");
        SqlStatementGuard.validateQueryOnly(
                "assert.with",
                "WITH result AS (SELECT COUNT(1) AS total FROM demo_table) SELECT total FROM result"
        );
    }

    @Test
    public void shouldRejectUpdateForDmlAssert()
    {
        EvoSchemaException error = Assert.assertThrows(
                EvoSchemaException.class,
                () -> SqlStatementGuard.validateQueryOnly("assert.update", "UPDATE demo_table SET status = 'DONE'")
        );

        Assert.assertEquals(EvoSchemaException.ProcesssError.DML_SCRIPT_ERROR, error.getReason());
    }
}
