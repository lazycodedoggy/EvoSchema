DROP TABLE IF EXISTS preddl_rollback_marker;
DROP TABLE IF EXISTS preddl_base_seed;

CREATE TABLE preddl_base_seed (
    id BIGINT PRIMARY KEY,
    status VARCHAR(32) NOT NULL
);

INSERT INTO preddl_base_seed(id, status) VALUES (1, 'INIT');
