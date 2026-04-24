DROP TABLE IF EXISTS order_sync_log;
DROP TABLE IF EXISTS customer_orders;

CREATE TABLE customer_orders (
    order_id BIGINT PRIMARY KEY,
    status VARCHAR(32) NOT NULL,
    updated_at BIGINT NOT NULL
);

INSERT INTO customer_orders(order_id, status, updated_at) VALUES (101, 'NEW', 1000);
INSERT INTO customer_orders(order_id, status, updated_at) VALUES (102, 'NEW', 2000);
INSERT INTO customer_orders(order_id, status, updated_at) VALUES (103, 'DONE', 3000);
