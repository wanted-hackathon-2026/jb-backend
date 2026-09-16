package com.jachwibangjeongsig.jb.global.llm;

public interface LlmClient {

	/**
	 * 모델 응답을 responseType 스키마로 강제해 역직렬화한다.
	 * responseType은 record여야 한다 ({@link JsonSchemas} 참고).
	 *
	 * @throws LlmUnavailableException 호출이 실패했거나 응답을 읽을 수 없을 때
	 */
	<T> T complete(String systemPrompt, String userPrompt, Class<T> responseType);
}
