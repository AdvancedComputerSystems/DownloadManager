package it.acsys.download.ngeo;

import int_.esa.eo.ngeo.downloadmanager.exception.AuthenticationException;
import int_.esa.eo.ngeo.downloadmanager.exception.DMPluginException;
import int_.esa.eo.ngeo.downloadmanager.plugin.IDownloadPlugin;
import int_.esa.eo.ngeo.downloadmanager.plugin.IDownloadPluginInfo;
import int_.esa.eo.ngeo.downloadmanager.plugin.IDownloadProcess;
import it.acsys.download.AESCryptUtility;
import it.acsys.download.ConfigUtility;
import it.acsys.download.DMProductDownloadListener;
import it.acsys.download.ngeo.database.DatabaseUtility;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.StringTokenizer;

import javax.servlet.ServletContext;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
 
public class DownloadManagerJob implements Job {
	public static final String JCS_CACHE = "JCS_CACHE";
	private static HashMap<String, IDownloadProcess> cache = null;
	
	public static final String USERS_CONFIG_PROPERTIES = "USERS_CONFIG_PROPERTIES";
	public static final String SERVLET_CONTEXT = "SERVLET_CONTEXT";
	
	private static Logger log = Logger.getLogger(DownloadManagerJob.class);
	
	public DownloadManagerJob() {
	}
	
	@Override
	public void execute(JobExecutionContext context) throws JobExecutionException {
		JobDataMap data = context.getJobDetail().getJobDataMap();
		ServletContext servletContext = (ServletContext) data.get(SERVLET_CONTEXT);
		String ssoStatus = (String) servletContext.getAttribute("SSO_LOGIN_STATUS");
	      if(ssoStatus != null && ssoStatus.equals("LOGINFAILED")) {
	    	  log.error("Wrong SSO username/password.\n Please edit the configuraion properties and restart the service.");
	    	  return;
        }
		Properties configProperties = ConfigUtility.loadConfig();
		int maxDownloads = Integer.valueOf(configProperties.getProperty("maxDownNumb"));
		Properties usersConfigProperties = (Properties) data.get(USERS_CONFIG_PROPERTIES);
		cache = (HashMap<String, IDownloadProcess>) data.get(JCS_CACHE);
		log.setLevel(Level.toLevel(configProperties.getProperty("loglevel")));
		
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
//  	  
  	  	cl = URLClassLoader.newInstance(jarfile, Thread.currentThread().getContextClassLoader());  	  
		
  	  	
  	  	int running = DatabaseUtility.getInstance().getRunningDownloads();		
		
		String pluginClass = "";
//		log.debug("maxDownloads-running " + (maxDownloads-running));
		if(maxDownloads-running >0) {
			HashMap<String, String> uris = DatabaseUtility.getInstance().getUriToDownload(maxDownloads-running);
			for(String id : uris.keySet()) {
				String uri = uris.get(id);
				for(String type : pluginsMap.keySet()) {
					if(uri.trim().contains(type)) {
						pluginClass = pluginsMap.get(type);
						break;
					}
				}
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
					    log.debug("Using to download plugin file " + currPlugin);
						try {
						  	  
				    	  String configPath = System.getProperty("configPath"); 
				          File configFile = new File(configPath);
				    	  IDownloadPluginInfo pluginInfo = currPlugin.initialize(new File (System.getProperty("java.io.tmpdir")),configFile);
				    	  IDownloadProcess downProcess = cache.get(id);
				    	  if( downProcess != null) {
				    		  downProcess.resumeDownload();
				    	  } else  {
					    	  DMProductDownloadListener listener = new DMProductDownloadListener(id, cache);
					    	  String user = (String) usersConfigProperties.get("umssouser");
			    			 
					    	  AESCryptUtility aesCryptUtility = new AESCryptUtility();
					    	  String password  = aesCryptUtility.decryptString((String) usersConfigProperties.getProperty("umssopwd"));
					        	
	//				    	  UPDATE DOWNLAOD STATUS IMMEDIATLY
					    	  DatabaseUtility.getInstance().updateDownloadStatus(id, 1);
//					    	  UPDATE PLUGIN NAME AND STATISTICS INFORMATION
					    	  DatabaseUtility.getInstance().updateDownloadStatisctics(id, pluginInfo.getName());
					    	  File repositoryDir = null;
					    	  String productDownloadDir = DatabaseUtility.getInstance().getProductDownloadDir(id);
					    	  if(Boolean.valueOf(configProperties.getProperty("isTemporaryDir")) ){
					    		  repositoryDir = new File(configProperties.getProperty("repositoryDir") + File.separator  + "TEMPORARY" + File.separator + productDownloadDir );
					    	  } else {
					    		  repositoryDir = new File(configProperties.getProperty("repositoryDir") + File.separator + productDownloadDir);
					    	  }
					    	  if(!repositoryDir.exists()) {
					    		  repositoryDir.mkdirs();
					    	  }
					    	  
					    	  if(uri.indexOf("?") != -1) {
					  			String queryUri = uri.trim().split("\\?")[1].replace("{", "%7B").replace("}", "%7D").replace(":", "%3A");
					  			uri = uri.trim().split("\\?")[0] + "?" +  queryUri;
					  		  }
					    	  log.info("DOWNLOAD STARTED " + uri.trim());
			    			  downProcess = currPlugin.createDownloadProcess(new URI(uri.trim()), repositoryDir, 
			    					  user, password, listener, configProperties.getProperty("proxy_host"), Integer.valueOf(configProperties.getProperty("proxy_port")), configProperties.getProperty("proxy_user"), configProperties.getProperty("proxy_pwd"));
	//				    	  String processIdentifier = listener.getProcessIdentifier();
	//				    	  log.debug("processIdentifier " + processIdentifier);
					    	  cache.put(id, downProcess);
				    	  }
				      } catch(AuthenticationException ex) {
				    	  servletContext.setAttribute("SSO_LOGIN_STATUS", "LOGINFAILED");
				    	  log.error(ex.getMessage());
				    	  log.error("Cannot start download " + uri.trim());
				    	  DatabaseUtility.getInstance().updateDownloadStatus(id, 4);
				      } catch(DMPluginException ex) {
				    	  log.error(ex.getMessage());
				    	  log.error("Cannot start download " + uri.trim());
				    	  DatabaseUtility.getInstance().updateDownloadStatus(id, 4);
				      } catch(URISyntaxException ex) {
				    	  log.error(ex.getMessage());
				    	  log.error("Uri malformed " + uri.trim());
				    	  DatabaseUtility.getInstance().updateDownloadStatus(id, 4);
				      } catch(Exception ex) {
				    	  ex.printStackTrace();
				    	  log.error(ex.getMessage());
				    	  log.error("Can not load plugin");
				    	  DatabaseUtility.getInstance().updateDownloadStatus(id, 4);
				      }
				} else  {
					  DatabaseUtility.getInstance().updateDownloadStatus(id, 4);
				  	  log.error("No plugin available for " + uri.trim());
				}
			}
		}
	}
			
}
