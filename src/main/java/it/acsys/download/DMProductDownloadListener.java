package it.acsys.download;

import int_.esa.eo.ngeo.downloadmanager.exception.DMPluginException;
import int_.esa.eo.ngeo.downloadmanager.plugin.EDownloadStatus;
import int_.esa.eo.ngeo.downloadmanager.plugin.IDownloadProcess;
import int_.esa.eo.ngeo.downloadmanager.plugin.IProductDownloadListener;
import it.acsys.download.ngeo.database.DatabaseUtility;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.log4j.Logger;

import com.siemens.pse.umsso.client.UmssoCLCore;
import com.siemens.pse.umsso.client.UmssoCLCoreImpl;
import com.siemens.pse.umsso.client.UmssoCLInput;
import com.siemens.pse.umsso.client.UmssoHttpPost;
import com.siemens.pse.umsso.client.util.UmssoHttpResponse;

public class DMProductDownloadListener implements IProductDownloadListener {
	
	private static Logger log = Logger.getLogger(DMProductDownloadListener.class);
	private static Properties configProperties = null;
	private static Properties excProperties = null;
	
	private static HashMap<String, IDownloadProcess> cache = null;
	private String processIdentifier;
	
	private static Properties usersConfigProperties = null;
	private static String usersConfigPath = ConfigUtility.loadConfig().getProperty("usersConfigPath");
	private static String exceptionProp = "./config/ExceptionMapping.properties";
	
	private static String pwd;
	private static String user;
	
	static {
        try {
        	String configPath = System.getProperty("configPath"); 
        	File configFile = new File(configPath);
        	InputStream stream  = new FileInputStream(configFile);
        	configProperties = new Properties();
        	configProperties.load(stream);
        	stream.close();
        	File excepFile = new File(exceptionProp);
        	InputStream excepStream  = new FileInputStream(excepFile);
        	excProperties = new Properties();
        	excProperties.load(excepStream);
        	excepStream.close();
        	usersConfigProperties = new Properties();
        	File usersConfigFile = new File(usersConfigPath);
        	InputStream usersStream  = new FileInputStream(usersConfigFile);
        	usersConfigProperties.load(usersStream);
        	usersStream.close();
        	user = usersConfigProperties.getProperty("umssouser");
        	AESCryptUtility aesCryptUtility = new AESCryptUtility();
        	pwd = aesCryptUtility.decryptString((String) usersConfigProperties.getProperty("umssopwd"));
        } catch (Exception e) {
            e.printStackTrace();
        }
	}
	
	public DMProductDownloadListener(String processIdentifier, HashMap<String, IDownloadProcess> cache) {
		LogUtility.setLevel(log);
		this.processIdentifier = processIdentifier;
		this.cache = cache;
	}
	
	@Override
	public void progress(Integer progress, Long completedLength, EDownloadStatus status,
			String message) {
		if(status.equals(EDownloadStatus.PAUSED)) {
			int currStatuId = DatabaseUtility.getInstance().getStatusId(processIdentifier);
			if(currStatuId == 2) {
				status = EDownloadStatus.NOT_STARTED;
			}
		}
		if(status.equals(EDownloadStatus.IN_ERROR)) {
			System.out.println("ERROR");
			System.out.println(excProperties.keySet().size());
			log.error("message " + message);
			Set<Object> keys = excProperties.keySet();
			for(Object key: keys) {
				System.out.println("key " + key);
				if(message.contains((String) key)) {
					message = excProperties.getProperty((String) key);
				}
			}
			message = message.substring(0, Math.min(message.length(), 250));
			
			DatabaseUtility.getInstance().updateErrorMessage(processIdentifier, message);
			if(message.contains(configProperties.getProperty("error_message"))) {
				log.fatal(message);
			}
		}
		
		if(status.equals(EDownloadStatus.COMPLETED)) {
			System.out.println("COMPLETED " + processIdentifier); 
			progress = 100;
		}
		DatabaseUtility.getInstance().progress(progress, completedLength, status, processIdentifier);
		
		try {
			if(status.equals(EDownloadStatus.COMPLETED) || status.equals(EDownloadStatus.IN_ERROR) 
					|| status.equals(EDownloadStatus.CANCELLED)) {
						boolean isTemporaryDir = Boolean.valueOf((String) configProperties.get("isTemporaryDir"));
						System.out.println("isTemporaryDir " + isTemporaryDir);
						IDownloadProcess process = (IDownloadProcess) cache.get(processIdentifier);
						if(process != null) {
							File[] files = process.getDownloadedFiles();
							System.out.println("files[0] " + files[0].getAbsolutePath());
							if(files != null) {
								if(status.equals(EDownloadStatus.CANCELLED)) {
									for(int n=0; n<files.length; n++) {
										File oldFile =  new File(files[n].getAbsolutePath());
										File ariaFile = new File(oldFile.getAbsolutePath() + ".aria2");
										FileUtility.delete(oldFile);
										FileUtility.delete(ariaFile);
									}
								} else if(isTemporaryDir) {
									for(int n=0; n<files.length; n++) {
										String scriptCommand = (String) configProperties.getProperty("script_command");
										File oldFile =  new File(files[n].getAbsolutePath());
										String[] parts = files[n].getAbsolutePath().split("TEMPORARY/");
										File newFile = null;
										if(oldFile.exists()) {
											if(oldFile.isDirectory()) {
												System.out.println("IS DIR " + oldFile.getAbsolutePath());
												newFile = new File(parts[0] + parts[1]);
												FileUtils.copyDirectory(oldFile, newFile);
												System.out.println("Copying to " + newFile.getAbsolutePath());
												FileUtils.deleteDirectory(oldFile);
												//INVOKE SHELL SCRIPT IF CONFIGURED
												if(scriptCommand != null && !scriptCommand.equals("")) {
													executeScript(newFile.getAbsolutePath());
												}
												
											} else {
												System.out.println("IS FILE " + oldFile.getAbsolutePath());
												newFile = new File(parts[0]);
												if(parts[1].indexOf(File.separator)!=-1) {
													newFile = new File(parts[0] + parts[1].substring(0, parts[1].indexOf(File.separator)));
													File oldDir = new File(parts[0] + "TEMPORARY/" + parts[1].substring(0, parts[1].indexOf(File.separator)));
													System.out.println("oldDir" + oldDir.getAbsolutePath());
													if(oldDir.exists()) {
														FileUtils.copyDirectory(oldDir, newFile);
														FileUtils.deleteDirectory(oldDir);
													}
												} else {
													FileUtils.copyFileToDirectory(oldFile, newFile);
													System.out.println("Copying to " + newFile.getAbsolutePath());
													FileUtils.deleteQuietly(oldFile);
												}
												//INVOKE SHELL SCRIPT IF CONFIGURED
												if(scriptCommand != null && !scriptCommand.equals("")) {
													executeScript(newFile.getAbsolutePath());
												}
											}
										}
									}
								} else {
									for(int n=0; n<files.length; n++) {
										String scriptCommand = (String) configProperties.getProperty("script_command");
										if(scriptCommand != null && !scriptCommand.equals("")) {
											executeScript(files[n].getAbsolutePath());
										}
									}
								}
								
							}
						}
						
//						IF FILESOURCE != REALURI DELETE TEMPORARY METALINK FILE
						String filesource = DatabaseUtility.getInstance().getFileNameById(processIdentifier);
						if(!DatabaseUtility.getInstance().getRealUriById(processIdentifier).equals(filesource)) {
							String fileName =  new URL(filesource).getFile();
							File metalinkFile = new File("webapps/" + fileName);
							metalinkFile.delete();
						}
						
						cache.remove(this.processIdentifier);
						//IF ALL FILES COMING FROM THE SAME DAR HAVE BEEN COMPLETED SEND MONITORING URL REQUEST
						String monitoringURL = DatabaseUtility.getInstance().getMonitoringURLByProcessIdentifier(processIdentifier);
						String notificationStatus = "COMPLETED";
						if(status.equals(EDownloadStatus.IN_ERROR)) {
							notificationStatus = "ERROR";
						}
						if(!monitoringURL.equalsIgnoreCase("MANUAL_DOWNLOAD")) {
							sendDarNotification(monitoringURL, notificationStatus, progress);
						}
							
//						UPDATE HOST AND PLUGIN STATISTICS
						DatabaseUtility.getInstance().updateDownloadStatisctics(this.processIdentifier);
						DatabaseUtility.getInstance().updatePluginStatistics(this.processIdentifier);
						DatabaseUtility.getInstance().updateHostStatistics(this.processIdentifier);
			}
		} catch(Exception ex) {
			ex.printStackTrace();
			log.error(ex.getMessage());
		} 
	}
	
	private void executeScript(String filepath) {
		String scriptCommand = (String) configProperties.getProperty("script_command");
		String[] args = scriptCommand.split("\\s+");
		StringBuffer message = new StringBuffer();
		for(int n=0; n<args.length; n++) {
			args[n] = StringEscapeUtils.unescapeXml(args[n].trim());
			if(args[n].contains("<Product_Path>")) {
				args[n] = args[n].replace("<Product_Path>", filepath);
			}
			
			message.append(StringEscapeUtils.unescapeXml(args[n]) + " ");
		}
		log.info("Executing " + message);
		ProcessBuilder pb = new ProcessBuilder(args);
		pb.redirectErrorStream(true);
		Process p = null;
		try {
			p = pb.start();
			BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
			String line;
			do {
			    line = reader.readLine();
			    if (line != null) { log.debug(line); }
			} while (line != null);
			reader.close();

			p.waitFor();
		} catch (Exception e) {
			e.printStackTrace();
			log.error("Can not execute script " + args[0] + " " + e.getMessage());
		}
		
	}
	
	@Override
	public void productDetails(String filename, Integer numFiles, Long totalLength) {
		DatabaseUtility.getInstance().updateProductDetails(processIdentifier, filename, numFiles, totalLength);
	}

	public String getProcessIdentifier() {
		return processIdentifier;
	}

	@Override
	public void progress(Integer progress, Long completedLength, EDownloadStatus status,
			String message, DMPluginException exc) {
		if(exc != null) {
			String[] fatalsEceptions = ((String) configProperties.getProperty("fatalExceptions")).split("\\|\\|");
			if(Arrays.asList(fatalsEceptions).contains(exc.getClass().getSimpleName())) {
				log.fatal("Download Manager Exception: " + exc.getMessage());
			} else {
				log.error("Download Manager Exception: " + exc.getMessage());
			}
		}	
		
		this.progress(progress, completedLength, status, message);
		
	}
	
	private String getProductDownloadNotification() {
    	return "<ngeo:ProductDownloadNotification><ngeo:ProductAccessURL>{product_access_URL}</ngeo:ProductAccessURL>"+
				"<ngeo:productDownloadStatus>{product_download_status}</ngeo:productDownloadStatus>"+
				"<ngeo:productDownloadMessage>{product_download_message}</ngeo:productDownloadMessage>"+
				"<ngeo:productDownloadProgress>{product_download_progress}</ngeo:productDownloadProgress>"+
				"<ngeo:productDownloadSize>{product_download_size}</ngeo:productDownloadSize></ngeo:ProductDownloadNotification>";
    }
	
	private String prepareMonitoringURLRequest(String DMId, String status, Integer progress) {
    	String filecontent = "";
    	try {
	    	FileInputStream wsRequest = new FileInputStream("./templates/DMDARMonitoring.xml");
	    	DataInputStream in = new DataInputStream(wsRequest);
	    	BufferedReader br = new BufferedReader(new InputStreamReader(in));
	    	StringBuffer sb = new StringBuffer();
	    	String strLine;
	    	while((strLine = br.readLine()) != null) {
	    		sb.append(strLine);
	    	}
	    	filecontent = sb.toString();
	    	in.close();
	    	filecontent = filecontent.replace("{DM_ID}", DMId);
	    	filecontent = filecontent.replace("{READY_PRODUCTS_OR_ALL}", "READY");
	    	String productsDownloadNotification = "";
	    	//SELECT FROM DB DOWNLOAD TO NOTIFY TO WS
	    	
	    	String productDownloadNotification = "";
	    
    		productDownloadNotification = this.getProductDownloadNotification();
	    	productDownloadNotification = productDownloadNotification.replace("{product_access_URL}", 
	    			StringEscapeUtils.escapeXml(DatabaseUtility.getInstance().getFileNameById(processIdentifier)));
			productDownloadNotification = productDownloadNotification.replace("{product_download_status}", status);
			productDownloadNotification = productDownloadNotification.replace("{product_download_progress}", String.valueOf(progress));
			productDownloadNotification = productDownloadNotification.replace("{product_download_message}","");
			productDownloadNotification = productDownloadNotification.replace("{product_download_size}", DatabaseUtility.getInstance().getFileSizeById(processIdentifier));
			productDownloadNotification = productsDownloadNotification += productDownloadNotification; 
	    	
	    
			filecontent = filecontent.replace("{products_download_notification}", productsDownloadNotification);
    	} catch(IOException ex) {
    		//ex.printStackTrace();
    		log.error("Error in preparing MonitoringURLRequest " + ex.getMessage());
    	}
    	return filecontent;
			
    }
	
	private void sendDarNotification(String darUrl, String status, Integer progress) {
		UmssoCLInput input = new UmssoCLInput();
		UmssoCLCore clCore = UmssoCLCoreImpl.getInstance();
		
		UmssoHttpPost darMethod = new UmssoHttpPost(darUrl);
	    try {
	    		
	      String DARRequest = this.prepareMonitoringURLRequest(configProperties.getProperty("DMIdentifier"), status, progress);
    	  log.debug("*************Sending complete notification to " + darUrl + "*************");
    	  log.debug(DARRequest);
	      log.debug("*************END OF complete notification to " + darUrl + "*************");
	      StringEntity entity = new StringEntity(DARRequest, ContentType.TEXT_XML);
	      darMethod.setEntity(entity);
    	  darMethod.addHeader("Content-Type", "application/xml");
    	  CommandLineCallback clc = new CommandLineCallback(user, pwd.toCharArray(), null);
    	  input.setVisualizerCallback(clc);
    	  input.setAppHttpMethod(darMethod);
		  clCore.processHttpRequest(input);
		  UmssoHttpResponse response = darMethod.getHttpResponseStore().getHttpResponse();
		  byte[] responseBody = response.getBody();
		  log.debug("*************Response for complete notification to " + darUrl + "*************");
    	  log.debug(new String(responseBody));
	      log.debug("*************END OF response for complete notification to " + darUrl + "*************");
		  
	    }  catch(Exception e) {
	        log.error("Can not get DARs");
	    } finally {
	    	darMethod.releaseConnection();
//	    	clCore.getUmssoHttpClient().getCookieStore().clear();
	    } 
    }
}
