INSERT INTO product (
    id,
    name,
    price,
    stock
)
VALUES
    (1, '프리미엄 에어프라이어', 129000, 100),
    (2, '홈쇼핑 특가 건강식품 세트', 89000, 200),
    (3, '한정판 무선청소기', 249000, 50),
    (4, '고급 침구 풀세트', 159000, 80),
    (5, '주방 칼 세트', 69000, 150),
    (6, '프리미엄 마사지건', 99000, 120),
    (7, '대용량 무선 가습기', 79000, 90),
    (8, '스마트 체중계', 39000, 300),
    (9, '홈트레이닝 밴드 세트', 29000, 500),
    (10, '프리미엄 전기그릴', 139000, 70),
    (11, '두루마리 휴지 세트', 24000, 30)
    AS incoming
ON DUPLICATE KEY UPDATE
    name = incoming.name,
    price = incoming.price,
    stock = incoming.stock,
    updated_at = CURRENT_TIMESTAMP(6);

INSERT INTO live_broadcast (
    id,
    title,
    product_id,
    started_at
)
VALUES
    (1, '오늘 단 하루 에어프라이어 특가 방송', 1, NOW(6)),
    (2, '건강식품 세트 한정 판매 방송', 2, NOW(6)),
    (3, '무선청소기 품절 임박 방송', 3, NOW(6)),
    (4, '신혼집 침구 풀세트 특가 방송', 4, NOW(6)),
    (5, '주방 필수템 칼 세트 라이브', 5, NOW(6)),
    (6, '퇴근 후 피로 회복 마사지건 특가', 6, NOW(6)),
    (7, '겨울철 필수 대용량 가습기 방송', 7, NOW(6)),
    (8, '건강관리 스마트 체중계 특가', 8, NOW(6)),
    (9, '홈트레이닝 입문 세트 방송', 9, NOW(6)),
    (10, '고기 굽는 날 전기그릴 특가 방송', 10, NOW(6)),
    (11, '잘풀리는 두루마리 휴지 방송', 11, NOW(6))
    AS broadcast
ON DUPLICATE KEY UPDATE
    title = broadcast.title,
    product_id = broadcast.product_id,
    started_at = broadcast.started_at,
    updated_at = CURRENT_TIMESTAMP(6);
