-- recommendation_criteria 는 요청 시점의 근무지 스냅샷을 보관한다. V5 에서 workplace 의
-- address 가 삭제되고 road_address 가 필수가 되었으므로 스냅샷도 원본에 맞춘다.
ALTER TABLE recommendation_criteria
    DROP COLUMN workplace_address,
    MODIFY workplace_road_address VARCHAR(255) NOT NULL;

-- LLM 평가 결과를 담을 자리. 표가 아직 비어 있어 NOT NULL 로 추가해도 안전하다.
ALTER TABLE recommendation_result
    ADD COLUMN display_order        INT  NOT NULL,
    ADD COLUMN commute_minutes      INT  NOT NULL,
    ADD COLUMN total_score          INT  NOT NULL,
    ADD COLUMN sunlight_score       INT  NOT NULL,
    ADD COLUMN quietness_score      INT  NOT NULL,
    ADD COLUMN safety_score         INT  NOT NULL,
    ADD COLUMN infrastructure_score INT  NOT NULL,
    ADD COLUMN commute_score        INT  NOT NULL,
    ADD COLUMN summary              TEXT NOT NULL;

-- 실패 사유를 남겨야 상태 조회가 PENDING 과 FAILED 를 구분해 설명할 수 있다.
ALTER TABLE recommendation
    ADD COLUMN failure_reason VARCHAR(255) NULL;
