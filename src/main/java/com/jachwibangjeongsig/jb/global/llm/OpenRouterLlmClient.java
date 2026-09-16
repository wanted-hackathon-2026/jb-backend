package com.jachwibangjeongsig.jb.global.llm;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class OpenRouterLlmClient implements LlmClient {

	private static final String BASE_URL = "https://openrouter.ai/api/v1";

	private final RestClient restClient;
	private final ObjectMapper objectMapper;
	private final String model;

	public OpenRouterLlmClient(
			@Value("${llm.api-key}") String apiKey,
			@Value("${llm.model}") String model,
			ObjectMapper objectMapper) {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(Duration.ofSeconds(5));
		// 모델 추론은 수십 초가 걸린다. VWorld 쪽 3초를 그대로 쓰면 전부 타임아웃난다.
		factory.setReadTimeout(Duration.ofSeconds(60));
		this.restClient = RestClient.builder()
				.baseUrl(BASE_URL)
				.requestFactory(factory)
				.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
				.build();
		this.objectMapper = objectMapper;
		this.model = model;
	}

	@Override
	public <T> T complete(String systemPrompt, String userPrompt, Class<T> responseType) {
		JsonNode response = call(requestBody(systemPrompt, userPrompt, responseType));

		if (!response.path("error").isMissingNode()) {
			throw new LlmUnavailableException("OpenRouter returned an error: " + response.path("error"));
		}
		String content = response.path("choices").path(0).path("message").path("content").asString("");
		if (content.isBlank()) {
			throw new LlmUnavailableException("OpenRouter returned no message content");
		}
		try {
			return objectMapper.readValue(content, responseType);
		} catch (JacksonException exception) {
			throw new LlmUnavailableException("OpenRouter returned content that is not " + responseType.getSimpleName(),
					exception);
		}
	}

	private Map<String, Object> requestBody(String systemPrompt, String userPrompt, Class<?> responseType) {
		return Map.of(
				"model", model,
				"messages", List.of(
						Map.of("role", "system", "content", systemPrompt),
						Map.of("role", "user", "content", userPrompt)),
				"response_format", Map.of(
						"type", "json_schema",
						"json_schema", Map.of(
								"name", "result",
								"strict", true,
								"schema", JsonSchemas.of(responseType))));
	}

	private JsonNode call(Map<String, Object> body) {
		try {
			JsonNode response = restClient.post()
					.uri("/chat/completions")
					.body(body)
					.retrieve()
					.body(JsonNode.class);
			if (response == null) {
				throw new LlmUnavailableException("OpenRouter returned an empty body");
			}
			return response;
		} catch (RestClientException exception) {
			throw new LlmUnavailableException("OpenRouter request failed", exception);
		}
	}
}
