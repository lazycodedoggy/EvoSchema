package io.github.evoschema.dbscript;

import com.google.common.collect.ImmutableList;
import io.github.evoschema.annotation.DBDML;
import io.github.evoschema.annotation.DBPREDDL;
import io.github.evoschema.annotation.DBScript;
import io.github.evoschema.annotation.TargetDBTemplate;
import io.github.evoschema.processor.exception.EvoSchemaException;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component("rollback_on_dml_failure")
public class RollbackOnDmlFailureDemo
{
    @DBPREDDL(order = 1, dataSource = "customer")
    public List<String> createRollbackAuditTable()
    {
        return ImmutableList.of(
                "CREATE TABLE IF NOT EXISTS dml_rollback_audit (order_id BIGINT PRIMARY KEY, note VARCHAR(32) NOT NULL)",
                "DROP TABLE IF EXISTS dml_rollback_audit"
        );
    }

    @DBPREDDL(order = 2, dataSource = "finance")
    public List<String> addRollbackColumn()
    {
        return ImmutableList.of(
                "ALTER TABLE dml_rollback_finance_orders ADD COLUMN rollback_marker VARCHAR(32) DEFAULT NULL",
                "ALTER TABLE dml_rollback_finance_orders DROP COLUMN rollback_marker"
        );
    }

    @DBDML(order = 1, dataSource = "customer")
    public List<String> markOrderProcessing()
    {
        return ImmutableList.of(
                "UPDATE dml_rollback_orders SET status = 'PROCESSING' WHERE order_id = 201"
        );
    }

    @DBScript(order = 2)
    public void failDuringScript(@TargetDBTemplate(dataSource = "customer") JdbcTemplate baseTemplate,
            @TargetDBTemplate(dataSource = "finance") JdbcTemplate financeTemplate)
    {
        baseTemplate.update(
                "INSERT INTO dml_rollback_audit(order_id, note) VALUES (?, ?)",
                201L,
                "before-failure"
        );
        financeTemplate.update(
                "UPDATE dml_rollback_finance_orders SET sync_status = 'SYNCED', rollback_marker = 'SCRIPT' WHERE order_id = ?",
                201L
        );
        throw new EvoSchemaException(EvoSchemaException.ProcesssError.DML_SCRIPT_ERROR, "expected rollback for unittest");
    }
}
