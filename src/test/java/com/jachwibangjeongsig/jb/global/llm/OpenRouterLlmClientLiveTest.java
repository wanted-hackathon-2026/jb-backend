package com.jachwibangjeongsig.jb.global.llm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 OpenRouter 를 호출한다 (유료). 키를 export 했을 때만 실행된다:
 *   OPENROUTER_API_KEY=... LLM_MODEL=openai/gpt-5-mini \
 *     ./gradlew test --tests '*OpenRouterLlmClientLiveTest'
 */
@EnabledIfEnvironmentVariable(named = "OPENROUTER_API_KEY", matches = ".+")
class OpenRouterLlmClientLiveTest {

	record Criterion(String name, int score, String comment) {
	}

	record Evaluation(String propertyName, List<Criterion> criteria, String summary) {
	}

	private final LlmClient client = new OpenRouterLlmClient(
			System.getenv("OPENROUTER_API_KEY"),
			System.getenv().getOrDefault("LLM_MODEL", "openai/gpt-5-mini"),
			JsonMapper.builder().build());

	@Test
	void fillsEveryFieldOfTheRequestedRecord() {
		Evaluation evaluation = client.complete(
				"너는 자취방 추천 도우미다. 사용자 선호도 기준으로 매물을 평가한다. score 는 0~100.",
				"""
						사용자 선호도: 채광 매우 중요, 소음 중요, 통근시간 30분 이내.
						매물: 역삼 원룸, 남향 3층, 대로변, 강남역까지 도보 12분, 보증금 1000/월세 60.
						채광과 소음 두 항목을 평가하고 총평을 써라.
						""",
				Evaluation.class);

		assertThat(evaluation.propertyName()).isNotBlank();
		assertThat(evaluation.summary()).isNotBlank();
		assertThat(evaluation.criteria()).hasSizeGreaterThanOrEqualTo(2);
		assertThat(evaluation.criteria()).allSatisfy(criterion -> {
			assertThat(criterion.name()).isNotBlank();
			assertThat(criterion.score()).isBetween(0, 100);
		});
	}
}
