package com.jachwibangjeongsig.jb.global.llm;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * record 를 OpenAI strict json_schema 로 변환한다.
 *
 * ponytail: String/숫자/boolean/enum/List/중첩 record 만 지원한다. Map, Optional,
 * 날짜 타입이 필요해지면 typeOf 에 분기를 추가한다. 라이브러리(victools)를 쓰지 않는 이유는
 * 그쪽이 Jackson 2 에 의존해서 이 프로젝트의 Jackson 3 와 클래스패스가 충돌하기 때문.
 */
final class JsonSchemas {

	private JsonSchemas() {
	}

	static Map<String, Object> of(Class<?> type) {
		if (!type.isRecord()) {
			throw unsupported(type);
		}
		Map<String, Object> properties = new LinkedHashMap<>();
		List<String> required = new ArrayList<>();
		for (RecordComponent component : type.getRecordComponents()) {
			properties.put(component.getName(), typeOf(component.getGenericType()));
			required.add(component.getName());
		}
		// strict 모드는 모든 프로퍼티가 required 이고 additionalProperties 가 false 여야 통과한다.
		return Map.of(
				"type", "object",
				"properties", properties,
				"required", required,
				"additionalProperties", false);
	}

	private static Map<String, Object> typeOf(Type type) {
		if (type instanceof ParameterizedType parameterized) {
			if (parameterized.getRawType() != List.class) {
				throw unsupported(type);
			}
			return Map.of("type", "array", "items", typeOf(parameterized.getActualTypeArguments()[0]));
		}
		if (!(type instanceof Class<?> raw)) {
			throw unsupported(type);
		}
		if (raw == String.class) {
			return Map.of("type", "string");
		}
		if (raw == boolean.class || raw == Boolean.class) {
			return Map.of("type", "boolean");
		}
		if (raw == int.class || raw == Integer.class || raw == long.class || raw == Long.class) {
			return Map.of("type", "integer");
		}
		if (raw == double.class || raw == Double.class || raw == float.class || raw == Float.class) {
			return Map.of("type", "number");
		}
		if (raw.isEnum()) {
			return Map.of("type", "string", "enum", Arrays.stream(raw.getEnumConstants())
					.map(constant -> ((Enum<?>) constant).name())
					.toList());
		}
		if (raw.isRecord()) {
			return of(raw);
		}
		throw unsupported(type);
	}

	private static IllegalArgumentException unsupported(Type type) {
		return new IllegalArgumentException("Unsupported LLM response type: " + type.getTypeName());
	}
}
