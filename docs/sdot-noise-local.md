# S-DoT 소음 데이터 로컬 설정

Git에는 서울시 인증키와 공식 센서 위치 파일을 포함하지 않는다.

1. 서울 열린데이터광장에서 일반 인증키를 발급받아 `.env`의 `SEOUL_OPEN_API_KEY`에 넣는다.
2. 공식 센서 설치 위치 XLSX를 `sensor_id,latitude,longitude,district` 열의 UTF-8 CSV로 변환하여
   `data/noise/sensors.csv`에 둔다.
3. 서버에서는 같은 파일을 컨테이너에 읽기 전용으로 마운트하고 `SDOT_SENSOR_FILE`을 컨테이너 경로로 지정한다.
4. `SDOT_CACHE_SECONDS` 기본값은 3,600초다.

공식 API는 현재 `http://openapi.seoul.go.kr:8088`만 정상 연결되며 인증키가 URL 경로로 전송된다.
HTTPS 8088과 HTTPS 443은 정상 연결되지 않았다. 운영 보안 검토 없이 인증키를 로그에 남기지 않는다.
