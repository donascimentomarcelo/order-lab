CREATE TABLE products (
    product_id VARCHAR(50) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    unit_price NUMERIC(19, 2) NOT NULL,
    CONSTRAINT ck_products_product_id_not_blank CHECK (btrim(product_id) <> ''),
    CONSTRAINT ck_products_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT ck_products_unit_price_non_negative CHECK (unit_price >= 0)
);
