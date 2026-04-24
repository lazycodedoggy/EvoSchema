DROP TABLE IF EXISTS dml_rollback_audit;
DROP TABLE IF EXISTS dml_rollback_orders;

CREATE TABLE dml_rollback_orders (
    order_id BIGINT PRIMARY KEY,
    status VARCHAR(32) NOT NULL
);

INSERT INTO dml_rollback_orders(order_id, status) VALUES (201, 'NEW');
