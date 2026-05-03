# Getting Started With TutorialOrderSyncDemo

This guide walks through the `tutorial_order_sync` migration included in the test suite and shows how EvoSchema executes a real multi-datasource migration across `customer` and `finance`.

## What You Will Learn

- how to define a migration component with `@DBPREDDL`, `@DBDML`, `@DBScript`, `@DBDMLAssert`, and `@DBPOSTDDL`
- how EvoSchema coordinates schema and data changes across multiple datasources
- how to run a migration component by name
- how to verify the final state after execution

## Demo Files

- migration component: [`TutorialOrderSyncDemo.java`](../src/test/java/io/github/evoschema/dbscript/TutorialOrderSyncDemo.java)
- integration test: [`TutorialOrderSyncDemoTest.java`](../src/test/java/io/github/evoschema/TutorialOrderSyncDemoTest.java)
- initial customer schema: [`tutorial-order-sync-customer-schema.sql`](../src/test/resources/unittest/tutorial-order-sync-customer-schema.sql)
- initial finance schema: [`tutorial-order-sync-finance-schema.sql`](../src/test/resources/unittest/tutorial-order-sync-finance-schema.sql)
- test datasource config: [`unittest/db.properties`](../src/test/resources/unittest/db.properties)

## Scenario

The demo simulates a release where:

- the `customer` datasource owns the order state
- the `finance` datasource owns the downstream sync status
- the migration marks pending customer orders as `READY`
- the migration syncs matching finance orders to `SYNCED`
- the migration records an audit trail in `order_sync_log`

## Step 1. Prepare the Databases

The demo test expects two MySQL databases:

- `customer_db`
- `finance_db`

The default unittest configuration is:

```properties
evoschema.datasource.customer.url=jdbc:mysql://127.0.0.1:3306/customer_db?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC
evoschema.datasource.customer.username=root
evoschema.datasource.customer.password=123456

evoschema.datasource.finance.url=jdbc:mysql://127.0.0.1:3306/finance_db?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC
evoschema.datasource.finance.username=root
evoschema.datasource.finance.password=123456
```

Create both databases before running the demo:

```sql
CREATE DATABASE IF NOT EXISTS customer_db;
CREATE DATABASE IF NOT EXISTS finance_db;
```

## Step 2. Understand the Initial State

Before the migration runs, the test initializes the schemas from SQL files:

- `customer_orders` starts with two `NEW` orders and one `DONE` order
- `finance_orders` starts with matching rows in `PENDING` status
- `order_sync_log` does not exist yet
- `finance_orders.sync_note` does not exist yet

## Step 3. Review the Migration Phases

The demo component `tutorial_order_sync` executes the following phases:

### Pre-DDL

```java
@DBPREDDL(order = 1, dataSource = "customer")
public List<String> prepareTutorialTable()

@DBPREDDL(order = 2, dataSource = "finance")
public List<String> prepareFinanceColumn()
```

These methods:

- create the `order_sync_log` table on `customer`
- add the `sync_note` column on `finance`
- provide compensation SQL for rollback if a later phase fails

### DML

```java
@DBDML(order = 1, dataSource = "customer")
public List<String> markOrdersReady()
```

This phase updates customer orders from `NEW` to `READY`.

### DBScript

```java
@DBScript(order = 2)
public void syncFinanceOrders(...)
```

This phase:

- queries all `READY` order ids from `customer`
- updates matching `finance_orders` rows to `SYNCED`
- inserts sync records into `order_sync_log`

### DMLAssert

```java
@DBDMLAssert(order = 3)
public void assertMigrationResult(...)
```

This phase validates that:

- the number of `READY` orders matches the number of `SYNCED` finance rows
- the sync log contains the same number of records

### Post-DDL

```java
@DBPOSTDDL(order = 1, dataSource = "finance")
public List<String> cleanupFinanceColumn()
```

This phase finalizes the `sync_note` column by setting its default value to `DONE`.

## Step 4. Run the Demo

Compile the project:

```bash
mvn -q -DskipTests compile
```

Run the migration by component name:

```bash
mvn -q exec:java -Dexec.args="tutorial_order_sync"
```

You can also invoke the starter directly:

```java
Starter.main(new String[] { "tutorial_order_sync" });
```

## Step 5. Verify the Result

After a successful run, the integration test expects:

- `customer_orders` has `2` rows with status `READY`
- `order_sync_log` has `2` rows with status `SYNCED`
- `finance_orders` has `2` rows with status `SYNCED`
- `finance_orders.sync_note` exists and its default value is `DONE`

The assertions are implemented in [`TutorialOrderSyncDemoTest.java`](../src/test/java/io/github/evoschema/TutorialOrderSyncDemoTest.java).

## Run the Integration Test

If you want to validate the entire flow automatically:

```bash
mvn -q -Dtest=TutorialOrderSyncDemoTest test
```

## Why This Demo Matters

`TutorialOrderSyncDemo` is a compact example of EvoSchema's core value:

- code-reviewed migration logic instead of ad-hoc SQL execution
- explicit phase ordering for safer release rollouts
- multi-datasource coordination with transactional DML handling
- developer-defined rollback for Pre-DDL changes
- assertion-driven release verification before final cleanup
