package org.frevvo.logback;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.encoder.EncoderBase;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonGenerator.Feature;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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

	private Charset charset;

	private boolean immediateFlush = true;

	private String layout;

	private String defaultFieldValue = "";

	private transient JsonNode layoutNode;

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

	public void setCharset(Charset charset) {
		this.charset = charset;
	}

	public Charset getCharset() {
		return charset;
	}

	public void setLayout(String layout) throws JsonParseException, IOException {
		if (layout != null)
			layout = layout.trim();
		this.layout = layout;
		if (layout != null)
			this.layoutNode = new ObjectMapper().readTree(new StringReader(
					layout));
		else
			this.layoutNode = null;
	}

	public String getLayout() {
		return layout;
	}

	public void doEncode(ILoggingEvent event) throws IOException {
		if (layoutNode != null) {
			JsonGenerator generator = JSON_FACTORY.createGenerator(
					outputStream, JsonEncoding.UTF8);
			JsonParser parser = layoutNode.traverse();
			try {
				encodeJson(parser, generator, event);
			} finally {
				parser.close();
				generator.writeRaw("\n");
			}

			if (isImmediateFlush())
				generator.flush();
		}
	}

	private void encodeJson(JsonParser parser, JsonGenerator generator,
			ILoggingEvent event) throws JsonParseException, IOException {
		while (parser.nextToken() != null) {
			switch (parser.getCurrentToken()) {
			case START_OBJECT:
				generator.writeStartObject();
				break;
			case START_ARRAY:
				generator.writeStartArray();
			case END_ARRAY:
				generator.writeEndArray();
				break;
			case END_OBJECT:
				generator.writeEndObject();
				break;
			case FIELD_NAME:
				generator.writeFieldName(parser.getText());
				break;
			case VALUE_FALSE:
			case VALUE_TRUE:
				generator.writeBoolean(parser.getBooleanValue());
				break;
			case VALUE_NULL:
				generator.writeNull();
				break;
			case VALUE_NUMBER_FLOAT:
				generator.writeNumber(parser.getFloatValue());
				break;
			case VALUE_NUMBER_INT:
				generator.writeNumber(parser.getIntValue());
				break;
			case VALUE_STRING:
				generateValue(generator, event, parser.getText());
				break;
			default:
				break;
			}
		}
	}

	public static final Pattern VARIABLE_PATTERN = Pattern
			.compile("#\\{([0-9a-zA-Z_\\-]*)(\\:([0-9a-zA-Z_\\-\\.]*))?\\}");

	private final void generateValue(final JsonGenerator gen,
			ILoggingEvent event, String value) throws IOException {
		if (value != null && value.indexOf("#{") > -1) {
			final Matcher m = VARIABLE_PATTERN.matcher(value);
			while (m.find()) {
				String var = m.group(1);
				if ("EVENT".equals(var)) {
					String eventProp = m.group(3);
					if (eventProp == null) {
						writeEventObject(gen, event);
						return;
					} else if ("level".equals(eventProp)) {
						writeLevelField(gen, event);
						return;
					} else if ("timestamp".equals(eventProp)) {
						writeTimestampField(gen, event);
						return;
					} else if ("logger".equals(eventProp)) {
						writeLoggerField(gen, event);
						return;
					} else if ("thread".equals(eventProp)) {
						writeThreadField(gen, event);
						return;
					} else if ("message".equals(eventProp)) {
						writeMessageField(gen, event);
						return;
					} else if ("marker".equals(eventProp)) {
						writeMarkerField(gen, event);
						return;
					} else if ("caller".equals(eventProp)) {
						writeCallerDataField(gen, event);
						return;
					}
				} else if ("MDC".equals(var)) {
					String mdcProp = m.group(3);
					if (mdcProp == null)
						writeMdcObject(gen, event.getMDCPropertyMap());
					else
						writeMdcField(gen, mdcProp, event.getMDCPropertyMap()
								.getOrDefault(mdcProp, null));
					return;
				} else if ("CONTEXT".equals(var)) {
					String contextProp = m.group(3);
					if (contextProp != null) {
						writeContextField(gen,
								getContext().getProperty(contextProp));
						return;
					}
					// only support context by name
				} else if ("ENVIRONMENT".equals(var)) {
					String envVar = m.group(3);
					if (envVar != null) {
						writeEnvironmentField(gen, System.getenv(envVar));
						return;
					}
					// only support context by name
				} else if ("SYSTEM".equals(var)) {
					String envVar = m.group(3);
					if (envVar != null) {
						writeSystemField(gen, System.getProperty(envVar));
						return;
					}
					// only support context by name
				}
			}
		}
		gen.writeString(value);
	}

	private final void writeSystemField(JsonGenerator gen, String value)
			throws IOException {
		if (value == null)
			value = getDefaultFieldValue();
		gen.writeString(value);
	}

	private final void writeEnvironmentField(JsonGenerator gen, String value)
			throws IOException {
		if (value == null)
			value = getDefaultFieldValue();
		gen.writeString(value);
	}

	private final void writeMdcObject(JsonGenerator gen, Map<String, String> mdc)
			throws IOException {
		gen.writeStartObject();
		for (Map.Entry<String, String> entry : mdc.entrySet()) {
			if (entry.getValue() != null && entry.getValue().length() > 0)
				writeMdcField(gen, entry.getKey(), entry.getValue());
		}
		gen.writeEndObject();
	}

	private final void writeEventObject(JsonGenerator gen, ILoggingEvent event)
			throws IOException {
		gen.writeStartObject();
		try {
			writeLevelField(gen, event);
			writeMarkerField(gen, event);
			writeTimestampField(gen, event);
			writeLoggerField(gen, event);
			writeThreadField(gen, event);
			writeMessageField(gen, event);
			writeCallerDataField(gen, event);
		} finally {
			gen.writeEndObject();
		}
	}

	private void writeContextField(JsonGenerator gen, String value)
			throws IOException {
		if (value == null)
			value = getDefaultFieldValue();
		gen.writeString(value);
	}

	private final void writeMdcField(JsonGenerator gen, String name,
			String value) throws IOException {
		if (value == null)
			value = getDefaultFieldValue();
		gen.writeStringField(name, value);
	}

	private final void writeCallerDataField(JsonGenerator gen,
			ILoggingEvent event) throws IOException {
		gen.writeString(Arrays.toString(event.getCallerData()));
	}

	private final void writeMarkerField(JsonGenerator gen, ILoggingEvent event)
			throws IOException {
		gen.writeString(event.getMarker().getName());
	}

	private final void writeMessageField(JsonGenerator gen, ILoggingEvent event)
			throws IOException {
		gen.writeString(event.getFormattedMessage());
	}

	private final void writeThreadField(JsonGenerator gen, ILoggingEvent event)
			throws IOException {
		gen.writeString(event.getThreadName());
	}

	private final void writeLoggerField(JsonGenerator gen, ILoggingEvent event)
			throws IOException {
		gen.writeString(event.getLoggerName());
	}

	private final void writeTimestampField(JsonGenerator gen,
			ILoggingEvent event) throws IOException {
		gen.writeNumber(event.getTimeStamp());
	}

	private final void writeLevelField(JsonGenerator gen, ILoggingEvent event)
			throws IOException {
		gen.writeString(event.getLevel().toString());
	}

	public void close() throws IOException {
	}
}
