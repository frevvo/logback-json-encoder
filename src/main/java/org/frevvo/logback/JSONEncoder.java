package org.frevvo.logback;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Date;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.encoder.EncoderBase;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonGenerator.Feature;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.util.ISO8601Utils;

/**
 * A Logback custom encoder that writes one JSON object per line for each
 * logging event. Here is an example of how to use it in logback.xml:
 * 
 * <pre>
 *  <appender name="stdout" class="ch.qos.logback.core.ConsoleAppender">
 * 		<encoder class="org.frevvo.logback.JSONEncoder">
 * 			<layout>{
 * 				"level": "#{EVENT:level}",
 * 				"timestamp": "#{EVENT:timestamp}",
 * 				"logger": "#{EVENT:logger}",
 * 				"thread-name": "#{EVENT:thread}",
 * 				"message": "#{EVENT:message}",
 * 				"caller": "#{EVENT:caller}",
 * 				"mdc": "#{MDC}",
 * 				"fileEncoding": "#{SYSTEM:file.encoding}",
 * 				"HOME": "#{ENVIRONMENT:HOME}",
 * 				"context": {
 * 					"test": "#{CONTEXT:TEST}"
 * 				}
 * 			}</layout>
 * 		</encoder>
 * 	</appender>
 * </pre>
 * 
 */
public class JSONEncoder extends EncoderBase<ILoggingEvent> {
	private static final JsonFactory JSON_FACTORY = new JsonFactory();
	static {
		JSON_FACTORY.enable(Feature.AUTO_CLOSE_JSON_CONTENT);
	}

	private boolean immediateFlush = true;

	private String defaultFieldValue = "";

	private JSONTemplate template;

	private transient JSONEmitter splicer;

	private transient JsonNode layoutNode;

	private Environment environment = new Environment() {
		public Object resolve(String name) {
			String value = System.getProperty(name);
			if (value == null)
				value = System.getenv(name);
			return value;
		}
	};

	public void setImmediateFlush(boolean immediateFlush) {
		this.immediateFlush = immediateFlush;
	}

	public boolean isImmediateFlush() {
		return immediateFlush;
	}

	public void setDefaultFieldValue(String defaultFieldValue) {
		this.defaultFieldValue = defaultFieldValue;
	}

	public String getDefaultFieldValue() {
		return defaultFieldValue;
	}

	public Environment getEnvironment() {
		return environment;
	}

	public JSONEncoder setTemplate(String template) throws JsonParseException,
			IOException {
		if (template != null)
			template = template.trim();
		this.template = new JSONTemplate(template);
		this.splicer = this.template.compile();
		return this;
	}

	public String getTemplate() {
		return template.getJsonTemplate();
	}

	public void doEncode(ILoggingEvent event) throws IOException {
		splicer.write(outputStream, event, getEnvironment());
		if (isImmediateFlush())
			outputStream.flush();
	}

	private void encodeJson(JsonParser parser, JsonGenerator generator,
			ILoggingEvent event) throws JsonParseException, IOException {
		parser.nextToken();
		while (!parser.isClosed()) {
			switch (parser.getCurrentToken()) {
			case START_OBJECT:
				generator.writeStartObject();
				parser.nextToken();
				break;
			case START_ARRAY:
				generator.writeStartArray();
				parser.nextToken();
			case END_ARRAY:
				generator.writeEndArray();
				parser.nextToken();
				break;
			case END_OBJECT:
				generator.writeEndObject();
				parser.nextToken();
				break;
			case FIELD_NAME:
				generateField(parser, generator, event, parser.getText());
				break;
			default:
				// ignoring anything else
				parser.nextToken();
				break;
			}
		}
	}

	private final void generateField(JsonParser parser,
			JsonGenerator generator, ILoggingEvent event, String fieldName)
			throws IOException {
		JsonToken token = parser.nextToken();
		switch (token) {
		case VALUE_FALSE:
		case VALUE_TRUE:
			generator.writeFieldName(parser.getText());
			generator.writeBoolean(parser.getBooleanValue());
			parser.nextToken();
			break;
		case VALUE_NULL:
			generator.writeFieldName(fieldName);
			generator.writeNull();
			parser.nextToken();
			break;
		case VALUE_NUMBER_FLOAT:
			generator.writeFieldName(fieldName);
			generator.writeNumber(parser.getFloatValue());
			parser.nextToken();
			break;
		case VALUE_NUMBER_INT:
			generator.writeFieldName(fieldName);
			generator.writeNumber(parser.getIntValue());
			parser.nextToken();
			break;
		case VALUE_STRING:
			generateValue(generator, event, fieldName, parser.getText());
			parser.nextToken();
			break;
		default:
			generator.writeFieldName(fieldName);
			break;
		}
	}

	public static final Pattern VARIABLE_PATTERN = Pattern
			.compile("#\\{([0-9a-zA-Z_\\-]*)(\\:([0-9a-zA-Z_\\-\\.]*))?\\}");

	private final void generateValue(final JsonGenerator gen,
			ILoggingEvent event, String fieldName, String fieldValue)
			throws IOException {
		if (fieldValue != null && fieldValue.indexOf("#{") > -1) {
			final Matcher m = VARIABLE_PATTERN.matcher(fieldValue);
			while (m.find()) {
				String var = m.group(1);
				if ("EVENT".equals(var)) {
					String eventProp = m.group(3);
					if (eventProp == null) {
						writeEventObject(gen, event, fieldName);
						return;
					} else if ("level".equals(eventProp)) {
						writeStringField(gen, fieldName,
								event.getLevel() != null ? event.getLevel()
										.toString() : null);
						return;
					} else if ("timestamp".equals(eventProp)) {
						if (event.getTimeStamp() > -1)
							gen.writeNumberField(fieldName,
									event.getTimeStamp());
						return;
					} else if ("date".equals(eventProp)) {
						writeDateField(gen, event, fieldName);
						return;
					} else if ("logger".equals(eventProp)) {
						writeStringField(gen, fieldName, event.getLoggerName());
						return;
					} else if ("thread".equals(eventProp)) {
						writeStringField(gen, fieldName, event.getThreadName());
						return;
					} else if ("message".equals(eventProp)) {
						writeStringField(gen, fieldName,
								event.getFormattedMessage());
						return;
					} else if ("marker".equals(eventProp)) {
						writeStringField(gen, fieldName,
								event.getMarker() != null ? event.getMarker()
										.getName() : null);
						return;
					} else if ("caller".equals(eventProp)) {
						writeCallerDataField(gen, event, fieldName);
						return;
					}
				} else if ("MDC".equals(var)) {
					String mdcProp = m.group(3);
					if (mdcProp == null)
						writeMdcObject(gen, event.getMDCPropertyMap(),
								fieldName);
					else
						writeStringField(gen, mdcProp, event
								.getMDCPropertyMap().get(mdcProp));
					return;
				} else if ("CONTEXT".equals(var)) {
					String contextProp = m.group(3);
					if (contextProp != null) {
						writeContextField(gen, fieldName, getContext()
								.getObject(contextProp));
						return;
					}
					// only support context by name
				} else if ("ENVIRONMENT".equals(var)) {
					String envVar = m.group(3);
					if (envVar != null) {
						writeStringField(gen, fieldName, System.getenv(envVar));
						return;
					}
					// only support context by name
				} else if ("SYSTEM".equals(var)) {
					String envVar = m.group(3);
					if (envVar != null) {
						writeStringField(gen, fieldName,
								fieldValue(System.getProperty(envVar)));
						return;
					}
					// only support context by name
				}
			}
		} else
			gen.writeStringField(fieldName, fieldValue);
	}

	private final String fieldValue(String value) {
		if (value == null)
			value = getDefaultFieldValue();
		return value;
	}

	private final void writeStringField(JsonGenerator gen, String fieldName,
			String fieldValue) throws IOException {
		fieldName = fieldValue(fieldName);
		if (fieldValue != null && fieldValue != null)
			gen.writeStringField(fieldName, fieldValue);
	}

	private final void writeMdcObject(JsonGenerator gen,
			Map<String, String> mdc, String fieldName) throws IOException {
		if (mdc.size() > 0) {
			gen.writeStartObject();
			try {
				for (Map.Entry<String, String> entry : mdc.entrySet()) {
					if (entry.getValue() != null
							&& entry.getValue().length() > 0)
						writeMdcField(gen, entry.getKey(), entry.getValue());
				}
			} finally {
				gen.writeEndObject();
			}
		}
	}

	private final void writeEventObject(JsonGenerator gen, ILoggingEvent event,
			String fieldName) throws IOException {
		gen.writeStartObject();
		try {
			// LEVEL
			writeStringField(gen, fieldName, event.getLevel() != null ? event
					.getLevel().toString() : null);

			// TIMESTAMP
			// if (event.getTimeStamp() > -1)
			// gen.writeNumberField(fieldName,
			// event.getTimeStamp());
			//
			// DATE
			writeDateField(gen, event, fieldName);

			// LOGGER
			writeStringField(gen, fieldName, event.getLoggerName());

			// THREAD NAME
			writeStringField(gen, fieldName, event.getThreadName());

			// MESSAGE
			writeStringField(gen, fieldName, event.getFormattedMessage());

			// MARKER
			writeStringField(gen, fieldName, event.getMarker() != null ? event
					.getMarker().getName() : null);

			// CALLER DATA
			writeCallerDataField(gen, event, fieldName);

		} finally {
			gen.writeEndObject();
		}
	}

	private void writeContextField(JsonGenerator gen, String fieldName,
			Object fieldValue) throws IOException {
		if (fieldValue instanceof BigDecimal)
			gen.writeNumberField(fieldName, (BigDecimal) fieldValue);
		else if (fieldValue instanceof BigInteger) {
			gen.writeFieldName(fieldName);
			gen.writeNumber((BigInteger) fieldValue);
		} else if (fieldValue instanceof Short)
			gen.writeNumberField(fieldName, (Short) fieldValue);
		else if (fieldValue instanceof Integer)
			gen.writeNumberField(fieldName, (Integer) fieldValue);
		else if (fieldValue instanceof Long)
			gen.writeNumberField(fieldName, (Long) fieldValue);
		else if (fieldValue instanceof Float)
			gen.writeNumberField(fieldName, (Float) fieldValue);
		else if (fieldValue instanceof Double)
			gen.writeNumberField(fieldName, (Double) fieldValue);
		else if (fieldValue instanceof String) {
			String value = fieldValue((String) fieldValue);
			if (value != null && value.length() > 0)
				gen.writeStringField(fieldName, value);
		} else if (fieldValue instanceof Boolean)
			gen.writeBooleanField(fieldName, (Boolean) fieldValue);
		else if (fieldValue != null)
			gen.writeStringField(fieldName, fieldValue(fieldValue.toString()));
	}

	private final void writeMdcField(JsonGenerator gen, String name,
			String value) throws IOException {
		if (value != null)
			gen.writeStringField(name, value);
	}

	private final void writeCallerDataField(JsonGenerator gen,
			ILoggingEvent event, String fieldName) throws IOException {
		if (event.getCallerData() != null && event.getCallerData().length > 0)
			gen.writeStringField(fieldName,
					Arrays.toString(event.getCallerData()));
	}

	private final void writeDateField(JsonGenerator gen, ILoggingEvent event,
			String fieldName) throws IOException {
		long timestamp = event.getTimeStamp();
		if (timestamp > -1)
			gen.writeStringField(fieldName,
					ISO8601Utils.format(new Date(timestamp)));
	}

	public void close() throws IOException {
	}
}
