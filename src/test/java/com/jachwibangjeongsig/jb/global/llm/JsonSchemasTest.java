package com.jachwibangjeongsig.jb.global.llm;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings("unchecked")
class JsonSchemasTest {

	enum Verdict {
		RECOMMENDED, NEUTRAL, AVOID
	}

	record Criterion(String name, int score) {
	}

	record Evaluation(String propertyName, List<Criterion> criteria, Verdict verdict, double totalScore,
			boolean shortlisted) {
	}

	record Unsupported(Map<String, String> extras) {
	}

	private static Map<String, Object> properties(Map<String, Object> schema) {
		return (Map<String, Object>) schema.get("properties");
	}

	@Test
	void marksEveryComponentRequiredAndForbidsExtraKeys() {
		Map<String, Object> schema = JsonSchemas.of(Evaluation.class);

		assertThat(schema).containsEntry("type", "object").containsEntry("additionalProperties", false);
		assertThat((List<String>) schema.get("required"))
				.containsExactly("propertyName", "criteria", "verdict", "totalScore", "shortlisted");
	}

	@Test
	void mapsScalarsListsEnumsAndNestedRecords() {
		Map<String, Object> properties = properties(JsonSchemas.of(Evaluation.class));

		assertThat(properties.get("propertyName")).isEqualTo(Map.of("type", "string"));
		assertThat(properties.get("totalScore")).isEqualTo(Map.of("type", "number"));
		assertThat(properties.get("shortlisted")).isEqualTo(Map.of("type", "boolean"));
		assertThat(properties.get("verdict"))
				.isEqualTo(Map.of("type", "string", "enum", List.of("RECOMMENDED", "NEUTRAL", "AVOID")));

		Map<String, Object> criteria = (Map<String, Object>) properties.get("criteria");
		assertThat(criteria).containsEntry("type", "array");

		Map<String, Object> item = (Map<String, Object>) criteria.get("items");
		assertThat(item).containsEntry("additionalProperties", false);
		assertThat(properties(item).get("score")).isEqualTo(Map.of("type", "integer"));
	}

	@Test
	void rejectsTypesItCannotDescribe() {
		assertThatThrownBy(() -> JsonSchemas.of(Unsupported.class))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Unsupported LLM response type");

		assertThatThrownBy(() -> JsonSchemas.of(String.class))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
