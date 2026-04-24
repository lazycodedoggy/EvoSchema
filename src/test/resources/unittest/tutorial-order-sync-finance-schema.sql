DROP TABLE IF EXISTS finance_orders;

CREATE TABLE finance_orders (
    order_id BIGINT PRIMARY KEY,
    sync_status VARCHAR(32) NOT NULL
);

INSERT INTO finance_orders(order_id, sync_status) VALUES (101, 'PENDING');
INSERT INTO finance_orders(order_id, sync_status) VALUES (102, 'PENDING');
INSERT INTO finance_orders(order_id, sync_status) VALUES (103, 'PENDING');
