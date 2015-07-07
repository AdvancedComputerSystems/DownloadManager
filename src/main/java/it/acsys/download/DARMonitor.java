package it.acsys.download;

import int_.esa.eo.ngeo.downloadmanager.exception.DMPluginException;
import int_.esa.eo.ngeo.downloadmanager.plugin.IDownloadPlugin;
import int_.esa.eo.ngeo.downloadmanager.plugin.IDownloadPluginInfo;
import int_.esa.eo.ngeo.downloadmanager.plugin.IDownloadProcess;
import it.acsys.download.ngeo.DARParser;
import it.acsys.download.ngeo.DownloadAction;
import it.acsys.download.ngeo.DownloadManagerJob;
import it.acsys.download.ngeo.database.DatabaseUtility;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.KeyStore;
import java.sql.Date;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.StringTokenizer;
import java.util.TimeZone;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.httpclient.contrib.ssl.EasySSLProtocolSocketFactory;
import org.apache.commons.httpclient.protocol.Protocol;
import org.apache.commons.httpclient.protocol.ProtocolSocketFactory;
import org.apache.commons.io.input.Tailer;
import org.apache.commons.io.input.TailerListenerAdapter;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.http.Header;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.SimpleTrigger;
import org.quartz.TriggerBuilder;
import org.quartz.impl.StdSchedulerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.siemens.pse.umsso.client.UmssoCLCore;
import com.siemens.pse.umsso.client.UmssoCLCoreImpl;
import com.siemens.pse.umsso.client.UmssoCLEnvironment;
import com.siemens.pse.umsso.client.UmssoCLInput;
import com.siemens.pse.umsso.client.UmssoHttpGet;
import com.siemens.pse.umsso.client.UmssoHttpPost;
import com.siemens.pse.umsso.client.util.UmssoHttpResponse;


/**
 * Application Lifecycle Listener implementation class DARMonitor
 *
 */
public class DARMonitor implements ServletContextListener {
	private static Logger log = Logger.getLogger(DARMonitor.class);
	private RetrieveDARURLsThread retrieveDAR = null;
	private RegisterDMThread registerDMThread = null;
	private Long refreshPeriod = 60000L;
	private static HashMap<String, IDownloadProcess> cache = null;
	private Scheduler downloadScheduler = null;
	private static String usersConfigPath = "./etc/users.properties";
	
	private static UmssoCLInput input = new UmssoCLInput();
	private static UmssoCLCore clCore = UmssoCLCoreImpl.getInstance();
	private static CommandLineCallback clc;
	
	private static Properties configProperties = null;
	private static Properties usersConfigProperties = null;
	
	private static String pwd;
	private static String user;
	
	private static Properties defaultConfig = null;
	
	private static ServletContext context = null;
	
	private static final String userLoginMessage = "Wrong SSO username/password.\n Please edit the configuraion properties and restart the service.";

	static {
        try {
        	String configPath = System.getProperty("configPath"); 
        	File configFile = new File(configPath);
        	InputStream stream  = new FileInputStream(configFile);
        	configProperties = new Properties();
        	configProperties.load(stream);
        	
        	
        	usersConfigProperties = new Properties();
        	File usersConfigFile = new File(usersConfigPath);
        	InputStream usersStream  = new FileInputStream(usersConfigFile);
        	usersConfigProperties.load(usersStream);
        	usersStream.close();
        	user = usersConfigProperties.getProperty("umssouser");
        	AESCryptUtility aesCryptUtility = new AESCryptUtility();
        	pwd = aesCryptUtility.decryptString((String) usersConfigProperties.getProperty("umssopwd"));
        	
        	stream.close();
        	
        	stream  = new FileInputStream("./templates/defaultConfiguration.properties");
        	defaultConfig = new Properties();
        	defaultConfig.load(stream);
        	stream.close();
        	
        	
        	if(Boolean.valueOf(configProperties.getProperty("acceptCertificate"))) {
        		log.debug("accepting certificates");
        		KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
	            trustStore.load(null, null);
	
	            SSLSocketFactory sf = new MySSLSocketFactory(trustStore);
	            sf.setHostnameVerifier(SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
	
	            
	            clCore.getUmssoHttpClient().getConnectionManager().getSchemeRegistry().register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
	        	clCore.getUmssoHttpClient().getConnectionManager().getSchemeRegistry().register(new Scheme("https", sf, 443));
        	}
        	
        } catch (Exception e) {            
            log.error("Can not initialize DAR monitor " + e.getMessage());
        }
	}
	
    public DARMonitor() {
        super();
    }
    
    private ArrayList<String> getMonitoringURLs(String monitoringURL, String DMId, String dateTime) {
    	log.debug("*************getMonitoringURLs*************");
    	log.info("Contacting " + monitoringURL + "/monitoringURL/?format=xml");
    	//PostMethod monitoringMethod = new PostMethod(monitoringURL + "/monitoringURL?format=xml");
    	
    	UmssoHttpPost monitoringMethod = new UmssoHttpPost(monitoringURL + "/monitoringURL/?format=xml");
	    
	    ArrayList<String> monitoringURLs = new ArrayList<String>();
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
    	  input.setAppHttpMethod(monitoringMethod);
    	  String ssoStatus = (String) context.getAttribute("SSO_LOGIN_STATUS");
	      if(ssoStatus != null && ssoStatus.equals("LOGINFAILED")) {
	    	  log.error(userLoginMessage);
    		  return monitoringURLs;
          }
    	  
    	  clCore.processHttpRequest(input);
    	  Header[] headers = monitoringMethod.getHttpResponseStore().getHttpResponse().getHeaders();
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
	    } 
	    
	    return monitoringURLs;
	      
    }
    
    private void stopDownloads(int wsId, String stopType) {
    	Map<String, Integer> downloads = DatabaseUtility.getInstance().getDownloadsToStop(wsId, stopType);
    	for(String gid : downloads.keySet()) {
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
    
    private void registerDM(String DMregistrationURL, String DMId, String DMFriendlyName) {
    	UmssoHttpPost registrationMethod = new UmssoHttpPost(DMregistrationURL + "/DMRegistrationMgmnt?format=xml");
	    try{	      
    	  FileInputStream registrationFile = new FileInputStream("./templates/DMRegistration.xml");
    	  DataInputStream in = new DataInputStream(registrationFile);
    	  BufferedReader br = new BufferedReader(new InputStreamReader(in));
    	  StringBuffer sb = new StringBuffer();
    	  String strLine;
    	  while((strLine = br.readLine()) != null) {
    		  sb.append(strLine);
    	  }
    	  String filecontent = sb.toString();
    	  in.close();
    	  filecontent = filecontent.replace("{DM_ID}", DMId);
    	  filecontent = filecontent.replace("{DM_FRIENDLY_NAME}", DMFriendlyName);
    	  StringEntity entity = new StringEntity(filecontent, ContentType.TEXT_XML);
    	  registrationMethod.setEntity(entity);
    	  
	      log.info("Registering to " + DMregistrationURL + "/DMRegistrationMgmnt?format=xml");
	      log.debug("*************DMRegistration request*************");
	      log.debug(filecontent);
	      log.debug("*************End DMRegistration request*************");
	      registrationMethod.addHeader("Content-Type", "application/xml");
	      input.setAppHttpMethod(registrationMethod);
	      String ssoStatus = (String) context.getAttribute("SSO_LOGIN_STATUS");
	      if(ssoStatus != null && ssoStatus.equals("LOGINFAILED")) {
    		  log.error(userLoginMessage);
	    	  return;
          }
		  
	      clCore.processHttpRequest(input);
		  UmssoHttpResponse response = registrationMethod.getHttpResponseStore().getHttpResponse();
		  byte[] responseBody = response.getBody();
		  registrationMethod.releaseConnection();
	      
	      log.debug("*************DMRegistration response*************");
	      log.debug(new String(responseBody));
	      log.debug("*************End DMRegistration response*************");	      
	      DatabaseUtility.getInstance().initializeMonitoringUrl(DMregistrationURL,configProperties.getProperty("DMIdentifier"));
	    }  catch(Exception e) {
	    	e.printStackTrace();
	        log.error("Can not register DM");
	    } finally {
	    	registrationMethod.releaseConnection();
	    }
	      
    }
    
    private void downloadDAR(String darUrl, int wsId) {
	    UmssoHttpPost darMethod = new UmssoHttpPost(darUrl);
	    try {
	    		
	      String DARRequest = this.prepareMonitoringURLRequest(darUrl, configProperties.getProperty("DMIdentifier"));
	      log.info("Performing DataAccessMonitoring request to " + darUrl);
	      log.debug("*************DataAccessMonitoring request to " + darUrl + "*************");
    	  log.debug(DARRequest);
	      log.debug("*************DataAccessMonitoring request to " + darUrl + "*************");
	      StringEntity entity = new StringEntity(DARRequest, ContentType.TEXT_XML);
	      darMethod.setEntity(entity);
    	  darMethod.addHeader("Content-Type", "application/xml");
    	  input.setAppHttpMethod(darMethod);
    	  String ssoStatus = (String) context.getAttribute("SSO_LOGIN_STATUS");
	      if(ssoStatus != null && ssoStatus.equals("LOGINFAILED")) {
	    	  log.error(userLoginMessage);
    		  return;
          }
    	  
		  clCore.processHttpRequest(input);
	      if(!DatabaseUtility.getInstance().checkDARDownloading(darUrl)) {
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
		      darParser.parse(new String(responseBody),darUrl, wsId, context);
	     } else {
	    	 log.info("The download of DAR " +  darUrl + " is already running");
	     } 
	    }  catch(Exception e) {
	    	e.printStackTrace();
	        log.error("Can not get DARs");
	    } finally {
	    	darMethod.releaseConnection();
	    } 
    }
    
    
    public void contextInitialized(ServletContextEvent arg0) {
    	
    	arg0.getServletContext().setAttribute("logBuffer", new StringBuffer());
    	//Start TAILER thread on log file
    	MyTailListener listener = new MyTailListener(arg0.getServletContext());
    	Tailer tailer = new Tailer(new File("./log/downloadmanager.log"), listener, 500);
    	final Thread thread = new Thread(tailer);
        thread.start();
        
    	 String[] values = new String[]{"WebServiceURLs"};
		 for (int n=0; n<values.length; n++) {
			 if(!configProperties.containsKey(values[n])) {
				 log.error("Configuration file does not contain key " + values[n] + "\n");
				 log.error("Download Manager cannot start");
				 System.exit(0);
				 return;
			 }
		 }
		 
    	 values = new String[]{"loglevel", "DMIdentifier", "repositoryDir"};
		 for (int n=0; n<values.length; n++) {
			 if(!configProperties.containsKey(values[n])) {
				 log.error("Configuration file does not contain key " + values[n] + "\n");
				 configProperties.setProperty(values[n], defaultConfig.getProperty(values[n]));
				 try {
					configProperties.save(new FileOutputStream(System.getProperty("configPath")), "");
				} catch (FileNotFoundException e) {
					//e.printStackTrace();
					log.error("Can not save default configuration values " + e.getMessage());
				}
				 log.error("Default value " + defaultConfig.getProperty(values[n]) + " has been set for " + values[n]);
			 }
		 }
//		 
    	String proxy_host = configProperties.getProperty("proxy_host");
		if(proxy_host != null && !proxy_host.equals("")) {
			clCore.init(new UmssoCLEnvironment(proxy_host, Integer.valueOf(configProperties.getProperty("proxy_port")), configProperties.getProperty("proxy_user"), configProperties.getProperty("proxy_pwd")));
		}
    	
    	context = arg0.getServletContext();
    	clc = CommandLineCallback.getInstance(user, pwd.toCharArray(), context);
    	input.setVisualizerCallback(clc); 
    	context.setAttribute("DM_ID", configProperties.getProperty("DMIdentifier"));
    	context.setAttribute("initialTime", System.currentTimeMillis());
    	cache = new HashMap<String, IDownloadProcess>(); 
    	context.setAttribute("cache", cache);
    	log.setLevel(Level.toLevel(configProperties.getProperty("loglevel")));
    	
    	JobDetail downloadJob = JobBuilder.newJob(DownloadManagerJob.class).withIdentity("DownloadManagerJob", "group").build();
    	downloadJob.getJobDataMap().put(DownloadManagerJob.USERS_CONFIG_PROPERTIES, usersConfigProperties);
    	downloadJob.getJobDataMap().put(DownloadManagerJob.JCS_CACHE, cache);
    	downloadJob.getJobDataMap().put(DownloadManagerJob.SERVLET_CONTEXT, context);
    	SimpleTrigger downloadTrigger = TriggerBuilder
    			.newTrigger()
    			.withIdentity("DownloadTrigger", "group")
    			.withSchedule(
    			    SimpleScheduleBuilder.simpleSchedule()
    				.withIntervalInSeconds(4).repeatForever())
    			.build();
    	
    	try {
	    	downloadScheduler = new StdSchedulerFactory().getScheduler();
	    	downloadScheduler.start();
	    	downloadScheduler.scheduleJob(downloadJob, downloadTrigger);
    	} catch(SchedulerException ex) {
    		log.error(ex.getMessage());
//    		ex.printStackTrace();
    	}    	
    	
//    	START THE RETRIEVE DAR THREAD
    	//initializeRegistrationUrls();
    	registerDMThread = new RegisterDMThread();
    	registerDMThread.start();
    	
        retrieveDAR= new RetrieveDARURLsThread();
        retrieveDAR.start();
        
        //resumeDownloads();
        ResumeThread resumeThread = new ResumeThread();
        resumeThread.start();
    }
    
    private void initializeRegistrationUrls() {
//    	DatabaseUtility.getInstance().resetWSURLs();
    	configProperties = ConfigUtility.loadConfig();
    	String WebServiceURLs = (String) configProperties.get("WebServiceURLs");
    	log.debug("WebServiceURLs " + WebServiceURLs);
    	StringTokenizer tokenizer = new StringTokenizer(WebServiceURLs, ",");
    	DatabaseUtility.getInstance().resetWsUrlActive();
    	while(tokenizer.hasMoreTokens()) {
    		String currRegistrationURL = tokenizer.nextToken().trim();
    		DatabaseUtility.getInstance().updateDMRegLastCall(currRegistrationURL, new Timestamp(System.currentTimeMillis()), configProperties.getProperty("DMIdentifier"));
    	}
    }
    
    private String getProductDownloadNotification() {
    	return "<ngeo:ProductDownloadNotification><ngeo:ProductAccessURL>{product_access_URL}</ngeo:ProductAccessURL>"+
				"<ngeo:productDownloadStatus>{product_download_status}</ngeo:productDownloadStatus>"+
				"<ngeo:productDownloadMessage>{product_download_message}</ngeo:productDownloadMessage>"+
				"<ngeo:productDownloadProgress>{product_download_progress}</ngeo:productDownloadProgress>"+
				"<ngeo:productDownloadSize>{product_download_size}</ngeo:productDownloadSize></ngeo:ProductDownloadNotification>";
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
	    	String productsDownloadNotification = "";
	    	//SELECT FROM DB DOWNLOAD TO NOTIFY TO WS
	    	Map<String, String> information = DatabaseUtility.getInstance().getDARInformation(monitoringURL);
	    	String productDownloadNotification = "";
	    	if(information.keySet().size() >0) {
	    		productDownloadNotification = this.getProductDownloadNotification();
		    	String statusValue = information.get("product_download_status");
		    	productDownloadNotification = productDownloadNotification.replace("{product_access_URL}",information.get("product_access_URL"));
				productDownloadNotification = productDownloadNotification.replace("{product_download_status}",statusValue);
				productDownloadNotification = productDownloadNotification.replace("{product_download_progress}",information.get("product_download_progress"));
				productDownloadNotification = productDownloadNotification.replace("{product_download_size}",information.get("product_download_size"));
				productDownloadNotification = productDownloadNotification.replace("{product_download_message}","");
				productDownloadNotification = productsDownloadNotification += productDownloadNotification; 
		    	if(statusValue.equals("CANCELLED") || statusValue.equals("COMPLETED") || statusValue.equals("ERROR")) {
		    		DatabaseUtility.getInstance().upadteNotificationStatus(information.get("product_identifier"));
		    	}
	    	}
			filecontent = filecontent.replace("{products_download_notification}", productsDownloadNotification);
    	} catch(IOException ex) {
    		//ex.printStackTrace();
    		log.error("Error in preparing MonitoringURLRequest " + ex.getMessage());
    	}
    	return filecontent;
			
    }
    	
    public void contextDestroyed(ServletContextEvent arg0) {
    	retrieveDAR.interrupt();
    	registerDMThread.interrupt();
    	try {
			downloadScheduler.deleteJob(new JobKey("DownloadManagerJob","group"));
		} catch (SchedulerException e1) {
			//e1.printStackTrace();
			log.error("Error in destrying downloadScheduler " + e1.getMessage());
		}
    	for(Object gid : cache.keySet()) {
	    	IDownloadProcess process = (IDownloadProcess) cache.get(gid);
	    	try {
				process.disconnect();
			} catch (DMPluginException e) {
//				e.printStackTrace();
				log.error("Error in disconnecting process " + gid + " " + e.getMessage());
			}	    	
    	}
    	
//    	SHUTDOWN PLUGINS
    	try {
    		File pluginDir = new File("./plugins");
    		File[] plugins = pluginDir.listFiles();
        	URLClassLoader cl = null;
        	URL[] jarfile = new URL[plugins.length];
        	for(int n=0; n< plugins.length; n++) {
        		jarfile[n] = new URL("jar", "","file:" + plugins[n].getAbsolutePath()+"!/");
        	}
        	cl = URLClassLoader.newInstance(jarfile, Thread.currentThread().getContextClassLoader());
      	  
      	  
        	ServiceLoader<IDownloadPlugin> pluginClasses = ServiceLoader.load(IDownloadPlugin.class, cl);
        	Iterator<IDownloadPlugin> iter = pluginClasses.iterator();
        	while(iter.hasNext())  {
      		  IDownloadPlugin curr = iter.next();
      		  curr.terminate();
        	} 
    	} catch(DMPluginException e) {
			  log.error("Plugins  already terminated");
		} catch(MalformedURLException e) {
			  log.error("Plugins  already terminated");
		}
    	
    }
    class ResumeThread extends Thread {
    	public void run() {
    		try {
				Thread.sleep(10000);
			} catch (InterruptedException e2) {
				e2.printStackTrace();
			}
    		Map<String, String> downloads = DatabaseUtility.getInstance().getDownloadsToResume();
    		Map<String, String> pausedDownloads = DatabaseUtility.getInstance().getPausedToResume();
    		Map<String, String> all = new HashMap<String, String>();
            all.putAll(downloads);
            all.putAll(pausedDownloads);
    		
            for(String gid : all.keySet()) {
            	String url = all.get(gid);
    	    	String pluginsConf = (String) configProperties.get("PluginClasses");
    	  		StringTokenizer tokenizer = new StringTokenizer(pluginsConf,"||");
    	  		LinkedHashMap<String, String> pluginsMap = new LinkedHashMap<String, String>();
    	  		while(tokenizer.hasMoreTokens()) {
    	  			String currentPlug = tokenizer.nextToken();
    	  			StringTokenizer tokenizer2 = new StringTokenizer(currentPlug,"@@");
    	  			String t1 = tokenizer2.nextToken();
    	  			String t2 = tokenizer2.nextToken();
    	  			pluginsMap.put(t1, t2);
    	  		}
    	  		
    	  		File pluginDir = new File("./plugins");
    	  		File[] plugins = pluginDir.listFiles();
    	  		URLClassLoader cl = null;
    	  		URL[] jarfile = new URL[plugins.length];
    	  		for(int n=0; n< plugins.length; n++) {
    	  			try {
    	  				jarfile[n] = new URL("jar", "","file:" + plugins[n].getAbsolutePath()+"!/");
    	  			} catch (MalformedURLException e) {
    	  				log.error(e.getMessage());
    	  				e.printStackTrace();
    	  			}
    	  		}
    	    	String pluginClass = "";
    	    	cl = URLClassLoader.newInstance(jarfile, Thread.currentThread().getContextClassLoader()); 
    	    	for(String type : pluginsMap.keySet()) {
    				if(url.trim().contains(type)) {
    					pluginClass = pluginsMap.get(type);
    					break;
    				}
    			}
//        	 
    	    	IDownloadPlugin currPlugin = null;
    				ServiceLoader<IDownloadPlugin> pluginClasses = ServiceLoader.load(IDownloadPlugin.class, cl);
    		  	  	Iterator<IDownloadPlugin> availablePulgins = pluginClasses.iterator();
    				while(availablePulgins.hasNext())  {
    					IDownloadPlugin curr = availablePulgins.next();
    					if(curr.getClass().getName().equals(pluginClass)) {
    						currPlugin = curr;
    						break;
    					}
    				}
    				if(currPlugin != null) {
    						try {
    						  	  
    				    	  String configPath = System.getProperty("configPath"); 
    				          File configFile = new File(configPath);
    				    	  IDownloadPluginInfo pluginInfo = currPlugin.initialize(new File (System.getProperty("java.io.tmpdir")),configFile);
    				    	  
    				    	  DMProductDownloadListener listener = new DMProductDownloadListener(gid, cache);
    				    	  String user = (String) usersConfigProperties.get("umssouser");
    		    			 
    				    	  AESCryptUtility aesCryptUtility = new AESCryptUtility();
    				    	  String password  = aesCryptUtility.decryptString((String) usersConfigProperties.getProperty("umssopwd"));
    				        	
//    				    	  UPDATE DOWNLAOD STATUS IMMEDIATLY
    				    	  DatabaseUtility.getInstance().updateDownloadStatus(gid, 1);
//    					    	  UPDATE PLUGIN NAME AND STATISTICS INFORMATION
    				    	  DatabaseUtility.getInstance().updateDownloadStatisctics(gid, pluginInfo.getName());
    				    	  File repositoryDir = null;
    				    	  String productDownloadDir = DatabaseUtility.getInstance().getProductDownloadDir(gid);
    				    	  if(Boolean.valueOf(configProperties.getProperty("isTemporaryDir")) ){
    				    		  repositoryDir = new File(configProperties.getProperty("repositoryDir") + File.separator  + "TEMPORARY" + File.separator + productDownloadDir );
    				    	  } else {
    				    		  repositoryDir = new File(configProperties.getProperty("repositoryDir") + File.separator + productDownloadDir);
    				    	  }
    				    	  if(!repositoryDir.exists()) {
    				    		  repositoryDir.mkdirs();
    				    	  }
    				    	  
    		    			  IDownloadProcess downProcess = currPlugin.createDownloadProcess(new URI(url.trim()), repositoryDir, user, password, listener, "", 0, "", "");
//    				    	  String processIdentifier = listener.getProcessIdentifier();
//    				    	  log.debug("processIdentifier " + processIdentifier);
    				    	  cache.put(gid, downProcess);
    				    	  if(pausedDownloads.keySet().contains(gid)) {
    				    		  downProcess.pauseDownload();
    				    	  }
    				    	  
    				      } catch(Exception ex) {
    				    	  ex.printStackTrace();
    				    	  log.debug("Cannot resume download " + url.trim() + ex.getMessage());
    				    	  DatabaseUtility.getInstance().updateDownloadStatus(gid, 4);
    				      }
    				}
    	    	}
            try {
    			Thread.sleep(10000);
    		} catch (InterruptedException e1) {
    			e1.printStackTrace();
    		}
            Map<String, String> idles = DatabaseUtility.getInstance().getIdleDownloads();
            for(String gid : idles.keySet()) {
            	String url = idles.get(gid);
    			DownloadAction.getInstance().addDownload(url, null, -1, gid, 
    						DatabaseUtility.getInstance().getDarStatus(gid), DatabaseUtility.getInstance().getProductDownloadDir(gid), context);
            }
    	}
    }
	class RegisterDMThread extends Thread {
		public void run() {
			String ssoStatus = (String) context.getAttribute("SSO_LOGIN_STATUS");
			while(ssoStatus == null || !ssoStatus.equals("LOGINFAILED") ) {
				LogUtility.setLevel(log);
				initializeRegistrationUrls();
				List<String> toRegister = DatabaseUtility.getInstance().getRegistrationUrls();
			    for(String currRegistrationURL:toRegister) {
				   UmssoHttpGet checkMethod = new UmssoHttpGet(currRegistrationURL);
				   input.setAppHttpMethod(checkMethod);
				   clCore.getUmssoHttpClient().getParams().setParameter(CoreConnectionPNames.CONNECTION_TIMEOUT, 6000);
       			   clCore.getUmssoHttpClient().getParams().setParameter(CoreConnectionPNames.SO_TIMEOUT, 6000);
				   
				   try {
					   clCore.processHttpRequest(input);
					   ssoStatus = (String) context.getAttribute("SSO_LOGIN_STATUS");
					   if(ssoStatus != null && ssoStatus.equals("LOGINFAILED") ) {
						   log.error(userLoginMessage);
						   return;
					   }
					   DatabaseUtility.getInstance().resetWSUrlFirstFailedcall(currRegistrationURL,configProperties.getProperty("DMIdentifier"));
	    		    } catch(Exception ex) {
	    	    		ex.printStackTrace();
	    	    		log.error("Can not connect to ws " + currRegistrationURL);
//		    	    		CHECK IF IT HAS BEEN REACHED THE maxWSRetryTime
	    	    		Timestamp ffTime = DatabaseUtility.getInstance().getWSUrlFirstFailedCall(currRegistrationURL, configProperties.getProperty("DMIdentifier"));
    	    		if(ffTime == null) {
//		    	    			log.debug(currWs + " updating first failed call");
	    	    			DatabaseUtility.getInstance().updateWSUrlFirstFailedCall(currRegistrationURL, configProperties.getProperty("DMIdentifier"));
    	    		} else {
    	    			if((System.currentTimeMillis() - ffTime.getTime()) > Long.valueOf(configProperties.getProperty("maxRetryTime"))) {
    	    				log.info(currRegistrationURL + " updating unreachable status");
    	    				log.fatal(currRegistrationURL + " was unreachable for  "+ (System.currentTimeMillis() - ffTime.getTime())/1000 + " seconds. Set to unreachable status.");
    	    				DatabaseUtility.getInstance().updateWSUrlUnreachableStatus(currRegistrationURL, configProperties.getProperty("DMIdentifier"));
    	    			}
    	    		}
		    	    		
    	    		continue;
    	    	} finally {
    	    		if(checkMethod != null)
    	    			checkMethod.releaseConnection();
    		    } 
				   
			   if(!DatabaseUtility.getInstance().isRegistered(currRegistrationURL, configProperties.getProperty("DMIdentifier"))) {
				   String dmIdentifier = configProperties.getProperty("DMIdentifier");		       
				   registerDM(currRegistrationURL, dmIdentifier, configProperties.getProperty("DMFriendlyName"));
			   }
		  }
		    try {
	        	Thread.sleep(refreshPeriod);
			} catch(InterruptedException ex){
				//ex.printStackTrace();
				log.error("error in registering dm thread " + ex.getMessage());
			}
    	}
	}
   }

   class RetrieveDARURLsThread extends Thread {
    	
    	public RetrieveDARURLsThread() {
    	}
    	
    	public void run() {
    		String ssoStatus = (String) context.getAttribute("SSO_LOGIN_STATUS");
    		while(ssoStatus == null || !ssoStatus.equals("LOGINFAILED")) {
    			LogUtility.setLevel(log);
    			ssoStatus = (String) context.getAttribute("SSO_LOGIN_STATUS");
    			Map<String, Integer> wsUrls = DatabaseUtility.getInstance().getWSUrls(configProperties.getProperty("DMIdentifier"));
	        	log.debug("wsUrls size " + wsUrls);
	        	Integer currRefresh = null;
	        	for(String currWs : wsUrls.keySet()){
	        		log.info("RETRIEVING DAR FROM " + currWs);
	        		currRefresh = wsUrls.get(currWs);
	        		boolean isStopped = DatabaseUtility.getInstance().getStopped(currWs);
	        		boolean tobeContacted = DatabaseUtility.getInstance().getToContact(currWs, configProperties.getProperty("DMIdentifier"));
	        		if(!isStopped  && tobeContacted){
	        			String conTimeOut = configProperties.getProperty("connectionTimeOut");
	        			int timeout = 6000;
	        			if(conTimeOut != null) {
	        				timeout = Integer.valueOf(conTimeOut);
	        			}
	        			clCore.getUmssoHttpClient().getParams().setParameter(CoreConnectionPNames.CONNECTION_TIMEOUT, timeout);
	        			clCore.getUmssoHttpClient().getParams().setParameter(CoreConnectionPNames.SO_TIMEOUT, timeout);
	        			
	        			try {
							
							Protocol easyhttps = new Protocol("https", (ProtocolSocketFactory) new EasySSLProtocolSocketFactory(), 443);
							Protocol.registerProtocol("https", easyhttps);
						} catch(Exception ex) {
							//ex.printStackTrace();
							log.error("error in retrieving dar thread " + ex.getMessage());
						}
	    			    
	    			    
		    	    	SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ENGLISH);
		    	    	TimeZone utc = TimeZone.getTimeZone("UTC");
		    	    	formatter.setTimeZone(utc);
		    	    	String dmIdentifier = configProperties.getProperty("DMIdentifier");
		    	    	long time = DatabaseUtility.getInstance().getTimeByWSUrl(currWs, dmIdentifier);
		    	    	ArrayList<String> monitoringURLs = getMonitoringURLs(currWs, dmIdentifier ,formatter.format(new Date(time)));
		    	    	Iterator<String> monitoringURLsIt = monitoringURLs.iterator();
		    	    	int wsId = DatabaseUtility.getInstance().getWsId(currWs, dmIdentifier);
		    	    	while(monitoringURLsIt.hasNext()) {
		    	    		downloadDAR(monitoringURLsIt.next(), wsId);
		    	    	}
		    	    }
	    	    	
	        	}
	        	try {
	        		if(currRefresh != null && currRefresh != 0 )  {
	        			Thread.sleep(currRefresh);
	        		} else {
	        			Thread.sleep(refreshPeriod);
	        		}
    			} catch(InterruptedException ex){
    				log.error("error in retrieving dar thred " + ex.getMessage());
    			}
    		}
    	}
   }
   
   class MyTailListener extends TailerListenerAdapter {
		   private  ServletContext context;
		   public MyTailListener(ServletContext context) {
		    	this.context = context;
		    }
		    @Override
		    public void handle(String line) {
		        StringBuffer logBuffer = (StringBuffer) context.getAttribute("logBuffer");
				logBuffer.append("<div>");
				logBuffer.append(StringEscapeUtils.escapeHtml(line));
				logBuffer.append("</div>");
		    }
	}
}