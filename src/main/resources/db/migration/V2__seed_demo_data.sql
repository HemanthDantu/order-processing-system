INSERT INTO users (
    id,
    username,
    password_hash,
    role,
    created_at,
    updated_at
) VALUES
(
    '11111111-1111-1111-1111-111111111111',
    'customer1',
    '$2a$10$CZVHZvssSBmSBqDeqae9EuabYjq/x1xTHXkNJI6Zc3alG6nx7DVC.',
    'CUSTOMER',
    TIMESTAMPTZ '2026-01-01 00:00:00+00',
    TIMESTAMPTZ '2026-01-01 00:00:00+00'
),
(
    '22222222-2222-2222-2222-222222222222',
    'admin1',
    '$2a$10$CZVHZvssSBmSBqDeqae9EuabYjq/x1xTHXkNJI6Zc3alG6nx7DVC.',
    'ADMIN',
    TIMESTAMPTZ '2026-01-01 00:00:00+00',
    TIMESTAMPTZ '2026-01-01 00:00:00+00'
);

INSERT INTO orders (
    id,
    customer_id,
    status,
    currency,
    total_amount,
    version,
    created_at,
    updated_at
) VALUES (
    '33333333-3333-3333-3333-333333333333',
    '11111111-1111-1111-1111-111111111111',
    'PENDING',
    'USD',
    99.98,
    0,
    TIMESTAMPTZ '2026-01-01 00:05:00+00',
    TIMESTAMPTZ '2026-01-01 00:05:00+00'
);

INSERT INTO order_items (
    id,
    order_id,
    product_id,
    product_name,
    quantity,
    unit_price,
    line_total
) VALUES (
    '44444444-4444-4444-4444-444444444444',
    '33333333-3333-3333-3333-333333333333',
    'prod-1',
    'Keyboard',
    2,
    49.99,
    99.98
);

INSERT INTO order_status_history (
    id,
    order_id,
    from_status,
    to_status,
    changed_by,
    changed_by_type,
    changed_at,
    reason
) VALUES (
    '55555555-5555-5555-5555-555555555555',
    '33333333-3333-3333-3333-333333333333',
    NULL,
    'PENDING',
    '11111111-1111-1111-1111-111111111111',
    'CUSTOMER',
    TIMESTAMPTZ '2026-01-01 00:05:00+00',
    'Seed demo order'
);
