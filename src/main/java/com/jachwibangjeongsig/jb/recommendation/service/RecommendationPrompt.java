package com.jachwibangjeongsig.jb.recommendation.service;

import com.jachwibangjeongsig.jb.property.entity.Property;
import com.jachwibangjeongsig.jb.property.entity.PropertyFeature;
import com.jachwibangjeongsig.jb.recommendation.entity.RecommendationCriteria;

import java.util.List;
import java.util.StringJoiner;

/** 프롬프트 문자열만 만든다. 호출도 저장도 하지 않아서 그대로 단위 테스트할 수 있다. */
final class RecommendationPrompt {

	private RecommendationPrompt() {
	}

	static String system() {
		return """
			너는 자취방 추천 도우미다. 사용자의 선호 중요도와 후보 매물 정보를 보고 매물마다 항목별 점수와 총평을 매긴다.

			규칙:
			- 모든 점수는 0~100 사이의 정수다. 높을수록 그 항목이 좋다는 뜻이다.
			- totalScore 는 사용자가 밝힌 중요도(1~5)를 가중치로 삼아 항목 점수를 종합한 값이다.
			  중요도가 높은 항목일수록 총점에 크게 반영해야 한다.
			- summary 는 한국어 2~3문장으로, 그 매물의 장점과 단점을 사용자 기준에서 설명한다.
			- 주어진 지표에 없는 사실을 지어내지 마라. 지표가 없는 항목은 그 점을 총평에 밝히고 중간값 부근을 준다.
			- candidateNumber 는 입력에 적힌 후보 번호를 그대로 쓴다.
			- 후보를 하나도 빠뜨리지 말고 모두 평가한다.
			""";
	}

	static String user(RecommendationCriteria criteria, List<String> candidateLines) {
		StringJoiner candidates = new StringJoiner("\n\n");
		candidateLines.forEach(candidates::add);
		return """
			[사용자 조건]
			근무지: %s (%s)
			이동수단: %s, 희망 통근시간: %d분 이내
			중요도(1~5): 채광 %d, 조용함 %d, 치안 %d, 인프라 %d
			보증금: %d~%d만원, 월세: %d~%d만원
			매물 유형: %s

			[후보 매물]
			%s
			""".formatted(
			criteria.getWorkplaceName(), criteria.getWorkplaceRoadAddress(),
			criteria.getTransportType(), criteria.getMaxCommuteMinutes(),
			criteria.getSunlightImportance(), criteria.getQuietnessImportance(),
			criteria.getSafetyImportance(), criteria.getInfrastructureImportance(),
			criteria.getDepositMin(), criteria.getDepositMax(),
			criteria.getMonthlyRentMin(), criteria.getMonthlyRentMax(),
			String.join(", ", criteria.roomTypeList()),
			candidates);
	}

	static String candidate(int number, Property property, int commuteMinutes,
		List<PropertyFeature> features, int maxDescription) {
		StringJoiner lines = new StringJoiner("\n");
		lines.add("후보 %d: %s".formatted(number, property.getName()));
		lines.add("  주소: %s".formatted(property.getRoadAddress()));
		lines.add("  유형: %s / %s, 보증금 %d만원, 월세 %d만원".formatted(
			property.getPropertyType(), property.getLeaseType(), property.getDeposit(), property.getMonthlyRent()));
		lines.add("  통근(직선거리 추정): 약 %d분".formatted(commuteMinutes));
		lines.add("  면적: %s, 층: %s/%s, 방향: %s, 준공: %s".formatted(
			text(property.getExclusiveArea()), text(property.getFloor()), text(property.getTotalFloors()),
			text(property.getDirection()), text(property.getBuildYear())));
		if (features.isEmpty()) {
			lines.add("  수집된 지표: 없음");
		} else {
			// 지표 코드는 수집기마다 늘어나므로 해석하지 않고 그대로 넘긴다.
			for (PropertyFeature feature : features) {
				lines.add("  지표 %s/%s: %s%s".formatted(feature.getCategory(), feature.getMetricCode(),
					feature.getNumericValue() != null ? feature.getNumericValue() : text(feature.getTextValue()),
					feature.getUnit() == null ? "" : " " + feature.getUnit()));
			}
		}
		if (property.getDescription() != null && !property.getDescription().isBlank()) {
			String description = property.getDescription().strip();
			lines.add("  설명: %s".formatted(description.length() <= maxDescription
				? description : description.substring(0, maxDescription) + "…"));
		}
		return lines.toString();
	}

	private static String text(Object value) {
		return value == null ? "정보 없음" : value.toString();
	}
}
