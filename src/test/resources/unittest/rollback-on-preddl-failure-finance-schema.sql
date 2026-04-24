DROP TABLE IF EXISTS finance_orders_that_do_not_exist;
DROP TABLE IF EXISTS preddl_finance_seed;

CREATE TABLE preddl_finance_seed (
    id BIGINT PRIMARY KEY,
    status VARCHAR(32) NOT NULL
);

INSERT INTO preddl_finance_seed(id, status) VALUES (1, 'INIT');
