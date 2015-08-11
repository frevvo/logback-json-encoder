package org.frevvo.logback;

import ch.qos.logback.classic.spi.ILoggingEvent;


public abstract class BaseEmitter implements Emitter {
	protected Object resolveVariable(String name, ILoggingEvent event,
			Environment env){
		if( name == null || name.length() == 0 )
			return null;
		
		if( "event.level".equals(name) ){
			return event.getLevel().toString();				
		}
		else if( "event.timestamp".equals(name) ){
			return event.getTimeStamp();				
		}
		else if( "event.date".equals(name) ){
			return event.getLevel().toString();				
		}
		else if( "event.logger".equals(name) ){
			
		}
		else if( "event.thread".equals(name) ){
			
		}
		else if( "event.message".equals(name) ){
			
		}
		else if( "event.marker".equals(name) ){
			
		}
		else if( "event.caller".equals(name) ){
			
		}
		else if( "mdc.")
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
}
