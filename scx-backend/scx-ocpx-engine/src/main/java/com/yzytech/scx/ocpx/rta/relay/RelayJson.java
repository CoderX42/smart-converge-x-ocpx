package com.yzytech.scx.ocpx.rta.relay;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

public final class RelayJson {

	public static final JsonMapper MAPPER = JsonMapper.builder()
		.enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
		.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
		.enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
		.disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
		.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
		.build();
	static {
		var coercion = MAPPER.coercionConfigFor(com.fasterxml.jackson.databind.type.LogicalType.Textual);
		for (var shape : java.util.List.of(com.fasterxml.jackson.databind.cfg.CoercionInputShape.Integer,
				com.fasterxml.jackson.databind.cfg.CoercionInputShape.Float,
				com.fasterxml.jackson.databind.cfg.CoercionInputShape.Boolean))
			coercion.setCoercion(shape, com.fasterxml.jackson.databind.cfg.CoercionAction.Fail);
	}

	private RelayJson() {
	}

	public static String write(Object value) {
		try {
			return MAPPER.writeValueAsString(value);
		}
		catch (Exception e) {
			throw new RelayException(400, "RTA_JSON_INVALID");
		}
	}

	public static <T> T read(String value, Class<T> type) {
		try {
			if (type == com.yzytech.scx.ocpx.rta.contract.RtaState.class) {
				var tree = (com.fasterxml.jackson.databind.node.ObjectNode) MAPPER.readTree(value);
				if (tree.path("matchedTaskIds").isObject() && tree.path("matchedTaskIds").isEmpty())
					tree.putArray("matchedTaskIds");
				return MAPPER.treeToValue(tree, type);
			}
			return MAPPER.readValue(value, type);
		}
		catch (Exception e) {
			throw new RelayException(400, "RTA_JSON_INVALID");
		}
	}

}
