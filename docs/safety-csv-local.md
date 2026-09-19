# 로컬 치안 CSV 연결

프로젝트 루트에서 기존 방식으로 백엔드를 재시작한다. `data/safety/cctv.csv`가 있으면 자동 활성화된다.
다른 디렉토리를 쓰려면 `--safety.csv.directory=/절대/경로`를 지정한다. 끄려면 `--safety.csv.enabled=false`.
기존 DB/OAuth/VWorld 환경 설정은 그대로 필요하며 인증키를 이 문서나 Git에 넣지 않는다.

`data/safety`는 Git에서 제외하며 원본 다운로드 파일은 수정하지 않는다.

| 로컬 파일명 | 내용 |
| --- | --- |
| cctv.csv | 첨부한 서울 CCTV 원자료 (CP949) |
| emergency-bell.csv | 첨부한 서울 비상벨 원자료 (CP949) |
| police.csv | 첨부한 경찰청 지구대·파출소 주소 원자료 (CP949) |
| seoul.geojson | WGS84 서울 행정경계 FeatureCollection |

현재 내려받은 경계는 southkorea/seoul-maps의 **JUSO 2015 자치구 비단순화 자료**이다.
출처: https://github.com/southkorea/seoul-maps/blob/master/juso/2015/json/seoul_municipalities_geo.json
저장소의 라이선스 안내는 Apache-2.0이다: https://github.com/southkorea/seoul-maps#copyright-and-license
**2026년 최신 경계 일치 여부는 검증하지 않았다.** 정확한 최신 경계가 필요하면 파일을 교체한다.
현재 파일 SHA-256: `4aec42529e845fa9a85093a4cfdaba8c7dfdd014950fe988f965c7efe93b07dd`.

매물 저장 트랜잭션이 커밋된 다음 시설 목록을 읽고 500m 집계와 최근접 거리를 기존 property_feature에 저장한다.
성공한 목록은 메모리에 유지하며 파일 교체 후 앱을 재시작해야 반영된다.
CSV나 경계 파일이 없거나 파싱에 실패하면 해당 지표를 저장하지 않는다. 원자료의 좌표 오류는 제외 건수를 로그로 남긴다.

파출소는 첫 수집에서 서울청 주소 각각을 VWorld로 변환하므로 **첫 등록 응답이 오래 걸릴 수 있다.**
변환 실패 시 일부 관서만으로 최단거리를 저장하지 않는다. 기존 거리 값은 유지한다.
실사용 전 일괄 사전 변환 또는 비동기 수집 정책을 확정해야 한다.
보안등은 자료가 없어 현재 미연결이며 `security-light.csv`가 없으면 0이 아닌 미수집으로 처리한다.

로컬 자료는 서버에 자동 업로드되지 않는다. 이번 작업은 EC2 환경 변경·배포를 포함하지 않는다.
