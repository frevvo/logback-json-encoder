package org.frevvo.logback;

import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ch.qos.logback.classic.spi.ILoggingEvent;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.io.JsonStringEncoder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JSONTemplate {
	private static final Pattern VARIABLE_PATTERN = Pattern
			.compile("#\\{([0-9a-zA-Z_\\-]*)(\\:([0-9a-zA-Z_\\-\\.]*))?\\}");

	private final String jsonTemplate;

	public JSONTemplate(String jsonTemplate) {
		this.jsonTemplate = jsonTemplate;
	}

	public String getJsonTemplate() {
		return jsonTemplate;
	}

	public JSONEmitter compile() throws JsonParseException, IOException {
		if (jsonTemplate == null || jsonTemplate.length() == 0)
			return JSONEmitter.NULL;

		JSONEmitter emitter = new JSONEmitter();

		JsonNode node = new ObjectMapper().readTree(new StringReader(
				jsonTemplate));
		JsonParser parser = node.traverse();
		try {
			compile(parser, emitter);
			return emitter;
		} finally {
			parser.close();
			emitter.close();
		}
	}

	private void compile(JsonParser parser, JSONEmitter emiter)
			throws JsonParseException, IOException {
		parser.nextToken();
		while (!parser.isClosed()) {
			switch (parser.getCurrentToken()) {
			case START_OBJECT:
				emiter.writeStartObject();
				parser.nextToken();
				break;
			case START_ARRAY:
				emiter.writeStartArray();
				parser.nextToken();
			case END_ARRAY:
				emiter.writeEndArray();
				parser.nextToken();
				break;
			case END_OBJECT:
				emiter.writeEndObject();
				parser.nextToken();
				break;
			case FIELD_NAME:
				compile(parser, emiter, parser.getText());
				break;
			default:
				// ignoring anything else
				parser.nextToken();
				break;
			}
		}
	}

	private final void compile(JsonParser parser, JSONEmitter splicer,
			String fieldName) throws IOException {
		JsonToken token = parser.nextToken();
		switch (token) {
		case VALUE_FALSE:
		case VALUE_TRUE:
			splicer.writeFieldName(parser.getText());
			splicer.writeBoolean(parser.getBooleanValue());
			parser.nextToken();
			break;
		case VALUE_NULL:
			splicer.writeFieldName(fieldName);
			splicer.writeNull();
			parser.nextToken();
			break;
		case VALUE_NUMBER_FLOAT:
			splicer.writeFieldName(fieldName);
			splicer.writeNumber(parser.getFloatValue());
			parser.nextToken();
			break;
		case VALUE_NUMBER_INT:
			splicer.writeFieldName(fieldName);
			splicer.writeNumber(parser.getIntValue());
			parser.nextToken();
			break;
		case VALUE_STRING:
			compile(splicer, fieldName, parser.getText());
			parser.nextToken();
			break;
		default:
			splicer.writeFieldName(fieldName);
			break;
		}
	}

	private final void compile(final JSONEmitter splicer, String fieldName,
			String fieldValue) throws IOException {
		if (fieldValue != null && fieldValue.indexOf("#{") > -1) {
			final Matcher m = VARIABLE_PATTERN.matcher(fieldValue);
			while (m.find()) {
				String var = m.group(1);
				if ("EVENT".equals(var)) {
					String eventProp = m.group(3);
					if (eventProp == null) {
						compileEventObject(splicer, fieldName);
						return;
					} else if ("level".equals(eventProp)) {
						compileLevelField(splicer, fieldName);
						return;
					} else if ("timestamp".equals(eventProp)) {
						compileTimestampField(splicer, fieldName);
						return;
					} else if ("date".equals(eventProp)) {
						compileeDateField(splicer, fieldName);
						return;
					} else if ("logger".equals(eventProp)) {
						compileLoggerField(splicer, fieldName);
						return;
					} else if ("thread".equals(eventProp)) {
						compileThreadField(splicer, fieldName);
						return;
					} else if ("message".equals(eventProp)) {
						compileMessageField(splicer, fieldName);
						return;
					} else if ("marker".equals(eventProp)) {
						compileMarkerField(splicer, fieldName);
						return;
					} else if ("caller".equals(eventProp)) {
						compileCallerDataField(splicer, fieldName);
						return;
					}
				} else if ("MDC".equals(var)) {
					String mdcProp = m.group(3);
					if (mdcProp == null)
						compileMdcObject(splicer, fieldName);
					else
						compileMdcField(splicer, mdcProp);
					return;
				} else if ("CONTEXT".equals(var)) {
					String contextProp = m.group(3);
					if (contextProp != null) {
						compileContextField(splicer, fieldName);
						return;
					}
					// only support context by name
				} else if ("ENVIRONMENT".equals(var)) {
					String envVar = m.group(3);
					if (envVar != null) {
						compileEnvironmentField(splicer, fieldName);
						return;
					}
					// only support context by name
				} else if ("SYSTEM".equals(var)) {
					String envVar = m.group(3);
					if (envVar != null) {
						compileSystemField(splicer, fieldName);
						return;
					}
					// only support context by name
				}
			}
		} else
			splicer.writeStringField(fieldName, fieldValue);
	}

	private void compileSystemField(JSONEmitter splicer, final String fieldName) {
		splicer.splice(new BaseEmitter() {
			public void emit(OutputStream os, ILoggingEvent event,
					Environment env) throws IOException {
				Object value = env.resolve(fieldName);
				if (value != null)
					writer.write(value);
			}
		});
	}

	private void compileEnvironmentField(JSONEmitter splicer,
			final String fieldName) {
		splicer.spliceField(new BaseEmitter() {
			public void write(Writer writer, ILoggingEvent event,
					Map<String, String> env, Map<String, String> system,
					Map<String, Object> context, Map<String, String> mdc)
					throws IOException {
				String value = fieldValue(env.get(fieldName));
				if (value != null)
					writer.write(value);
			}
		});
	}

	private void compileContextField(final JSONEmitter splicer,
			final String fieldName) {
		splicer.spliceField(new BaseEmitter() {
			public void write(Writer writer, ILoggingEvent event,
					Map<String, String> env, Map<String, String> system,
					Map<String, Object> context, Map<String, String> mdc)
					throws IOException {
				Object value = context.get(fieldName);
				if (value instanceof BigDecimal)
					splicer.writeNumberField(fieldName, (BigDecimal) value);
				else if (value instanceof BigInteger) {
					splicer.writeFieldName(fieldName);
					splicer.writeNumber((BigInteger) value);
				} else if (value instanceof Short)
					splicer.writeNumberField(fieldName, (Short) value);
				else if (value instanceof Integer)
					splicer.writeNumberField(fieldName, (Integer) value);
				else if (value instanceof Long)
					splicer.writeNumberField(fieldName, (Long) value);
				else if (value instanceof Float)
					splicer.writeNumberField(fieldName, (Float) value);
				else if (value instanceof Double)
					splicer.writeNumberField(fieldName, (Double) value);
				else if (value instanceof String) {
					String value = fieldValue((String) value);
					if (value != null && value.length() > 0)
						splicer.writeStringField(fieldName, value);
				} else if (value instanceof Boolean)
					splicer.writeBooleanField(fieldName, (Boolean) value);
				else if (value != null)
					splicer.writeStringField(fieldName,
							fieldValue(value.toString()));
			}
		});
	}

	private void compileMdcField(JSONEmitter splicer, String fieldName) {
		splicer.spliceField(new BaseEmitter() {
			public void write(Writer writer, ILoggingEvent event,
					Map<String, String> env, Map<String, String> system,
					Map<String, Object> context, Map<String, String> mdc)
					throws IOException {
				String value = fieldValue(env.get(fieldName));
				if (value != null)
					writer.write(value);
			}
		});
	}

	private void compileMdcObject(JSONEmitter splicer, String fieldName) {

	}

	private void compileCallerDataField(JSONEmitter splicer, String fieldName) {
		splicer.spliceStringField(fieldName);
	}

	private void compileMarkerField(JSONEmitter splicer, String fieldName) {
		splicer.spliceStringField(fieldName);
	}

	private void compileMessageField(JSONEmitter splicer, String fieldName) {
		splicer.spliceStringField(fieldName);
	}

	private void compileThreadField(JSONEmitter splicer, String fieldName) {
		splicer.spliceStringField(fieldName);
	}

	private void compileLoggerField(JSONEmitter splicer, String fieldName) {
		splicer.spliceStringField(fieldName);
	}

	private void compileeDateField(JSONEmitter splicer, String fieldName) {
		splicer.spliceStringField(fieldName);
	}

	private void compileTimestampField(JSONEmitter splicer, String fieldName) {
		splicer.spliceStringField(fieldName);
	}

	private void compileLevelField(JSONEmitter splicer, String fieldName) {
		splicer.spliceStringField(fieldName);
	}

	private void compileEventObject(JSONEmitter splicer, String fieldName) {
	}

	private String fieldValue(String value) {
		return value;
	}
}
