# 매물 사진 등록 명세

## 목적

관리자가 등록된 매물에 사진을 추가한다. 사진 파일은 애플리케이션 볼륨에 저장하고,
DB에는 매물과 사진을 연결하는 메타데이터만 저장한다.

## API

### POST `/api/properties/{propertyId}/images`

- 관리자만 호출할 수 있다.
- `multipart/form-data`의 `files` 필드로 사진을 전송한다.
- 한 번에 한 장 이상 업로드해야 한다.
- 한 매물에 저장할 수 있는 사진은 기존 사진을 포함해 최대 10장이다.
- 파일 한 장의 최대 크기는 10MB다.
- JPEG, PNG, WEBP만 허용한다.
- 확장자와 요청 Content-Type을 신뢰하지 않고 파일 시그니처로 형식을 확인한다.
- 전송 순서대로 `displayOrder`를 부여하며, 기존 사진이 있으면 마지막 순서 뒤에 추가한다.
- 첫 번째 사진(`displayOrder = 0`)을 대표 사진으로 사용한다.

성공 시 `201 Created`와 저장된 사진 목록을 반환한다.

```json
{
  "propertyId": "매물 UUID",
  "images": [
    {
      "id": "사진 UUID",
      "url": "/api/property-images/{propertyId}/{storedFilename}",
      "displayOrder": 0
    }
  ]
}
```

### PUT `/api/properties/{propertyId}/images/{imageId}`

- 관리자만 호출할 수 있다.
- `multipart/form-data`의 `file` 필드로 새 사진 한 장을 전송한다.
- 사진 ID와 `displayOrder`는 유지하고 저장 파일만 교체한다.
- 성공 시 `200 OK`와 교체된 사진 객체를 반환한다.
- DB 반영에 실패하면 새로 저장한 파일을 삭제하고 기존 사진을 유지한다.

### DELETE `/api/properties/{propertyId}/images/{imageId}`

- 관리자만 호출할 수 있다.
- 성공 시 `204 No Content`를 반환한다.
- 삭제한 사진보다 뒤에 있던 사진의 `displayOrder`를 1씩 당긴다.
- 따라서 대표 사진을 삭제하면 다음 사진이 대표 사진이 된다.

## 오류

- 인증 없음 또는 잘못된 토큰: `401 Unauthorized`
- 관리자 권한 없음: `403 Forbidden`
- 매물이 존재하지 않음: `404 Not Found`, `PROPERTY_NOT_FOUND`
- 해당 매물의 사진이 존재하지 않음: `404 Not Found`, `PROPERTY_IMAGE_NOT_FOUND`
- 파일 없음, 빈 파일 또는 총 10장 초과: `400 Bad Request`, `INVALID_PROPERTY_IMAGE`
- 파일 한 장이 10MB 초과: `413 Payload Too Large`, `PROPERTY_IMAGE_TOO_LARGE`
- 지원하지 않거나 내용과 형식이 다른 파일: `415 Unsupported Media Type`, `UNSUPPORTED_PROPERTY_IMAGE_TYPE`
- 저장 실패: `500 Internal Server Error`, `PROPERTY_IMAGE_STORAGE_FAILED`

## 저장

- 볼륨 기본 경로는 `data/property-images`다.
- 배포 컨테이너에서는 `/app/data/property-images`를 사용한다.
- 실제 저장 키는 `{propertyId}/{randomUuid}.{확장자}`다.
- 원본 파일명은 저장하거나 응답하지 않는다.
- `property_image`에는 `id`, `property_id`, `storage_key`, `display_order`, `created_at`을 저장한다.
- `(property_id, display_order)`는 유일해야 한다.
- DB 저장에 실패하면 이번 요청에서 생성한 파일을 삭제한다.

## 이번 범위에서 제외

- 임의 순서 변경
- 썸네일 리사이징
- S3 및 CDN
- 매물 상세·지도 조회 응답에 사진 연결
