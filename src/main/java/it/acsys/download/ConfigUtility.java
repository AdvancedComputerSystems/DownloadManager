package it.acsys.download;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
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
	
	public static File getFileDownloadDirectory(String uri, String productDownloadDir) {
		  File repositoryDir = null;
		  //String productDownloadDir = DatabaseUtility.getInstance().getProductDownloadDir(gid);
		  Properties configProperties = ConfigUtility.loadConfig();
		  String finalDestination = configProperties.getProperty("repositoryDir") + File.separator + productDownloadDir;
		  String tempDestination = configProperties.getProperty("repositoryDir") + File.separator  + "TEMPORARY" + File.separator + productDownloadDir;
		  if(Boolean.valueOf(configProperties.getProperty("isTemporaryDir"))){
			  repositoryDir = new File(tempDestination);
		  } else {
			  repositoryDir = new File(finalDestination);
		  }
		  if(!repositoryDir.exists()) {
	  		  repositoryDir.mkdirs();
	  	  }
  	 
	  	  if(uri.indexOf("?") != -1) {
				String queryUri = uri.trim().split("\\?")[1].replace("{", "%7B").replace("}", "%7D").replace(":", "%3A");
				uri = uri.trim().split("\\?")[0] + "?" +  queryUri;
			  }
	  	  URI uriOBJ = null;
		try {
			uriOBJ = new URI(uri);
		} catch (URISyntaxException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		  System.out.println("uri " + uri);
		  String query = uriOBJ.getQuery();
		  System.out.println("query " + query);
		  String downloadsOptions = "";
		  if(query != null) {
		    	  String[] params = query.split("&");
		  for (String param : params)  {  
		    if(param.startsWith("ngEO_DO")) {
				downloadsOptions = param.split("=")[1];
				downloadsOptions = downloadsOptions.replace("%7B", " (");
				downloadsOptions = downloadsOptions.replace("%7D", ")");
				downloadsOptions = downloadsOptions.replace("{", " (");
				downloadsOptions = downloadsOptions.replace("}", ")");
				downloadsOptions = downloadsOptions.replace("%3A", "=");
				downloadsOptions = downloadsOptions.replace(":", "=");
			 }
		  }
		}
	  	  String path = uriOBJ.getPath();
	  	  System.out.println("path " + path);
	  	  String fileName = path.substring(path.lastIndexOf("/")+1);
	  	  if(fileName.indexOf(".") != -1) {
	  		  fileName = fileName.substring(0, fileName.indexOf("."));
	  	  }
	  	  StringBuilder builder = new StringBuilder(fileName);
			  builder.append(downloadsOptions);
			  String currIndex = "";
			  File finalFile = new File(finalDestination + File.separator + fileName +  downloadsOptions + currIndex);
			  int n = 1;
			  while(finalFile.exists()) {
				  currIndex = " (" + String.valueOf(n) + ")";
				  finalFile = new File(finalDestination + File.separator + fileName + downloadsOptions + currIndex);
				  n++;
			  }
			  
			  if(Boolean.valueOf(configProperties.getProperty("isTemporaryDir")) ) {
				  File tempFile = new File(tempDestination + File.separator + fileName +  downloadsOptions + currIndex);
				  while(tempFile.exists()) {
					  currIndex = " (" + String.valueOf(n)+ ")";
					  tempFile = new File(tempDestination + File.separator + fileName +  downloadsOptions  + currIndex);
					  n++;
				  }
				  if(!tempFile.exists()) {
					  tempFile.mkdirs();
		    	  }
			  } else if(!finalFile.exists()) {
	  		  finalFile.mkdirs();
	  	  }
			  
	  	  File finalRep = new File(repositoryDir.getAbsolutePath() + File.separator + fileName +  downloadsOptions + currIndex);
	  	  return finalRep;
	}
}
