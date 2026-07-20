-- Local profile 전용 데모 데이터.
-- 애플리케이션을 한 번 실행해 JPA 스키마가 생성된 뒤 coffee_order DB에서 실행한다.

USE coffee_order;

SET @demo_user_id = 9001;
SET @demo_admin_id = 9002;
SET @seed_americano_id = 9101;
SET @seed_latte_id = 9102;
SET @seed_decaf_id = 9103;
SET @seed_non_coffee_id = 9104;
SET @seed_sold_out_id = 9105;
SET @demo_password = '$2a$10$.fNPn0FGcwp1sCkel/KJVutSAYhDdilciz.owW90GEmL66xnRMEFC';
SET @now = NOW(6);

ALTER TABLE outbox_events MODIFY payload TEXT NOT NULL;
ALTER TABLE dead_letter_order_events MODIFY payload TEXT NOT NULL;

DROP TEMPORARY TABLE IF EXISTS local_demo_member_ids;
CREATE TEMPORARY TABLE local_demo_member_ids (
    id BIGINT PRIMARY KEY
);

INSERT INTO local_demo_member_ids (id)
SELECT id
FROM members
WHERE id IN (@demo_user_id, @demo_admin_id)
   OR email IN ('user.local@example.com', 'admin.local@example.com');

DROP TEMPORARY TABLE IF EXISTS local_demo_cart_ids;
CREATE TEMPORARY TABLE local_demo_cart_ids (
    id BIGINT PRIMARY KEY
);

INSERT INTO local_demo_cart_ids (id)
SELECT id
FROM carts
WHERE member_id IN (SELECT id FROM local_demo_member_ids);

DROP TEMPORARY TABLE IF EXISTS local_demo_order_ids;
CREATE TEMPORARY TABLE local_demo_order_ids (
    id BIGINT PRIMARY KEY
);

INSERT INTO local_demo_order_ids (id)
SELECT id
FROM orders
WHERE member_id IN (SELECT id FROM local_demo_member_ids);

DELETE FROM outbox_events
WHERE aggregate_type = 'ORDER'
  AND aggregate_id IN (SELECT id FROM local_demo_order_ids);

DELETE FROM external_order_event_logs
WHERE member_id IN (SELECT id FROM local_demo_member_ids)
   OR order_id IN (SELECT id FROM local_demo_order_ids);

DELETE FROM payments
WHERE member_id IN (SELECT id FROM local_demo_member_ids)
   OR order_id IN (SELECT id FROM local_demo_order_ids);

DELETE FROM point_histories
WHERE member_id IN (SELECT id FROM local_demo_member_ids);

DELETE FROM order_items
WHERE order_id IN (SELECT id FROM local_demo_order_ids);

DELETE FROM orders
WHERE id IN (SELECT id FROM local_demo_order_ids);

DELETE FROM cart_items
WHERE cart_id IN (SELECT id FROM local_demo_cart_ids);

DELETE FROM carts
WHERE id IN (SELECT id FROM local_demo_cart_ids);

DELETE FROM points
WHERE member_id IN (SELECT id FROM local_demo_member_ids);

DELETE FROM members
WHERE id IN (SELECT id FROM local_demo_member_ids);

DELETE FROM menus
WHERE id IN (
        @seed_americano_id,
        @seed_latte_id,
        @seed_decaf_id,
        @seed_non_coffee_id,
        @seed_sold_out_id
    )
   OR name LIKE 'HTTP 테스트%';

INSERT INTO members (
    id,
    email,
    password,
    name,
    role,
    status,
    deleted_at,
    created_at,
    updated_at
) VALUES
    (
        @demo_user_id,
        'user.local@example.com',
        @demo_password,
        '로컬 사용자',
        'USER',
        'ACTIVE',
        NULL,
        @now,
        @now
    ),
    (
        @demo_admin_id,
        'admin.local@example.com',
        @demo_password,
        '로컬 관리자',
        'ADMIN',
        'ACTIVE',
        NULL,
        @now,
        @now
    );

INSERT INTO points (
    id,
    member_id,
    balance,
    version,
    created_at,
    updated_at
) VALUES
    (
        @demo_user_id,
        @demo_user_id,
        50000,
        0,
        @now,
        @now
    ),
    (
        @demo_admin_id,
        @demo_admin_id,
        0,
        0,
        @now,
        @now
    );

INSERT INTO point_histories (
    member_id,
    order_id,
    payment_id,
    type,
    amount,
    balance_after,
    created_at
) VALUES (
    @demo_user_id,
    NULL,
    NULL,
    'CHARGE',
    50000,
    50000,
    @now
);

INSERT INTO menus (
    id,
    name,
    description,
    category,
    price,
    status,
    deleted_at,
    created_at,
    updated_at
) VALUES
    (
        @seed_americano_id,
        '아메리카노',
        '고소한 원두의 기본 아메리카노',
        'COFFEE',
        3000,
        'ON_SALE',
        NULL,
        @now,
        @now
    ),
    (
        @seed_latte_id,
        '카페라떼',
        '우유가 들어간 부드러운 커피',
        'COFFEE',
        4500,
        'ON_SALE',
        NULL,
        @now,
        @now
    ),
    (
        @seed_decaf_id,
        '디카페인 콜드브루',
        '카페인을 줄인 차가운 커피',
        'DECAF',
        5000,
        'ON_SALE',
        NULL,
        @now,
        @now
    ),
    (
        @seed_non_coffee_id,
        '초코라떼',
        '진한 초콜릿 베이스의 논커피 음료',
        'NON_COFFEE',
        4800,
        'ON_SALE',
        NULL,
        @now,
        @now
    ),
    (
        @seed_sold_out_id,
        '크루아상',
        '품절 상태 확인용 베이커리 메뉴',
        'BAKERY',
        3500,
        'SOLD_OUT',
        NULL,
        @now,
        @now
    );

DROP TEMPORARY TABLE IF EXISTS local_demo_order_ids;
DROP TEMPORARY TABLE IF EXISTS local_demo_cart_ids;
DROP TEMPORARY TABLE IF EXISTS local_demo_member_ids;
