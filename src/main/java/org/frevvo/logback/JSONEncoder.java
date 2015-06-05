package org.frevvo.logback;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Map.Entry;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.encoder.EncoderBase;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonGenerator.Feature;

/**
 * A Logback custom encoder that writes one JSON object per line for each
 * logging event. Here is an example of how to use it in logback.xml:
 * 
 * <pre>
 *  <appender name="stdout" class="ch.qos.logback.core.ConsoleAppender">
 * 		<encoder class="org.frevvo.logback.JSONEncoder"/>
 * 	</appender>
 * </pre>
 */
public class JSONEncoder extends EncoderBase<ILoggingEvent> {
	private static final JsonFactory JSON_FACTORY = new JsonFactory();
	static {
		JSON_FACTORY.enable(Feature.AUTO_CLOSE_JSON_CONTENT);
	}

	private Charset charset;

	private boolean immediateFlush = true;

	public void setImmediateFlush(boolean immediateFlush) {
		this.immediateFlush = immediateFlush;
	}

	public boolean isImmediateFlush() {
		return immediateFlush;
	}

	public void setCharset(Charset charset) {
		this.charset = charset;
	}

	public Charset getCharset() {
		return charset;
	}

	public void doEncode(ILoggingEvent event) throws IOException {
		JsonGenerator jg = JSON_FACTORY.createGenerator(outputStream,
				JsonEncoding.UTF8);
		jg.writeStartObject();
		try {
			jg.writeObjectField("level", event.getLevel().toString());
			jg.writeObjectField("timestamp", event.getTimeStamp());
			if (event.getMarker() != null)
				jg.writeObjectField("marker", event.getMarker().getName());
			jg.writeObjectField("logger-name", event.getLoggerName());
			jg.writeObjectField("thread-name", event.getThreadName());
			jg.writeObjectField("message", event.getMessage());
			if (event.getCallerData() != null
					&& event.getCallerData().length > 0)
				jg.writeObjectField("stacktrace",
						Arrays.toString(event.getCallerData()));
			jg.writeObjectFieldStart("custom");
			for (Entry<String, String> entry : event.getMDCPropertyMap()
					.entrySet()) {
				jg.writeObjectField(entry.getKey(), entry.getValue());
			}
		} finally {
			jg.writeEndObject();
			jg.writeRaw("\n");
		}
		if (isImmediateFlush())
			jg.flush();
	}

	public void close() throws IOException {
	}
}
