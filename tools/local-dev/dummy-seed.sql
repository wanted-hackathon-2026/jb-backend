-- 로컬 개발용 매물 데이터. Flyway V6까지 적용한 DB에서 수동 실행한다.
-- 사용자·토큰·Flyway 이력은 포함하지 않는다.
-- 기존 UUID의 매물은 덮어쓰지 않고, 없는 매물만 추가한다.
-- 매물을 더 추가할 때는 VALUES에 새 UUID의 행을 쉼표로 이어 붙인다.

SET NAMES utf8mb4;
START TRANSACTION;

INSERT INTO property (
    id, name, address, road_address, sgg_code, umd_name, lat, lng,
    property_type, lease_type, deposit, monthly_rent, exclusive_area,
    floor, total_floors, build_year, direction, description, created_at, updated_at
) VALUES (
    X'C53291FDC1634D43B7C93FE7BCD7AE5A',
    '수원 원룸',
    '경기도 수원시 영통구 원천동 29-29',
    '경기도 수원시 영통구 월드컵로193번길 51',
    '41117',
    '원천동',
    37.27692448634101,
    127.04493291276478,
    '원룸',
    'MONTHLY',
    1000,
    50,
    23.50,
    3,
    4,
    2014,
    '남향',
    '수원 원룸',
    '2026-09-17 00:20:10.489692',
    '2026-09-17 00:20:10.489692'
)
ON DUPLICATE KEY UPDATE id = property.id;

COMMIT;
