package it.acsys.download;

import int_.esa.eo.ngeo.downloadmanager.exception.DMPluginException;
import int_.esa.eo.ngeo.downloadmanager.plugin.IDownloadPlugin;
import int_.esa.eo.ngeo.downloadmanager.plugin.IDownloadPluginInfo;
import int_.esa.eo.ngeo.downloadmanager.plugin.IDownloadProcess;
import it.acsys.download.ngeo.DownloadManagerJob;
import it.acsys.download.ngeo.database.DatabaseUtility;

import java.awt.AWTException;
import java.awt.Desktop;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.KeyStore;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.StringTokenizer;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.swing.ImageIcon;

import org.apache.commons.io.input.Tailer;
import org.apache.commons.io.input.TailerListenerAdapter;
import org.apache.commons.lang.StringEscapeUtils;
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
import org.quartz.impl.triggers.SimpleTriggerImpl;

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
	private static String usersConfigPath = ConfigUtility.loadConfig().getProperty("usersConfigPath");
	
	private static UmssoCLInput input = new UmssoCLInput();
	private static UmssoCLCore clCore = UmssoCLCoreImpl.getInstance();
	private static CommandLineCallback clc;
	
	private static Properties configProperties = null;
	private static Properties usersConfigProperties = null;
	
	private static String pwd;
	private static String user;
	
	private static Properties defaultConfig = null;
	
	private static ServletContext context = null;
	
	private static final String userLoginMessage = "Wrong SSO username/password.\n Please edit the configuration properties and restart the service.";

	private static Map<String, IDownloadPlugin> pluginsMap;
	private static Map<IDownloadPlugin, IDownloadPluginInfo> pluginsInfo;
	
	private static String showEditAction ="{\"_explicitType\":\"acs_sibAction\",\"interfaceClass\":\"ngeo_if_LIST\",\"command\":\"click\",\"targetId\":\"__btnEditConf__\",\"payloads\":{\"classname\":\"nGEo\",\"main_if\":\"ngEOManager\",\"__btnEditConf__\":{\"properties\":{\"filename\":\"\",\"ageNew\":\"\",\"status_id\":\"0\",\"error_message\":\"\",\"ngeo_if_grid\":{\"properties\":{\"selectedItems\":[],\"postData\":{\"_search\":false,\"nd\":1453739911187,\"rows\":20,\"page\":1,\"sidx\":\"filesource asc, ageNew\",\"sord\":\"asc\"}}}}}},\"interfaceId\":\"ngeo_if_LIST\"}";
	
	static {
        try {
        	String configPath = System.getProperty("configPath"); 
        	System.out.println("configPath " + configPath);
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
        	e.printStackTrace();
            log.error("Can not initialize DAR monitor " + e.getMessage(), e);
        }
	}
	
	
    public DARMonitor() {
        super();
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
	      clc = new CommandLineCallback(user, pwd.toCharArray(), context);
	      input.setVisualizerCallback(clc);
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
	      int code = response.getStatusLine().getStatusCode();
	      if(code == 200) {
	    	  DatabaseUtility.getInstance().initializeMonitoringUrl(DMregistrationURL,configProperties.getProperty("DMIdentifier"));
	      }
	    }  catch(Exception e) {
	    	e.printStackTrace();
	        log.error("Can not register DM");
	    } finally {
	    	registrationMethod.releaseConnection();
//	    	clCore.getUmssoHttpClient().getCookieStore().clear();
	    }
	      
    }
    
    
    public void contextInitialized(ServletContextEvent arg0) {
    	//SYSTEM TRAY
    	if (!SystemTray.isSupported()) {
            System.out.println("SystemTray is not supported");
            
        } else {
        	 System.out.println("SystemTray IS supported");
	        final PopupMenu popup = new PopupMenu();
	        Image image = new ImageIcon("./Resources/ngEOBlue.png").getImage();
			
	        final TrayIcon trayIcon =
	                new TrayIcon(image);
	        trayIcon.setImageAutoSize(true);
	        final SystemTray tray = SystemTray.getSystemTray();
	        trayIcon.setToolTip("NgEO Download Manager is running");
	        
	        // Create a popup menu components
	        MenuItem openBrowser = new MenuItem("Open ngEO Download Manager in a browser");
	        MenuItem showConfigPanel = new MenuItem("Open configuration panel");
	        MenuItem exitItem = new MenuItem("Stop ngEO Download Manager");
	        
	        //Add components to popup menu
	        popup.add(openBrowser);
	        popup.add(showConfigPanel);
	        popup.add(exitItem);
	        
	        trayIcon.setPopupMenu(popup);
	        
	        System.out.println("IMAGE TRAY HASH " + image.hashCode());
	        
	        try {
	            tray.add(trayIcon);
	        } catch (AWTException e) {
	        	e.printStackTrace();
	            System.out.println("TrayIcon could not be added.");
	            return;
	        }
	        exitItem.addActionListener(new ActionListener() {
	            public void actionPerformed(ActionEvent e) {
	                tray.remove(trayIcon);
	                System.exit(0);
	            }
	        });
	        
	        openBrowser.addActionListener(new ActionListener() {
	            public void actionPerformed(ActionEvent e) {
	            	if(Desktop.isDesktopSupported())
	    	        {
	            		Desktop desktop = Desktop.isDesktopSupported() ? Desktop.getDesktop() : null;
	            	    if (desktop != null && desktop.isSupported(Desktop.Action.BROWSE)) {
	            	        try {
	            	        	String protocol = configProperties.getProperty("protocol");
	            	        	String jettyPort = configProperties.getProperty("jettyPort");
	            	            desktop.browse(new URL(protocol +"://localhost:" + jettyPort + "/DownloadManager").toURI());
	            	        } catch (Exception ex) {
	            	            ex.printStackTrace();
	            	        }
	            	    }
	    	        } else {
	    	        	log.error("DESKTOP IS NOT SUPPORTED. Could not open preferred browser.");
	    	        }
	            }
	        });
	        
	        showConfigPanel.addActionListener(new ActionListener() {
	            public void actionPerformed(ActionEvent e) {
	            	if(Desktop.isDesktopSupported())
	    	        {
	            		Desktop desktop = Desktop.isDesktopSupported() ? Desktop.getDesktop() : null;
	            	    if (desktop != null && desktop.isSupported(Desktop.Action.BROWSE)) {
	            	        try {
	            	        	String protocol = configProperties.getProperty("protocol");
	            	        	String jettyPort = configProperties.getProperty("jettyPort");
	            	            desktop.browse(new URL(protocol +"://localhost:" + jettyPort + "/DownloadManager/index.jsp?showConfig=true").toURI());	            	            
	            	        } catch (Exception ex) {
	            	            ex.printStackTrace();
	            	        }
	            	    }
	    	        } else {
	    	        	System.out.println("DESKTOP IS NOT SUPPORTED");
	    	        }
	            }
	        });
	        
        }
        
    	context = arg0.getServletContext();
    	context.setAttribute("logBuffer", new StringBuffer());
    	//Start TAILER thread on log file
    	MyTailListener listener = new MyTailListener(context);
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
    	
		
    	clc = new CommandLineCallback(user, pwd.toCharArray(), context);
    	input.setVisualizerCallback(clc); 
    	context.setAttribute("DM_ID", configProperties.getProperty("DMFriendlyName"));
    	context.setAttribute("initialTime", System.currentTimeMillis());
    	cache = new HashMap<String, IDownloadProcess>(); 
    	context.setAttribute("cache", cache);
    	log.setLevel(Level.toLevel(configProperties.getProperty("loglevel")));
    	pingFirstUrl();
    	//INITIALIZE PLUGINS
    	String pluginsConf = (String) configProperties.get("PluginClasses");
		StringTokenizer tokenizer = new StringTokenizer(pluginsConf,"||");
		LinkedHashMap<String, String> keyPluginsMap = new LinkedHashMap<String, String>();
		while(tokenizer.hasMoreTokens()) {
			String currentPlug = tokenizer.nextToken();
			StringTokenizer tokenizer2 = new StringTokenizer(currentPlug,"@@");
			String t1 = tokenizer2.nextToken();
			String t2 = tokenizer2.nextToken();
			System.out.println(" keyPluginsMap put " + t2 + " " + t1);
			keyPluginsMap.put(t2, t1);
		}
		pluginsMap = new HashMap<String, IDownloadPlugin>();
		pluginsInfo = new HashMap<IDownloadPlugin, IDownloadPluginInfo>();
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
//  	  
  	  	cl = URLClassLoader.newInstance(jarfile, Thread.currentThread().getContextClassLoader());      	  	
  	    ServiceLoader<IDownloadPlugin> pluginClasses = ServiceLoader.load(IDownloadPlugin.class, cl);
  	  	Iterator<IDownloadPlugin> availablePulgins = pluginClasses.iterator();
		while(availablePulgins.hasNext())  {
			IDownloadPlugin curr = availablePulgins.next();
			System.out.println("curr.getClass().getName() " + curr.getClass().getName());
			String currType = keyPluginsMap.get(curr.getClass().getName());
			if(currType != null) {
				String configPath = System.getProperty("configPath"); 
	        	File configFile = new File(configPath);
		    	IDownloadPluginInfo pluginInfo = null;
				try {
					pluginInfo = curr.initialize(new File (System.getProperty("java.io.tmpdir")),configFile);
				} catch (DMPluginException e) {
					e.printStackTrace();
					log.error("Can not initialize plug in " + curr.getClass());
				}
				System.out.println(" pluginsMap put " + currType + " " + curr.getClass().getName());
				pluginsMap.put(currType, curr);
				System.out.println(" pluginsInfo put " + curr.getClass().getName() + " " + pluginInfo.getClass());
				pluginsInfo.put(curr, pluginInfo);
			}
		}
		
		context.setAttribute("pluginsMap", pluginsMap);
		context.setAttribute("pluginsInfo", pluginsInfo);
    	
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
    	
        retrieveDAR = new RetrieveDARURLsThread(context);
        retrieveDAR.start();
        context.setAttribute("RetrieveDARURLsThread", retrieveDAR);
        
        //resumeDownloads();
        ResumeThread resumeThread = new ResumeThread();
        resumeThread.start();
        
        //resume Polling to active monitoring urls
        Map<String, Integer> monitoringUrls = DatabaseUtility.getInstance().getMonitoringUrlToResume();
		for(String currUrl : monitoringUrls.keySet()) {
			String jobKey =  currUrl.hashCode() + "_" + System.currentTimeMillis();
    		log.debug("STARTING NEW MONITOR DAR JOB " + jobKey + " for " + currUrl);
    		JobDetail job = JobBuilder.newJob(MonitorDARJob.class).withIdentity("DAR_JOB_" + jobKey, "group1").build();
	    	int wsId = monitoringUrls.get(currUrl);
    		job.getJobDataMap().put(MonitorDARJob.WS_ID, wsId);
	    	job.getJobDataMap().put(MonitorDARJob.MONITORING_URL, currUrl);
	    	job.getJobDataMap().put(MonitorDARJob.SERVLET_CONTEXT, context);
	    	job.getJobDataMap().put(MonitorDARJob.JOB_KEY, jobKey);
	    	int currRefresh = DatabaseUtility.getInstance().getRefreshPeriodByWsId(wsId);
	    	SimpleTrigger queryTrigger = new SimpleTriggerImpl(
	    			"QUERY_TRIGGER_" + jobKey , "group1", SimpleTrigger.REPEAT_INDEFINITELY, currRefresh);
	    	//schedule it				    	
	    	try {
		    	Scheduler scheduler = new StdSchedulerFactory().getScheduler();
		    	scheduler.start();
		    	scheduler.scheduleJob(job, queryTrigger);
	    	} catch(SchedulerException ex) {
	    		log.error("Can not start monitor job " + ex.getMessage());
	    	}
		}
        
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
    
    private void pingFirstUrl() {
//    	DatabaseUtility.getInstance().resetWSURLs();
    	configProperties = ConfigUtility.loadConfig();
    	String WebServiceURLs = (String) configProperties.get("WebServiceURLs");
    	log.debug("PINGING WebServiceURLs " + WebServiceURLs);
    	StringTokenizer tokenizer = new StringTokenizer(WebServiceURLs, ",");
    	DatabaseUtility.getInstance().resetWsUrlActive();
    	if(tokenizer.hasMoreElements()) {
    		String firstURL = tokenizer.nextToken().trim();
    		log.debug("PINGING firstURL " + firstURL);
    		String ssoStatus = (String) context.getAttribute("SSO_LOGIN_STATUS");
    		if(ssoStatus != null && ssoStatus.equals("LOGINFAILED") ) {
			   log.error(userLoginMessage);
			   return;
		   }
		   UmssoHttpGet checkMethod = new UmssoHttpGet(firstURL);
		   clc = new CommandLineCallback(user, pwd.toCharArray(), context);
	       input.setVisualizerCallback(clc);
		   input.setAppHttpMethod(checkMethod);
		   int connectionTimeOut = Integer.valueOf(configProperties.getProperty("connectionTimeOut"));
		   log.debug("SETTING TIMEOUT FOR REGISTER DM THREAD " + connectionTimeOut);
		   clCore.getUmssoHttpClient().getParams().setParameter(CoreConnectionPNames.CONNECTION_TIMEOUT, connectionTimeOut);
		   clCore.getUmssoHttpClient().getParams().setParameter(CoreConnectionPNames.SO_TIMEOUT, connectionTimeOut);
		   try {
			   clCore.processHttpRequest(input);
			   DatabaseUtility.getInstance().resetWSUrlFirstFailedcall(firstURL,configProperties.getProperty("DMIdentifier"));
		    } catch(Exception ex) {
	    		ex.printStackTrace();
		    }
		   
    	}
		
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
    		String ssoStatus = (String) context.getAttribute("SSO_LOGIN_STATUS");				
			if(ssoStatus != null && ssoStatus.equals("LOGINFAILED")) {
				return;
			}
    		
    		try {
				Thread.sleep(10000);
			} catch (InterruptedException e2) {
				e2.printStackTrace();
			}
    		
    		
    		//TODO
    		//RESUME COULD START TWICE RUNNING DOWNLOADS
    		Map<String, String> downloads = DatabaseUtility.getInstance().getDownloadsByStatusId(1);
    		Map<String, String> pausedDownloads = DatabaseUtility.getInstance().getDownloadsByStatusId(3);
    		Map<String, String> all = new HashMap<String, String>();
            all.putAll(downloads);
            all.putAll(pausedDownloads);
    		
            for(String gid : all.keySet()) {
            	String url = all.get(gid);
            	IDownloadPlugin currPlugin = null;
				for(String type : pluginsMap.keySet()) {
					System.out.println("uri.trim() " + url.trim());
					System.out.println("type " + type);
					if(url.trim().contains(type)) {
						currPlugin = pluginsMap.get(type);
						break;
					}
				}
				
				System.out.println("currPlugin " + currPlugin);
				if(currPlugin != null) {
						try {
				    	  DMProductDownloadListener listener = new DMProductDownloadListener(gid, cache);
				    	  String user = (String) usersConfigProperties.get("umssouser");
		    			 
				    	  AESCryptUtility aesCryptUtility = new AESCryptUtility();
				    	  String password  = aesCryptUtility.decryptString((String) usersConfigProperties.getProperty("umssopwd"));
				        	
//    				    	  UPDATE DOWNLAOD STATUS IMMEDIATLY
				    	  DatabaseUtility.getInstance().updateDownloadStatus(gid, 1);
//    					    	  UPDATE PLUGIN NAME AND STATISTICS INFORMATION
				    		
//				    	  UPDATE PLUGIN NAME AND STATISTICS INFORMATION
				    	  IDownloadPluginInfo pluginInfo = pluginsInfo.get(currPlugin);
				    	  DatabaseUtility.getInstance().updateDownloadStatisctics(gid, pluginInfo.getName(), pluginInfo.handlePause());
				    	  //File finalRep = ConfigUtility.getFileDownloadDirectory(url, gid);
				    	  String finalRep = DatabaseUtility.getInstance().getProductDownloadDir(gid);
				    	  System.out.println("finalRep " + finalRep);
				    	  log.info("DOWNLOAD STARTED " + url.trim());
				    	  IDownloadProcess downProcess = currPlugin.createDownloadProcess(new URI(url.trim()), new File(finalRep), 
		    					  user, password, listener, configProperties.getProperty("proxy_host"), Integer.valueOf(configProperties.getProperty("proxy_port")), configProperties.getProperty("proxy_user"), configProperties.getProperty("proxy_pwd"));
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
            Map<String, String> idles = DatabaseUtility.getInstance().getDownloadsByStatusId(7);
            for(String gid : idles.keySet()) {
            	String url = idles.get(gid);
            	DatabaseUtility.getInstance().updateDownloadStatus(gid, 2);
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
			       ssoStatus = (String) context.getAttribute("SSO_LOGIN_STATUS");
				   if(ssoStatus != null && ssoStatus.equals("LOGINFAILED") ) {
					   log.error(userLoginMessage);
					   return;
				   }
				   UmssoHttpGet checkMethod = new UmssoHttpGet(currRegistrationURL);
				   clc = new CommandLineCallback(user, pwd.toCharArray(), context);
			       input.setVisualizerCallback(clc);
				   input.setAppHttpMethod(checkMethod);
				   int connectionTimeOut = Integer.valueOf(configProperties.getProperty("connectionTimeOut"));
				   log.debug("SETTING TIMEOUT FOR REGISTER DM THREAD " + connectionTimeOut);
				   clCore.getUmssoHttpClient().getParams().setParameter(CoreConnectionPNames.CONNECTION_TIMEOUT, connectionTimeOut);
       			   clCore.getUmssoHttpClient().getParams().setParameter(CoreConnectionPNames.SO_TIMEOUT, connectionTimeOut);
				   try {
					   clCore.processHttpRequest(input);
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
    	    		if(checkMethod != null) {
    	    			checkMethod.releaseConnection();
    	    		}
//    	    		clCore.getUmssoHttpClient().getCookieStore().clear();
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