package io.github.evoschema.dbscript;

import com.google.common.collect.ImmutableList;
import io.github.evoschema.annotation.DBDML;
import io.github.evoschema.annotation.DBDMLAssert;
import io.github.evoschema.annotation.DBPOSTDDL;
import io.github.evoschema.annotation.DBPREDDL;
import io.github.evoschema.annotation.DBScript;
import io.github.evoschema.annotation.TargetDBTemplate;
import io.github.evoschema.processor.exception.EvoSchemaException;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component("tutorial_order_sync")
public class TutorialOrderSyncDemo
{
    @DBPREDDL(order = 1, dataSource = "customer")
    public List<String> prepareTutorialTable()
    {
        return ImmutableList.of(
                "CREATE TABLE IF NOT EXISTS order_sync_log (" +
                        "order_id BIGINT PRIMARY KEY," +
                        "status VARCHAR(32) NOT NULL," +
                        "synced_at BIGINT NOT NULL" +
                        ");",
                "DROP TABLE IF EXISTS order_sync_log;"
        );
    }

    @DBPREDDL(order = 2, dataSource = "finance")
    public List<String> prepareFinanceColumn()
    {
        return ImmutableList.of(
                "ALTER TABLE finance_orders ADD COLUMN sync_note VARCHAR(64) DEFAULT NULL;",
                "ALTER TABLE finance_orders DROP COLUMN sync_note;"
        );
    }

    @DBDML(order = 1, dataSource = "customer")
    public List<String> markOrdersReady()
    {
        return ImmutableList.of(
                "UPDATE customer_orders SET status = 'READY', updated_at = updated_at + 100 WHERE status = 'NEW'",
                "DELETE FROM order_sync_log WHERE order_id < 0"
        );
    }

    @DBScript(order = 2)
    public void syncFinanceOrders(@TargetDBTemplate(dataSource = "customer") JdbcTemplate baseTemplate,
            @TargetDBTemplate(dataSource = "finance") JdbcTemplate financeTemplate)
    {
        List<Long> readyOrderIds = baseTemplate.queryForList(
                "SELECT order_id FROM customer_orders WHERE status = 'READY' ORDER BY order_id",
                Long.class
        );
        for (Long orderId : readyOrderIds) {
            financeTemplate.update(
                    "UPDATE finance_orders SET sync_status = 'SYNCED', sync_note = 'synced-by-tutorial' WHERE order_id = ?",
                    orderId
            );
            baseTemplate.update(
                    "INSERT INTO order_sync_log(order_id, status, synced_at) VALUES (?, 'SYNCED', ?)",
                    orderId,
                    1700000000L + orderId
            );
        }
    }

    @DBDMLAssert(order = 3)
    public void assertMigrationResult(@TargetDBTemplate(dataSource = "customer") JdbcTemplate baseTemplate,
            @TargetDBTemplate(dataSource = "finance") JdbcTemplate financeTemplate)
    {
        Long readyCount = baseTemplate.queryForObject(
                "SELECT COUNT(1) FROM customer_orders WHERE status = 'READY'",
                Long.class
        );
        Long syncedFinanceCount = financeTemplate.queryForObject(
                "SELECT COUNT(1) FROM finance_orders WHERE sync_status = 'SYNCED'",
                Long.class
        );
        Long logCount = baseTemplate.queryForObject(
                "SELECT COUNT(1) FROM order_sync_log WHERE status = 'SYNCED'",
                Long.class
        );
        long expectedReadyCount = readyCount == null ? 0L : readyCount;
        long actualSyncedFinanceCount = syncedFinanceCount == null ? 0L : syncedFinanceCount;
        long actualLogCount = logCount == null ? 0L : logCount;
        if (expectedReadyCount != actualSyncedFinanceCount || expectedReadyCount != actualLogCount) {
            throw new EvoSchemaException(
                    EvoSchemaException.ProcesssError.DML_CONFIRM,
                    String.format(
                            "tutorial assert failed: ready=%d, financeSynced=%d, syncLog=%d",
                            expectedReadyCount,
                            actualSyncedFinanceCount,
                            actualLogCount
                    )
            );
        }
    }

    @DBPOSTDDL(order = 1, dataSource = "finance")
    public List<String> cleanupFinanceColumn()
    {
        return ImmutableList.of(
                "ALTER TABLE finance_orders MODIFY COLUMN sync_note VARCHAR(64) DEFAULT 'DONE'"
        );
    }
}
