package io.github.evoschema;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class RollbackOnPreDdlFailureDemoTest extends AbstractEvoSchemaIntegrationTest
{
    private static final String CUSTOMER_SCHEMA_SQL = "unittest/rollback-on-preddl-failure-base-schema.sql";
    private static final String FINANCE_SCHEMA_SQL = "unittest/rollback-on-preddl-failure-finance-schema.sql";

    @BeforeClass
    public static void initSchema()
    {
        prepareTestEnvironment();
        initSchemaForDataSource("customer", CUSTOMER_SCHEMA_SQL);
        initSchemaForDataSource("finance", FINANCE_SCHEMA_SQL);
    }

    @Test
    public void shouldRollbackCompletedPreDdlWhenLaterPreDdlFails()
    {
        Starter.main(new String[] { "rollback_on_preddl_failure" });

        Assert.assertFalse(tableExists("customer", "preddl_rollback_marker"));
    }
}
