package it.acsys.download;

import java.util.Properties;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

public class LogUtility {
	
	public static void setLevel(Logger log) {
		Properties configProperties = ConfigUtility.loadConfig();
		log.setLevel(Level.toLevel(configProperties.getProperty("loglevel")));
	}

}
