package org.frevvo.logback;

import java.io.IOException;

import org.junit.Before;
import org.junit.Test;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;

import com.fasterxml.jackson.core.JsonParseException;

public class JSONEncoderTest {
	private JSONEncoder encoder = new JSONEncoder();
	private LoggerContext loggerContext = new LoggerContext();
	private LoggingEvent event = new LoggingEvent(JSONEncoderTest.class.getCanonicalName(), loggerContext.getLogger(JSONEncoderTest.class), Level.INFO, "aa {}", null, new Object[]{"yuri"});
	
	@Before
	public void setUp() throws IOException{
		encoder.init(System.out);
		loggerContext.putObject("TEST", new Integer(1));
	}
	
	@Test
	public void testLevel() throws JsonParseException, IOException {
		encoder.setLayout("{ \"level\" : \"#{EVENT:level}\" }").doEncode(event);
	}
	
	@Test
	public void testNestedLevel() throws JsonParseException, IOException {
		encoder.setLayout("{ \"event\" : { \"level\" : \"#{EVENT:level}\"} }").doEncode(event);
	}

	@Test
	public void testNestedNumber() throws JsonParseException, IOException {
		encoder.setLayout("{ \"level\" : { \"value\": 1 } }").doEncode(event);
	}

	@Test
	public void testCallerData() throws JsonParseException, IOException {
		encoder.setLayout("{ \"caller\" : \"#{EVENT:caller}\" }").doEncode(event);
	}
	
	@Test
	public void testComposite() throws JsonParseException, IOException {
		encoder.setLayout("{ \"mymessage\" : \"This is my message: #{EVENT:message}\" }").doEncode(event);
	}

}

//"{
//"	'level': '#{EVENT:level}',\n"
//	'marker': '#{EVENT:marker}',
//	'timestamp': '#{EVENT:timestamp}',
//	'logger': '#{EVENT:logger}',
//	'thread-name': '#{EVENT:thread}',
//	'message': '#{EVENT:message}',
//	'caller': '#{EVENT:caller}',
//	'mdc': '#{MDC}',
//	'fileEncoding': '#{SYSTEM:file.encoding}',
//	'HOME': '#{ENVIRONMENT:HOME}',
//	'context': {
//		'test': '#{CONTEXT:TEST}'
//	}
//}"
