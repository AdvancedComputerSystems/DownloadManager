package it.acsys.download;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.apache.log4j.Logger;

public class ConfigUtility {
	private static Logger log = Logger.getLogger(ConfigUtility.class.getName());
	
	public static Properties loadConfig() {
		
		String configPath=null;
		Properties configProperties = new Properties();
		InputStream stream  = null;
		try {
        	configPath = System.getProperty("configPath"); 
        	if(configPath==null){
        		log.error("Cannot start without a configuration file.");
        		System.exit(1);
        	}
        	File configFile = new File(configPath);
        	stream  = new FileInputStream(configFile);
        	configProperties.load(stream);
        	stream.close();
        } catch (IOException e) {
        	e.printStackTrace();
        	log.error("Cannot find configuration file : " + e.getMessage());
        } finally {
        	if(stream != null) {
        		try {
        			
					stream.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
        	}
        }
		
        return configProperties;
	}
}
