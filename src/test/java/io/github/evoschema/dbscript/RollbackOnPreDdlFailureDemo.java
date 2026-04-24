package io.github.evoschema.dbscript;

import com.google.common.collect.ImmutableList;
import io.github.evoschema.annotation.DBPREDDL;
import java.util.List;
import org.springframework.stereotype.Component;

@Component("rollback_on_preddl_failure")
public class RollbackOnPreDdlFailureDemo
{
    @DBPREDDL(order = 1, dataSource = "customer")
    public List<String> createRollbackTable()
    {
        return ImmutableList.of(
                "CREATE TABLE IF NOT EXISTS preddl_rollback_marker (id BIGINT PRIMARY KEY)",
                "DROP TABLE IF EXISTS preddl_rollback_marker"
        );
    }

    @DBPREDDL(order = 2, dataSource = "finance")
    public List<String> failOnMissingTable()
    {
        return ImmutableList.of(
                "ALTER TABLE finance_orders_that_do_not_exist ADD COLUMN should_fail BIGINT",
                "ALTER TABLE finance_orders_that_do_not_exist DROP COLUMN should_fail"
        );
    }
}
