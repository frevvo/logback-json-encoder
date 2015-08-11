package org.frevvo.logback;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import ch.qos.logback.classic.spi.ILoggingEvent;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.util.JsonGeneratorDelegate;

public class JSONEmitter extends JsonGeneratorDelegate {
	public static final JSONEmitter NULL;
	static {
		try {
			NULL = new JSONEmitter();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static final JsonFactory JSON_FACTORY = new JsonFactory();
	static {
		JSON_FACTORY.enable(Feature.AUTO_CLOSE_JSON_CONTENT);
	}

	private List<BaseEmitter> emitters = new ArrayList<BaseEmitter>();

	public JSONEmitter() throws IOException {
		super(JSON_FACTORY.createGenerator(new ByteArrayOutputStream()));
	}

	public void splice(BaseEmitter emitter) {
		emitters.add(new ConstEmitter(getOutputStream().toByteArray()));
		emitters.add(emitter);
		getOutputStream().reset();
	}

	public void close() throws IOException {
		writeRaw("\n");
		close();
		emitters.add(new ConstEmitter(getOutputStream().toByteArray()));
	}

	public void write(OutputStream os, ILoggingEvent event, Environment env)
			throws IOException {
		for (Emitter emitter : emitters) {
			emitter.emit(os, event, env);
		}
	}

	protected ByteArrayOutputStream getOutputStream() {
		return (ByteArrayOutputStream) super.getOutputTarget();
	}
}