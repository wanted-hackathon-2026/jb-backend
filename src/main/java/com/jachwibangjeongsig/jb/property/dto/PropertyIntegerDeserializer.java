package com.jachwibangjeongsig.jb.property.dto;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/** Reject coercion so a decimal price or floor cannot silently lose precision. */
public class PropertyIntegerDeserializer extends ValueDeserializer<Integer> {

	@Override
	public Integer deserialize(JsonParser parser, DeserializationContext context) throws JacksonException {
		if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT) {
			return (Integer) context.handleUnexpectedToken(Integer.class, parser);
		}
		return parser.getIntValue();
	}
}
