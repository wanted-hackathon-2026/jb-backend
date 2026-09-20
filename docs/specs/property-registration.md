# 관리자 매물 등록 API 명세

## POST /api/properties

인증: `Authorization: Bearer <accessToken>`.
JWT subject로 사용자를 조회하고 **DB의 현재 `UserRole.ADMIN`**만 허용한다.
JWT의 `role` 클레임은 권한 판단에 사용하지 않는다. 역할 변경은 다음 요청부터 반영된다.
관리자 지정은 기존 운영 절차를 사용하며 권한 승격 API는 제공하지 않는다.

## 요청

```json
{
  "name": "수원 원룸",
  "address": "경기 수원시 팔달구 우만동 228",
  "roadAddress": "경기 수원시 팔달구 월드컵로 205",
  "sggCode": "41115",
  "umdName": "우만동",
  "propertyType": "원룸",
  "leaseType": "MONTHLY",
  "deposit": 1000,
  "monthlyRent": 50,
  "exclusiveArea": 23.50,
  "supplyArea": 33.00,
  "floor": 3,
  "bathroomCount": 1,
  "totalFloors": 4,
  "buildYear": 2014,
  "direction": "남향",
  "description": "매물 설명"
}
```

보증금·월세 단위는 **만원**, 전용면적 단위는 **㎡**다.
정수 필드는 JSON 정수로 보내야 하며, 소수나 숫자 문자열을 정수로 자동 변환하지 않는다.

| 필드 | 필수 | 검증 |
| --- | --- | --- |
| name | 예 | 공백 불가, 최대 100자 |
| address | 예 | 지번주소, 공백 불가, 최대 255자 |
| roadAddress | 예 | 도로명주소, 공백 불가, 최대 255자 |
| sggCode | 예 | 시군구 코드, ASCII 숫자 5자리 문자열 (`[0-9]{5}`) |
| umdName | 예 | 읍면동명, 공백 불가, 최대 50자 |
| propertyType | 예 | 공백 불가, 최대 20자, 별도 허용값 목록 없음 |
| leaseType | 예 | `JEONSE`(전세) 또는 `MONTHLY`(월세), 대문자 문자열만 허용 |
| deposit / monthlyRent | 예 | 0 이상, Java int 범위의 정수 |
| exclusiveArea | 아니오 | 양수, 정수부 최대 6자리·소수부 최대 2자리, 단위 ㎡ |
| supplyArea | 아니오 | 양수, 정수부 최대 6자리·소수부 최대 2자리, 단위 ㎡ (2026-09-20 추가) |
| floor | 아니오 | Java int 범위의 정수, 지하층을 위해 음수 허용 |
| bathroomCount | 아니오 | Java int 범위의 양의 정수 (2026-09-20 추가) |
| totalFloors / buildYear | 아니오 | Java int 범위의 양의 정수 |
| direction | 아니오 | 공백 불가, 최대 10자, 별도 허용값 목록 없음 |
| description | 아니오 | UTF-8 TEXT 저장 한도를 안전하게 지키도록 최대 16,383자 |

전세(`JEONSE`)는 `monthlyRent = 0`, 월세(`MONTHLY`)는 `monthlyRent > 0`이어야 한다.
보증금은 두 유형 모두 0 이상이며, 매매 유형은 지원하지 않는다.
유형이 누락되거나 허용되지 않은 값, 숫자 또는 월세와 맞지 않는 조합이면 `400 INVALID_REQUEST`다.
이 검증은 geocoding과 저장 전에 수행한다.

선택 필드는 생략하거나 null로 보낼 수 있다. 지역 코드·읍면동명은 관리자가 제공하며
이번 API에서 주소와의 일치 여부는 검증하지 않는다.
카카오 우편번호 팝업을 사용하는 프론트에서는 `sggCode`를 `data.sigunguCode`로 자동 채우고,
`umdName`은 `data.bname1 || data.bname`으로 전달한다. 사용자가 지역 코드를 직접 입력하게 하지 않는다.
현재 API에는 우편번호 저장 필드가 없으며, 카카오의 `data.zonecode`를 `sggCode` 대신 보내면 안 된다.
ID·좌표·생성/수정 시각은 서버가 생성하며 요청 DTO에 포함하지 않는다.
좌표는 공통 geocoding 클라이언트가 도로명주소로 조회한 WGS84 위도·경도를 사용한다.
외부 API 호출이 성공한 뒤 저장 트랜잭션을 시작한다.

## 성공 응답

`201 Created`, JSON 객체.

요청의 매물 필드 전체와 서버 생성 `id`(UUID), `latitude`, `longitude`,
`createdAt`, `updatedAt`을 반환한다. 시각은 기존 엔티티와 동일한 LocalDateTime 문자열이다.
좌표 이름은 기존 즐겨찾기 상세 응답과 일치한다.
등록 응답 및 즐겨찾기 목록·상세의 property 객체에 `leaseType`을 포함한다.

중복 등록 기준은 없으며 같은 내용을 다시 요청하면 다른 UUID의 매물로 저장한다.
등록된 매물은 기존 즐겨찾기 API에서 바로 사용할 수 있다.

## 오류 응답

기존 공통 오류 형식과 `Content-Type: application/problem+json`을 사용한다.
필드: `type`, `title`, `status`, `detail`, `instance`, `code`, `timestamp`.

| 상태 | code | 조건 |
| --- | --- | --- |
| 401 | INVALID_ACCESS_TOKEN | 미인증, 만료·변조·잘못된 종류의 토큰 |
| 403 | FORBIDDEN | DB의 역할이 ADMIN이 아님, 사용자가 삭제됨, JWT subject가 유효한 사용자 UUID가 아님 |
| 400 | INVALID_REQUEST | 요청 JSON 또는 필드 검증 실패 |
| 400 | ADDRESS_NOT_GEOCODABLE | 도로명주소의 좌표를 찾을 수 없음 |
| 502 | GEOCODING_UNAVAILABLE | 외부 좌표 변환 서비스 실패 |

권한 검사는 컨트롤러와 요청 필드 검증보다 먼저 수행한다. 허용되지 않은 사용자는
VWorld를 호출하거나 매물을 저장하지 않는다. 좌표 변환 실패 시에도 저장하지 않는다.

## 테스트와 배포

등록 계약 테스트는 Testcontainers MySQL과 geocoding stub을 사용한다.
실제 VWorld 키는 필요하지 않으며 `vworld.api-key`는 테스트 속성에 더미 값으로 명시한다.

```bash
./gradlew test --tests '*PropertyApiContractTest'
./gradlew clean build
```

`V6__add_property_lease_type.sql`이 기존 property 테이블에 `lease_type`을 추가한다.
기존 행은 월세가 0이면 JEONSE, 양수면 MONTHLY로 초기 분류하며 UUID·가격·기존 시각은 유지한다.
과거에는 거래 구분이 없었으므로 기존 월세 0인 행이 실제 전세인지 확인해야 한다.
새 등록에서는 유형을 추론하거나 기본값을 넣지 않는다.
DB에도 NOT NULL 및 유형·월세 조합 CHECK 제약을 적용한다.
이미 적용된 V1~V5 파일은 수정하지 않는다. 앱 재시작 시 Flyway가 V6를 적용한다.
운영 환경에서는 거점 기능에 사용하는 `VWORLD_API_KEY`를 그대로 재사용한다.
이번 변경에는 매물 조회·수정·삭제, 이미지 등록, 관리자 지정 API가 포함되지 않는다.
