DROP TABLE IF EXISTS dml_rollback_finance_orders;

CREATE TABLE dml_rollback_finance_orders (
    order_id BIGINT PRIMARY KEY,
    sync_status VARCHAR(32) NOT NULL
);

INSERT INTO dml_rollback_finance_orders(order_id, sync_status) VALUES (201, 'PENDING');
