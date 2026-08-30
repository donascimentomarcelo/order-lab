INSERT INTO products (product_id, name, unit_price)
VALUES
    ('NOTEBOOK-001', 'Notebook', 3500.00),
    ('MONITOR-001', 'Monitor', 1200.00),
    ('KEYBOARD-001', 'Teclado', 250.00);

INSERT INTO products (product_id, name, unit_price)
SELECT
    'PRODUCT-' || lpad(sequence_number::text, 7, '0'),
    (ARRAY[
        'Notebook',
        'Monitor',
        'Teclado',
        'Mouse',
        'Headset',
        'Webcam',
        'Impressora',
        'Scanner',
        'Roteador',
        'Switch',
        'SSD',
        'Memoria RAM',
        'Processador',
        'Placa de Video',
        'Placa Mae',
        'Fonte',
        'Gabinete',
        'Cadeira',
        'Mesa',
        'Smartphone'
    ])[((sequence_number - 1) % 20) + 1]
        || ' ' || lpad(sequence_number::text, 7, '0'),
    (((sequence_number::bigint * 7919) % 999900) + 100)::numeric / 100
FROM generate_series(1, 999997) AS generated_products(sequence_number);
