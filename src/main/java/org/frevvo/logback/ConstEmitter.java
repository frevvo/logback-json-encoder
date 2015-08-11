package org.frevvo.logback;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

import ch.qos.logback.classic.spi.ILoggingEvent;

public class ConstEmitter extends BaseEmitter {
	private byte[] bytes;

	public ConstEmitter(byte[] bytes) {
		this.bytes = bytes;
	}

	public void write(OutputStream os, ILoggingEvent event,
			Map<String, String> env, Map<String, Object> context,
			Map<String, String> mdc) throws IOException {
	}

	public void emit(OutputStream os, ILoggingEvent event, Environment env)
			throws IOException {
		os.write(bytes, 0, bytes.length);
	}
}
