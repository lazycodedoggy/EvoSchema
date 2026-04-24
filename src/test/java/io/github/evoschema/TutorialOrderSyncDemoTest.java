package io.github.evoschema;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.jdbc.core.JdbcTemplate;

public class TutorialOrderSyncDemoTest extends AbstractEvoSchemaIntegrationTest
{
    private static final String CUSTOMER_SCHEMA_SQL = "unittest/tutorial-order-sync-customer-schema.sql";
    private static final String FINANCE_SCHEMA_SQL = "unittest/tutorial-order-sync-finance-schema.sql";

    @BeforeClass
    public static void initSchema()
    {
        prepareTestEnvironment();
        initSchemaForDataSource("customer", CUSTOMER_SCHEMA_SQL);
        initSchemaForDataSource("finance", FINANCE_SCHEMA_SQL);
    }

    @Test
    public void shouldRunTutorialOrderSyncDemo()
    {
        Starter.main(new String[] { "tutorial_order_sync" });

        JdbcTemplate baseTemplate = jdbcTemplate("customer");
        JdbcTemplate financeTemplate = jdbcTemplate("finance");

        Long readyCount = baseTemplate.queryForObject(
                "SELECT COUNT(1) FROM customer_orders WHERE status = 'READY'",
                Long.class
        );
        Long syncLogCount = baseTemplate.queryForObject(
                "SELECT COUNT(1) FROM order_sync_log WHERE status = 'SYNCED'",
                Long.class
        );
        Long financeSyncedCount = financeTemplate.queryForObject(
                "SELECT COUNT(1) FROM finance_orders WHERE sync_status = 'SYNCED'",
                Long.class
        );
        String syncNoteDefault = financeTemplate.queryForObject(
                "SELECT COLUMN_DEFAULT FROM information_schema.COLUMNS " +
                        "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'finance_orders' AND COLUMN_NAME = 'sync_note'",
                String.class
        );

        Assert.assertEquals(Long.valueOf(2L), readyCount);
        Assert.assertEquals(Long.valueOf(2L), syncLogCount);
        Assert.assertEquals(Long.valueOf(2L), financeSyncedCount);
        Assert.assertEquals("DONE", syncNoteDefault);
        Assert.assertTrue(tableExists("customer", "order_sync_log"));
        Assert.assertTrue(columnExists("finance", "finance_orders", "sync_note"));
    }
}
