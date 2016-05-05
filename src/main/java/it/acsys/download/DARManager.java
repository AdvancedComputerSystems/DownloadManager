package it.acsys.download;

import int_.esa.eo.ngeo.downloadmanager.exception.DMPluginException;
import int_.esa.eo.ngeo.downloadmanager.plugin.IDownloadProcess;
import it.acsys.download.ngeo.DARParser;
import it.acsys.download.ngeo.database.DatabaseUtility;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import javax.servlet.ServletContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.conn.PoolingClientConnectionManager;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.siemens.pse.umsso.client.UmssoCLCore;
import com.siemens.pse.umsso.client.UmssoCLCoreImpl;
import com.siemens.pse.umsso.client.UmssoCLInput;
import com.siemens.pse.umsso.client.UmssoHttpPost;
import com.siemens.pse.umsso.client.util.UmssoHttpResponse;

public class DARManager {
	private ServletContext context;
	private static Logger log = Logger.getLogger(DARManager.class);
	private static UmssoCLInput input = new UmssoCLInput();
	private static UmssoCLCore clCore = UmssoCLCoreImpl.getInstance();
	private static CommandLineCallback clc;
	private static Properties usersConfigProperties = null;
	private static String usersConfigPath = ConfigUtility.loadConfig().getProperty("usersConfigPath");
	
	private static String pwd;
	private static String user;
	private static final String userLoginMessage = "Wrong SSO username/password.\n Please edit the configuration properties and restart the service.";
	
	static {
        try {
        	
        	usersConfigProperties = new Properties();
        	File usersConfigFile = new File(usersConfigPath);
        	InputStream usersStream  = new FileInputStream(usersConfigFile);
        	usersConfigProperties.load(usersStream);
        	usersStream.close();
        	user = usersConfigProperties.getProperty("umssouser");
        	AESCryptUtility aesCryptUtility = new AESCryptUtility();
        	pwd = aesCryptUtility.decryptString((String) usersConfigProperties.getProperty("umssopwd"));
        	
        	
        } catch (Exception e) {
            log.error("Can not initialize DAR MANAGER USER/PWD " + e.getMessage());
        }
	}
	
	
	
	public DARManager(ServletContext context) {
		this.context = context;
		clc = new CommandLineCallback(user, pwd.toCharArray(), context);
		((PoolingClientConnectionManager) clCore.getUmssoHttpClient().getConnectionManager()).setDefaultMaxPerRoute(150);;
	    ((PoolingClientConnectionManager) clCore.getUmssoHttpClient().getConnectionManager()).setMaxTotal(200);
		LogUtility.setLevel(log);
	}
	
	public ArrayList<String> getMonitoringURLs(String monitoringURL, String DMId, String dateTime) {
		log.debug("*************getMonitoringURLs*************");
    	log.info("Contacting " + monitoringURL + "/monitoringURL/?format=xml");
		Properties configProperties = ConfigUtility.loadConfig();
    	UmssoHttpPost monitoringMethod = new UmssoHttpPost(monitoringURL + "/monitoringURL/?format=xml");
	    
	    ArrayList<String> monitoringURLs = new ArrayList<String>();
	    String ssoStatus = (String) context.getAttribute("SSO_LOGIN_STATUS");
  	  	System.out.println("ssoStatus " + ssoStatus);
	      if(ssoStatus != null && ssoStatus.equals("LOGINFAILED")) {
	    	  log.error("");
  		  return monitoringURLs;
        }
	    try{
    	  FileInputStream monitoringFile = new FileInputStream("./templates/DMMonitoringURL.xml");
    	  DataInputStream in = new DataInputStream(monitoringFile);
    	  BufferedReader br = new BufferedReader(new InputStreamReader(in));
    	  StringBuffer sb = new StringBuffer();
    	  String strLine;
    	  while((strLine = br.readLine()) != null) {
    		  sb.append(strLine);
    	  }
    	  String filecontent = sb.toString();
    	  in.close();
    	  filecontent = filecontent.replace("{DM_ID}", DMId);
    	  filecontent = filecontent.replace("{DM_SET_TIME}", dateTime);
    	  StringEntity entity = new StringEntity(filecontent, ContentType.TEXT_XML);
    	  monitoringMethod.setEntity(entity);
    	  log.debug("*************MonitoringURLs request*************");
    	  log.debug(filecontent);
    	  log.debug("*************End MonitoringURLs request*************");
    	  monitoringMethod.addHeader("Content-Type", "application/xml");
    	  
    	  clc = new CommandLineCallback(user, pwd.toCharArray(), context);
      	  input.setVisualizerCallback(clc);
    	  input.setAppHttpMethod(monitoringMethod);
    	  
    	  
    	  clCore.processHttpRequest(input);
    	  org.apache.http.Header[] headers = monitoringMethod.getHttpResponseStore().getHttpResponse().getHeaders();
		  String serverDate = null;
		  for(int n=0; n<headers.length; n++) {
			  if(headers[n].getName().equalsIgnoreCase("Date")) {
					 serverDate = headers[n].getValue();
					 break;
				 }
		  }
		  System.out.println("serverDate " + serverDate);
		  long serverTime = 0l;
		  if(serverDate != null) {
				 SimpleDateFormat dateFormatter = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH);
				 java.util.Date date = dateFormatter.parse(serverDate);
				 serverTime = date.getTime();
		  }
		  
		  System.out.println("Formatted serverDate " + serverTime);
		  
		  String dmIdentifier = configProperties.getProperty("DMIdentifier");
		  
		  DatabaseUtility.getInstance().updateMonitoringUrlTime(monitoringURL, serverTime, dmIdentifier);
    	  
		  UmssoHttpResponse response =
				  monitoringMethod.getHttpResponseStore().getHttpResponse();
		  byte[] responseBody = response.getBody();
		  monitoringMethod.releaseConnection();
	      log.debug("*************MonitoringURLs response*************");
	      log.debug(new String(responseBody));
	      log.debug("*************End MonitoringURLs response*************");
	      DatabaseUtility.getInstance().updateLastCall(monitoringURL, new Timestamp(System.currentTimeMillis()), dmIdentifier);
	      DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
	      DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
		  Document doc = dBuilder.parse(new ByteArrayInputStream(responseBody));
		  
		  XPathFactory xpf = XPathFactory.newInstance();
		  XPath xpath = xpf.newXPath();
		  
		  Node userOrder = (Node) xpath.evaluate("/MonitoringURL-Resp/UserOrder", doc, XPathConstants.NODE);
		  if(userOrder != null && userOrder.getTextContent().equals("STOP")) {
			  log.debug("Received STOP from  " + monitoringURL);
			  stopDownloads(DatabaseUtility.getInstance().getWsId(monitoringURL, dmIdentifier), "STOP");
		  } else if(userOrder != null && userOrder.getTextContent().equals("STOP_IMMEDIATELY")) {
			  stopDownloads(DatabaseUtility.getInstance().getWsId(monitoringURL, dmIdentifier), "STOP_IMMEDIATELY");
			  log.debug("Received STOP_IMMEDIATELY from  " + monitoringURL);
		  } else {
			  NodeList nodeList = (NodeList) xpath.evaluate("/MonitoringURL-Resp/MonitoringURLList/MonitoringURL", doc, XPathConstants.NODESET);
			  for(int x=0; x<nodeList.getLength(); x++) {
				  log.debug("URL to be monitored " + nodeList.item(x).getTextContent());
				  monitoringURLs.add(nodeList.item(x).getTextContent()+ "?format=xml");
			  }
			  
			  Node refreshNode = (Node) xpath.evaluate("/MonitoringURL-Resp/RefreshPeriod", doc, XPathConstants.NODE);
			  if(refreshNode != null) {
				  DatabaseUtility.getInstance().updateRefreshTime(monitoringURL, Long.valueOf(refreshNode.getTextContent())*1000, dmIdentifier);
			  }
		  }  
	    }  catch(Exception e) {
	    	e.printStackTrace();
	        log.debug("Can not get monitoring URls");
	    } finally {
	    	monitoringMethod.releaseConnection();
//	    	clCore.getUmssoHttpClient().getCookieStore().clear();
	    } 
	    
	    return monitoringURLs;
	      
    }
	
	private void stopDownloads(int wsId, String stopType) {
    	Map<String, Integer> downloads = DatabaseUtility.getInstance().getDownloadsToStop(wsId, stopType);
    	for(String gid : downloads.keySet()) {
    		HashMap<String, IDownloadProcess> cache = (HashMap<String, IDownloadProcess>) context.getAttribute("cache");
			IDownloadProcess down = (IDownloadProcess) cache.get(gid);
			try {
				down.cancelDownload();
			} catch(DMPluginException ex) {
				ex.printStackTrace();
				log.error(ex.getMessage());
			}
			int statusId = downloads.get(gid);
			if(statusId == 3) {
				try {
					down.resumeDownload();
					down.cancelDownload();
				} catch(DMPluginException ex) {
					ex.printStackTrace();
					log.error(ex.getMessage());
				}
			}
			if(statusId == 2 || statusId == 3) {
//				UPDATING KILL DOWNLOAD STATUS
				 DatabaseUtility.getInstance().killDownload(gid);
			}
    	}
//		UPDATING WS STATUS
    	DatabaseUtility.getInstance().stopWS(wsId);
    }
	
	public String downloadDAR(String darUrl, int wsId) {
	    UmssoHttpPost darMethod = new UmssoHttpPost(darUrl);
	    String darStatus = "IN_PROGRESS";
	    String ssoStatus = (String) context.getAttribute("SSO_LOGIN_STATUS");
	      if(ssoStatus != null && ssoStatus.equals("LOGINFAILED")) {
	    	  log.error(userLoginMessage);
  		  return "";
        }
	    try {
	      Properties configProperties = ConfigUtility.loadConfig();
	      String DARRequest = this.prepareMonitoringURLRequest(darUrl, configProperties.getProperty("DMIdentifier"));
	      log.info("Performing DataAccessMonitoring request to " + darUrl);
	      log.debug("*************DataAccessMonitoring request to " + darUrl + "*************");
    	  log.debug(DARRequest);
	      log.debug("*************DataAccessMonitoring request to " + darUrl + "*************");
	      StringEntity entity = new StringEntity(DARRequest, ContentType.TEXT_XML);
	      darMethod.setEntity(entity);
    	  darMethod.addHeader("Content-Type", "application/xml");
    	  clc = new CommandLineCallback(user, pwd.toCharArray(), context);
      	  input.setVisualizerCallback(clc);
    	  input.setAppHttpMethod(darMethod);
	      String conTimeOut = configProperties.getProperty("connectionTimeOut");
		  int timeout = 6000;
		  if(conTimeOut != null) {
				timeout = Integer.valueOf(conTimeOut);
		  }
		  clCore.getUmssoHttpClient().getParams().setParameter(CoreConnectionPNames.CONNECTION_TIMEOUT, timeout);
		  clCore.getUmssoHttpClient().getParams().setParameter(CoreConnectionPNames.SO_TIMEOUT, timeout);
    	  
		  clCore.processHttpRequest(input);
	      //if(!DatabaseUtility.getInstance().checkDARDownloading(darUrl)) {
	    	  UmssoHttpResponse response = darMethod.getHttpResponseStore().getHttpResponse();
	    	  byte[] responseBody = response.getBody();
		      log.debug("*************DataAccessMonitoring response from " + darUrl + "*************");
		      log.debug(new String(responseBody));
		      log.debug("*************DataAccessMonitoring response from " + darUrl + "*************");
		      FileWriter fstream = new FileWriter(((String) configProperties.getProperty("DARsDir"))+"/DAR"+"_"+System.currentTimeMillis());
		      BufferedWriter out = new BufferedWriter(fstream);
		      out.write(new String(responseBody));
		      out.close();
		      DARParser darParser = new DARParser();
		      darStatus = darParser.parse(new String(responseBody),darUrl, wsId, context);
//	     } else {
//	    	 log.info("The download of DAR " +  darUrl + " is already running");
//	     } 
	    }  catch(Exception e) {
	    	e.printStackTrace();
	        log.error("Can not get DARs");
	    } finally {
	    	darMethod.releaseConnection();
//	    	clCore.getUmssoHttpClient().getCookieStore().clear();
	    }
	    
	    return darStatus;
    }
	
	private String prepareMonitoringURLRequest(String monitoringURL, String DMId) {
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
	    	//SELECT FROM DB DOWNLOAD TO NOTIFY TO WS
	    	List<Map<String, String>> information = DatabaseUtility.getInstance().getDARInformation(monitoringURL);
	    	
	    	StringBuffer productDownloadNotification = new StringBuffer();
	    	for(Map<String, String> currInf : information) {
		    	if(currInf.keySet().size() >0) {
		    		String currProductDownloadNotification = this.getProductDownloadNotification();
			    	String statusValue = currInf.get("product_download_status");
			    	currProductDownloadNotification = currProductDownloadNotification.replace("{product_access_URL}",currInf.get("product_access_URL"));
			    	currProductDownloadNotification = currProductDownloadNotification.replace("{product_download_status}",statusValue);
			    	currProductDownloadNotification = currProductDownloadNotification.replace("{product_download_progress}",currInf.get("product_download_progress"));
			    	currProductDownloadNotification = currProductDownloadNotification.replace("{product_download_size}",currInf.get("product_download_size"));
			    	currProductDownloadNotification = currProductDownloadNotification.replace("{product_download_message}","");
			    	if(statusValue.equals("CANCELLED") || statusValue.equals("COMPLETED") || statusValue.equals("ERROR")) {
			    		DatabaseUtility.getInstance().updateNotificationStatus(currInf.get("product_identifier"));
			    	}
			    	productDownloadNotification.append(currProductDownloadNotification);
		    	}
	    	}
	    	
			filecontent = filecontent.replace("{products_download_notification}", productDownloadNotification.toString());
    	} catch(IOException ex) {
    		//ex.printStackTrace();
    		log.error("Error in preparing MonitoringURLRequest " + ex.getMessage());
    	}
    	return filecontent;
			
    }
	
	private String getProductDownloadNotification() {
    	return "<ngeo:ProductDownloadNotification><ngeo:ProductAccessURL>{product_access_URL}</ngeo:ProductAccessURL>"+
				"<ngeo:productDownloadStatus>{product_download_status}</ngeo:productDownloadStatus>"+
				"<ngeo:productDownloadMessage>{product_download_message}</ngeo:productDownloadMessage>"+
				"<ngeo:productDownloadProgress>{product_download_progress}</ngeo:productDownloadProgress>"+
				"<ngeo:productDownloadSize>{product_download_size}</ngeo:productDownloadSize></ngeo:ProductDownloadNotification>";
    }
}
