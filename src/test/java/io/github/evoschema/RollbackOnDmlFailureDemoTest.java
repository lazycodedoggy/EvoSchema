package io.github.evoschema;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.jdbc.core.JdbcTemplate;

public class RollbackOnDmlFailureDemoTest extends AbstractEvoSchemaIntegrationTest
{
    private static final String BASE_SCHEMA_SQL = "unittest/rollback-on-dml-failure-base-schema.sql";
    private static final String FINANCE_SCHEMA_SQL = "unittest/rollback-on-dml-failure-finance-schema.sql";

    @BeforeClass
    public static void initSchema()
    {
        prepareTestEnvironment();
        initSchemaForDataSource("customer", BASE_SCHEMA_SQL);
        initSchemaForDataSource("finance", FINANCE_SCHEMA_SQL);
    }

    @Test
    public void shouldRollbackDmlTransactionAndCompensatePreDdlWhenScriptFails()
    {
        Starter.main(new String[] { "rollback_on_dml_failure" });

        JdbcTemplate baseTemplate = jdbcTemplate("customer");
        JdbcTemplate financeTemplate = jdbcTemplate("finance");

        String baseStatus = baseTemplate.queryForObject(
                "SELECT status FROM dml_rollback_orders WHERE order_id = 201",
                String.class
        );
        Long auditCount = tableExists("customer", "dml_rollback_audit")
                ? baseTemplate.queryForObject("SELECT COUNT(1) FROM dml_rollback_audit", Long.class)
                : 0L;
        String financeStatus = financeTemplate.queryForObject(
                "SELECT sync_status FROM dml_rollback_finance_orders WHERE order_id = 201",
                String.class
        );

        Assert.assertEquals("NEW", baseStatus);
        Assert.assertEquals(Long.valueOf(0L), auditCount);
        Assert.assertEquals("PENDING", financeStatus);
        Assert.assertFalse(tableExists("customer", "dml_rollback_audit"));
        Assert.assertFalse(columnExists("finance", "dml_rollback_finance_orders", "rollback_marker"));
    }
}
