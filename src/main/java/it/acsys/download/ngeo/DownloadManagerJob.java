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
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Properties;

import javax.servlet.ServletContext;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.SchedulerException;
import org.quartz.TriggerKey;
 
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
	    	  log.error("Wrong SSO username/password.\n Please edit the configuration properties and restart the service.");
	    	  try {
				context.getScheduler().unscheduleJob(new TriggerKey("DownloadTrigger", "group"));
			} catch (SchedulerException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	    	  return;
        }
		Properties configProperties = ConfigUtility.loadConfig();
		int maxDownloads = Integer.valueOf(configProperties.getProperty("maxDownNumb"));
		Properties usersConfigProperties = (Properties) data.get(USERS_CONFIG_PROPERTIES);
		cache = (HashMap<String, IDownloadProcess>) data.get(JCS_CACHE);
		log.setLevel(Level.toLevel(configProperties.getProperty("loglevel")));  	  	
  	  	int running = DatabaseUtility.getInstance().getRunningDownloads();		
		
		if(maxDownloads-running >0) {
			HashMap<String, String> uris = DatabaseUtility.getInstance().getUriToDownload(maxDownloads-running);
			for(String id : uris.keySet()) {
				System.out.println("ID " + id + " " + Thread.currentThread().getName());
				String uri = uris.get(id);
				HashMap<String, IDownloadPlugin> pluginsMap = (HashMap<String, IDownloadPlugin>) servletContext.getAttribute("pluginsMap");
				HashMap<IDownloadPlugin, IDownloadPluginInfo> pluginsInfo = (HashMap<IDownloadPlugin, IDownloadPluginInfo>) servletContext.getAttribute("pluginsInfo");
//				
				IDownloadPlugin currPlugin = null;
				for(String type : pluginsMap.keySet()) {
					System.out.println("uri.trim() " + uri.trim());
					System.out.println("type " + type);
					if(uri.trim().startsWith(type)) {
						currPlugin = pluginsMap.get(type);
						break;
					}
				}
				
				if(currPlugin != null) {
					    log.debug("Using to download plugin file " + currPlugin);
						try {
				    	  IDownloadProcess downProcess = cache.get(id);
				    	  if( downProcess != null) {
				    		  downProcess.resumeDownload();
				    	  } else  {
				    		  System.out.println("currPlugin " + currPlugin);
					    	  DMProductDownloadListener listener = new DMProductDownloadListener(id, cache);
					    	  String user = (String) usersConfigProperties.get("umssouser");
			    			 
					    	  AESCryptUtility aesCryptUtility = new AESCryptUtility();
					    	  String password  = aesCryptUtility.decryptString((String) usersConfigProperties.getProperty("umssopwd"));
					        	
//					    	  UPDATE PLUGIN NAME AND STATISTICS INFORMATION
					    	  IDownloadPluginInfo pluginInfo = pluginsInfo.get(currPlugin);
					    	  DatabaseUtility.getInstance().updateDownloadStatisctics(id, pluginInfo.getName(), pluginInfo.handlePause());
					    	  //File finalRep = ConfigUtility.getFileDownloadDirectory(uri, id);
					    	  String finalRep = DatabaseUtility.getInstance().getProductDownloadDir(id);
					    	  System.out.println("finalRep " + finalRep);
					    	  log.info("DOWNLOAD STARTED " + uri.trim());
			    			  downProcess = currPlugin.createDownloadProcess(new URI(uri.trim()), new File(finalRep), user, password, listener, configProperties.getProperty("proxy_host"), Integer.valueOf(configProperties.getProperty("proxy_port")), configProperties.getProperty("proxy_user"), configProperties.getProperty("proxy_pwd"));
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
				    	  ex.printStackTrace();
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
