# EvoSchema

EvoSchema 是一个阶段化的数据库演进执行器（Pre-DDL / DML / Assert / Post-DDL），用于在一个或多个数据源上以代码方式驱动 Schema 与数据变更。

它聚焦于重大版本发布的工程可控性与安全性：明确的执行顺序、基于开发者补偿 SQL 的有限回滚能力，以及 SQL 约束（仅 DML、DML+查询、仅查询）来降低数据库误操作风险。

## 解决的问题

在微服务架构中，各个服务通常拥有各自独立的数据库 Schema。在重大版本发布过程中，多个服务可能需要在相近的时间窗口内同时演进各自的 Schema 与数据。

这会带来一类典型的工程难题：

- Schema 变更往往要求“发布级别”的一致性
- 数据库彼此独立，缺少跨服务的统一事务上下文
- MySQL 的 DDL 通常会触发隐式提交，天然不利于回滚

EvoSchema 并不试图把这件事解决成一个全局分布式事务平台。

相反，它提供一个可落地的工程模型：

- 用代码定义迁移逻辑
- 将执行拆分为有序的阶段
- 支持多数据源执行
- 通过开发者定义的补偿 SQL 提供有限回滚能力
- 通过 SQL 约束降低误操作风险

## 项目定位

EvoSchema 是：

- 一个非 Web 的 Spring Boot 应用
- 一个单进程迁移执行器
- 一个基于注解驱动的数据库演进框架
- 适用于受控的发布流水线或内部迁移执行工具

EvoSchema 不是：

- 一个跨微服务的统一编排控制面
- 一个分布式强一致发布系统
- 一个对所有 JDBC 入口都完全封闭的 SQL 沙箱
- 一个以纯 SQL 文件为主的 Flyway/Liquibase 通用替代品

## 核心执行模型

框架每次运行只执行一个迁移组件，并按如下阶段顺序执行：

```text
Pre-DDL -> DML / DBScript -> DMLAssert -> Post-DDL
```

### Pre-DDL

使用 `@DBPREDDL` 定义前置结构性变更。

- 常用于 `CREATE`、`ALTER`、`RENAME`、权限变更或兼容性准备
- 每个方法返回两条 SQL：
  - 正向 SQL
  - 补偿 SQL
- 如果后续阶段失败，EvoSchema 会尝试按逆序执行补偿 SQL

### DML

使用 `@DBDML` 定义标准数据变更。

- 返回 SQL 语句列表
- 当前实现仅允许：
  - `INSERT`
  - `UPDATE`
  - `DELETE`
- `REPLACE`、`MERGE`、`CALL` 以及任何 DDL 都会被该注解拒绝

### DBScript

使用 `@DBScript` 在 Java 代码中编写更复杂的数据迁移逻辑。

- 通过 `@TargetDBTemplate` 注入受限的 `JdbcTemplate` 参数
- 允许常见的字符串 SQL 入口：
  - DML：`INSERT`、`UPDATE`、`DELETE`、`REPLACE`、`MERGE`、`CALL`
  - 查询：`SELECT`、`SHOW`、`EXPLAIN`、`DESCRIBE`、`DESC`
- 通过受限模板校验并拒绝非法 DDL 操作

### DMLAssert

使用 `@DBDMLAssert` 定义发布时的数据断言。

- 用于 DML / DBScript 之后的一致性校验
- 注入仅查询的 `JdbcTemplate`
- 允许：
  - `SELECT`
  - `SHOW`
  - `EXPLAIN`
  - `DESCRIBE`
  - `DESC`
- 当断言逻辑失败时抛出异常

### Post-DDL

使用 `@DBPOSTDDL` 定义清理或最终结构收口的变更。

- 通常包含不可逆操作，例如列清理或最终形态收敛
- 仅返回正向 SQL
- 不提供补偿 SQL

## 事务与回滚模型

EvoSchema 支持两种事务模式：

- 单数据源：本地事务
- 多数据源：Atomikos JTA/XA 事务

重要限制：

- 回滚能力是有限且与阶段相关的
- `Pre-DDL` 的回滚依赖开发者提供的补偿 SQL
- `Post-DDL` 默认不提供自动回滚能力
- 原生数据库层面的 DDL 回滚仍受底层数据库能力约束

## 当前技术栈

- Java 17
- Spring Boot 3.5.x
- 当前仅支持 MySQL；未来版本计划支持 PostgreSQL 等主流数据库
- XA 支持当前仅限 MySQL；更广泛的数据库支持将逐步扩展

## 安装

### 1. 引入项目

使用 Maven 构建：

```bash
mvn clean package
```

### 2. 准备运行时配置

应用会从如下位置加载数据源配置：

```text
classpath:${profiles.prefixpath}/db.properties
```

默认运行时配置位于 [`src/main/resources/application.properties`](src/main/resources/application.properties)。

相关配置项示例：

```properties
spring.profiles.active=dev
logging.config=classpath:${profiles.prefixpath}/log4j2.xml
spring.jta.atomikos.properties.max-timeout=3000000
spring.jta.atomikos.properties.default-jta-timeout=3000000
```

## 数据源配置

在 `db.properties` 中按如下格式配置数据源：

```properties
evoschema.datasource.customer.driverClassName=com.mysql.cj.jdbc.Driver
evoschema.datasource.customer.url=jdbc:mysql://127.0.0.1:3306/customer_db?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC
evoschema.datasource.customer.username=root
evoschema.datasource.customer.password=123456

evoschema.datasource.finance.driverClassName=com.mysql.cj.jdbc.Driver
evoschema.datasource.finance.url=jdbc:mysql://127.0.0.1:3306/finance_db?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC
evoschema.datasource.finance.username=root
evoschema.datasource.finance.password=123456
```

### 命名规则

对于数据源 key `customer`，EvoSchema 会自动注册：

- `customerDataSource`
- `customerJdbcTemplate`

对于数据源 key `finance`，EvoSchema 会自动注册：

- `financeDataSource`
- `financeJdbcTemplate`

## 如何编写迁移组件

使用 [`DBScriptTemplate.java`](src/main/java/io/github/evoschema/dbscript/DBScriptTemplate.java) 作为脚手架。

### 步骤 1：复制模板

创建一个新类并重命名，例如：

```java
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
import org.springframework.stereotype.Component;

@Component("release_20260401")
public class Release20260401Migration
{
    @DBPREDDL(order = 1, dataSource = "customer")
    public List<String> preDDL()
    {
        return ImmutableList.of(
                "ALTER TABLE customer_orders ADD COLUMN archived TINYINT DEFAULT 0;",
                "ALTER TABLE customer_orders DROP COLUMN archived;"
        );
    }

    @DBDML(order = 1, dataSource = "customer")
    public List<String> dml()
    {
        return ImmutableList.of(
                "UPDATE customer_orders SET archived = 0 WHERE archived IS NULL"
        );
    }

    @DBScript(order = 2)
    public void script(
            @TargetDBTemplate(dataSource = "customer") JdbcTemplate customerTemplate,
            @TargetDBTemplate(dataSource = "finance") JdbcTemplate financeTemplate)
    {
        Long count = customerTemplate.queryForObject(
                "SELECT COUNT(1) FROM customer_orders WHERE archived = 0",
                Long.class
        );
        if (count != null && count > 0) {
            financeTemplate.update(
                    "UPDATE finance_orders SET sync_status = 'READY' WHERE sync_status IS NULL"
            );
        }
    }

    @DBDMLAssert(order = 3)
    public void dmlAssert(@TargetDBTemplate(dataSource = "customer") JdbcTemplate customerTemplate)
    {
        Long count = customerTemplate.queryForObject(
                "SELECT COUNT(1) FROM customer_orders WHERE archived IS NULL",
                Long.class
        );
        if (count != null && count > 0) {
            throw new IllegalStateException("archived column still contains null values");
        }
    }

    @DBPOSTDDL(order = 1, dataSource = "customer")
    public List<String> postDDL()
    {
        return ImmutableList.of(
                "ALTER TABLE customer_orders MODIFY COLUMN archived TINYINT NOT NULL DEFAULT 0"
        );
    }
}
```

### 步骤 2：添加 `@Component`

组件名是运行时入口 id。

例如：

```java
@Component("release_20260401")
```

### 步骤 3：按名称选择数据源

通过 `dataSource = "customer"` 或通过如下方式注入模板：

```java
@TargetDBTemplate(dataSource = "customer")
```

数据源名称必须与 `db.properties` 中的 key 一致。

## 注解参考

### `@DBPREDDL`

- 阶段：DML 之前
- 返回：严格 2 条 SQL
- 目的：正向 DDL + 补偿 DDL

示例：

```java
@DBPREDDL(order = 1, dataSource = "customer")
public List<String> preDDL()
{
    return ImmutableList.of(
            "ALTER TABLE customer_orders ADD COLUMN ext_id BIGINT DEFAULT NULL",
            "ALTER TABLE customer_orders DROP COLUMN ext_id"
    );
}
```

### `@DBDML`

- 阶段：DML
- 返回：标准 DML SQL 列表
- 允许的首关键字：
  - `INSERT`
  - `UPDATE`
  - `DELETE`

示例：

```java
@DBDML(order = 1, dataSource = "customer")
public List<String> fixData()
{
    return ImmutableList.of(
            "UPDATE customer_orders SET status = 'READY' WHERE status = 'NEW'"
    );
}
```

### `@DBScript`

- 阶段：DML
- 方式：在 Java 中使用受限 `JdbcTemplate` 编写逻辑
- 适用场景：仅靠 SQL 不足以表达的复杂处理

示例：

```java
@DBScript(order = 2)
public void sync(
        @TargetDBTemplate(dataSource = "customer") JdbcTemplate customerTemplate,
        @TargetDBTemplate(dataSource = "finance") JdbcTemplate financeTemplate)
{
    List<Long> orderIds = customerTemplate.queryForList(
            "SELECT order_id FROM customer_orders WHERE status = 'READY'",
            Long.class
    );
    for (Long orderId : orderIds) {
        financeTemplate.update(
                "UPDATE finance_orders SET sync_status = 'SYNCED' WHERE order_id = ?",
                orderId
        );
    }
}
```

### `@DBDMLAssert`

- 阶段：DML / DBScript 之后
- 方式：仅查询的断言逻辑
- 目的：当数据状态不满足预期时快速失败

示例：

```java
@DBDMLAssert(order = 3)
public void assertResult(@TargetDBTemplate(dataSource = "customer") JdbcTemplate template)
{
    Long count = template.queryForObject(
            "SELECT COUNT(1) FROM customer_orders WHERE status = 'READY'",
            Long.class
    );
    if (count == null || count == 0L) {
        throw new IllegalStateException("no READY records found");
    }
}
```

### `@DBPOSTDDL`

- 阶段：最终结构清理/收口
- 返回：仅正向 SQL

示例：

```java
@DBPOSTDDL(order = 1, dataSource = "customer")
public List<String> postDDL()
{
    return ImmutableList.of(
            "ALTER TABLE customer_orders DROP COLUMN old_status"
    );
}
```

## 如何运行

通过组件名运行：

```bash
mvn -q -DskipTests compile
mvn -q exec:java -Dexec.args="release_20260401"
```

或直接调用启动类：

```java
Starter.main(new String[] { "release_20260401" });
```

如果不传参数，入口组件默认为当前日期（`yyyyMMdd`）。

## SQL 约束

当前 SQL 限制规则如下：

### `@DBDML`

- 允许：
  - `INSERT`
  - `UPDATE`
  - `DELETE`
- 拒绝：
  - `REPLACE`
  - `MERGE`
  - `CALL`
  - DDL

### `@DBScript`

- 允许：
  - `INSERT`
  - `UPDATE`
  - `DELETE`
  - `REPLACE`
  - `MERGE`
  - `CALL`
  - `SELECT`
  - `SHOW`
  - `EXPLAIN`
  - `DESCRIBE`
  - `DESC`
- 拒绝：
  - DDL

### `@DBDMLAssert`

- 允许：
  - `SELECT`
  - `SHOW`
  - `EXPLAIN`
  - `DESCRIBE`
  - `DESC`
- 拒绝：
  - DML
  - DDL

说明：

- 当前 guard 主要覆盖常见的字符串 SQL `JdbcTemplate` 调用入口
- 它并不是对所有 JDBC API 的完全沙箱封闭

## 推荐的发布模式

对于多步骤变更，推荐采用如下模式：

1. 使用 `Pre-DDL` 添加兼容结构
2. 使用 `DBDML` / `DBScript` 进行回填或数据转换
3. 使用 `DBDMLAssert` 进行正确性校验
4. 使用 `Post-DDL` 完成最终结构收口

这个模式可以降低发布风险，也更容易理解与处理失败场景。

## 测试

仓库包含集成测试与单元测试。

当前测试覆盖重点：

- 所有迁移阶段的主流程（happy path）
- 当后续 `Pre-DDL` 失败时，已完成 `Pre-DDL` 的回滚
- DML / DBScript 失败时的事务回滚 + `Pre-DDL` 补偿回滚
- SQL guard 行为
- 受限 `JdbcTemplate` 行为
- 手工注册数据源 Bean 的单例生命周期（销毁回调）

运行所有测试：

```bash
mvn -q test
```

代表性测试类：

- [TutorialOrderSyncDemoTest](src/test/java/io/github/evoschema/TutorialOrderSyncDemoTest.java)
- [RollbackOnPreDdlFailureDemoTest](src/test/java/io/github/evoschema/RollbackOnPreDdlFailureDemoTest.java)
- [RollbackOnDmlFailureDemoTest](src/test/java/io/github/evoschema/RollbackOnDmlFailureDemoTest.java)
- [SqlStatementGuardTest](src/test/java/io/github/evoschema/SqlStatementGuardTest.java)
- [GuardedJdbcTemplateTest](src/test/java/io/github/evoschema/GuardedJdbcTemplateTest.java)
- [SpringBeanFactoryTest](src/test/java/io/github/evoschema/SpringBeanFactoryTest.java)

## 限制

- EvoSchema 每次进程运行只协调执行一个迁移组件
- 回滚能力受框架能力与补偿 SQL 的可表达性约束
- `Post-DDL` 设计上不可回滚

## 许可证

本项目采用 MIT License 开源。

完整文本见 [`LICENSE`](LICENSE)。
- 它不是一个跨微服务的中心化编排器
- 它无法保证独立服务之间的发布原子性

## 适用场景

当你需要：

- 可 code review 的数据库演进逻辑
- 明确的阶段化执行顺序
- 可控的多数据源迁移执行
- 发布时的数据断言
- 相比完全手工迁移更可重复的工程化方案

可以使用 EvoSchema。
