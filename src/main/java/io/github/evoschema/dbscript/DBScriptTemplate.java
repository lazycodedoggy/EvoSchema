package io.github.evoschema.dbscript;

import com.google.common.collect.ImmutableList;
import io.github.evoschema.annotation.DBDML;
import io.github.evoschema.annotation.DBDMLAssert;
import io.github.evoschema.annotation.DBPOSTDDL;
import io.github.evoschema.annotation.DBPREDDL;
import io.github.evoschema.annotation.DBScript;
import io.github.evoschema.annotation.TargetDBTemplate;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Scaffold for EvoSchema users.
 * <p>
 * Rename this class, add your own {@code @Component("componentName")}, then replace the placeholder SQL
 * with your real migration logic.
 */
public class DBScriptTemplate
{
    @DBPREDDL(order = 1, dataSource = "customer")
    public List<String> preDDL()
    {
        return ImmutableList.of(
                "CREATE TABLE your_table(id BIGINT PRIMARY KEY);",
                "DROP TABLE IF EXISTS your_table;"
        );
    }

    @DBDML(order = 1, dataSource = "customer")
    public List<String> dml()
    {
        return ImmutableList.of(
                "UPDATE your_table SET id = id WHERE id > 0;"
        );
    }

    @DBScript(order = 2)
    public void script(@TargetDBTemplate(dataSource = "customer") JdbcTemplate template)
    {
        template.queryForList("SELECT id FROM your_table LIMIT 1");
    }

    @DBDMLAssert(order = 3)
    public void dmlAssert(@TargetDBTemplate(dataSource = "customer") JdbcTemplate template)
    {
        template.queryForList("SELECT id FROM your_table LIMIT 1");
    }

    @DBPOSTDDL(order = 1, dataSource = "customer")
    public List<String> postDDL()
    {
        return ImmutableList.of(
                "ALTER TABLE your_table ADD COLUMN your_flag TINYINT DEFAULT 0;"
        );
    }
}
