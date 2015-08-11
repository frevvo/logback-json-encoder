package org.frevvo.logback;

import java.io.IOException;
import java.io.OutputStream;

import ch.qos.logback.classic.spi.ILoggingEvent;

public interface Emitter {
	void emit(OutputStream os, ILoggingEvent event, Environment env)
			throws IOException;
}
