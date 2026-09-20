# 내 정보 및 매물 즐겨찾기 API 명세

이 문서는 대화에서 승인한 동작을 공유용으로 정리한 것이다. 구현에 맞춰 기대값을 변경하지 않는다.

## 공통

- 모든 API는 Access Token의 Bearer 인증이 필요하다. 사용자 ID는 토큰에서 결정한다.
- 인증 실패는 401 `INVALID_ACCESS_TOKEN`이다.
- 잘못된 입력은 400 `INVALID_REQUEST`이다.
- 오류는 `application/problem+json`으로 반환하며 type, title, status, detail, instance, code, timestamp를 포함한다.
- UUID 경로 및 요청 값이 올바르지 않으면 400이다.

## 내 정보

### GET /api/me

200: id, provider(`GOOGLE`), email, nickname, role, profileCompleted, createdAt.
닉네임을 지정하기 전 nickname은 null, profileCompleted는 false이다.

### PATCH /api/me

요청: `{"nickname":"새닉네임"}`. 앞뒤 공백을 제거한 뒤 길이는 2~15자이다.
닉네임은 사용자 간 중복을 허용하지 않는다. 누락, null, 길이 위반은 400이다.
다른 사용자의 닉네임이면 409 `NICKNAME_ALREADY_EXISTS`; 자기 닉네임을 다시 저장하는 것은 성공한다.
200 응답은 내 정보 조회와 같다. 이메일·권한·다른 사용자의 정보는 변경하지 않는다.

## 즐겨찾기

### GET /api/me/favorites

page 기본 0, 0 이상의 정수. size 기본 20, 1~100의 정수.
자신의 즐겨찾기만 최신 등록 순으로 반환한다.
200: content, page, size, totalElements, totalPages, last.
빈 목록은 content=[], totalElements=0, totalPages=0, last=true이다.
항목: favoriteId, createdAt, property.
property 요약: id, name, address, roadAddress, propertyType, leaseType, deposit, monthlyRent, exclusiveArea, floor, buildYear.
leaseType: `JEONSE`(전세), `MONTHLY`(월세). 보증금·월세 단위는 만원이다.

### GET /api/me/favorites/{propertyId}

자신이 즐겨찾기한 매물만 조회한다. 없으면 404 `FAVORITE_NOT_FOUND`이다.
200: favoriteId, createdAt, property.
property는 요약 필드에 sggCode, umdName, latitude, longitude, totalFloors, direction, description을 추가한다.

### POST /api/me/favorites

요청: `{"propertyId":"매물 UUID"}`.
201: favoriteId, propertyId, createdAt.
없는 매물은 404 `PROPERTY_NOT_FOUND`; 이미 등록한 매물은 409 `FAVORITE_ALREADY_EXISTS`이다.
동시 중복 등록도 한 건만 저장하며 나머지 요청은 409이다.

### DELETE /api/me/favorites/{propertyId}

자신의 즐겨찾기만 삭제한다. 존재 여부와 무관하게 204이며 응답 본문은 없다.
다른 사용자의 즐겨찾기는 변경하지 않는다.
