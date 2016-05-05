package it.acsys.download;

import it.acsys.download.ngeo.database.DatabaseUtility;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.TimeZone;

import javax.servlet.ServletContext;

import org.apache.commons.httpclient.contrib.ssl.EasySSLProtocolSocketFactory;
import org.apache.commons.httpclient.protocol.Protocol;
import org.apache.commons.httpclient.protocol.ProtocolSocketFactory;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.log4j.Logger;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleTrigger;
import org.quartz.impl.StdSchedulerFactory;
import org.quartz.impl.triggers.SimpleTriggerImpl;

import com.siemens.pse.umsso.client.UmssoCLCore;
import com.siemens.pse.umsso.client.UmssoCLCoreImpl;

public class RetrieveDARURLsThread extends Thread {
	
	private static Logger log = Logger.getLogger(DARMonitor.class);
	private boolean stop = false;
	private static Properties configProperties = null;
	
	private static ServletContext context = null;
	private static UmssoCLCore clCore = UmssoCLCoreImpl.getInstance();
	private Long refreshPeriod = 30000L;
	
	static {
        try {
        	String configPath = System.getProperty("configPath"); 
        	File configFile = new File(configPath);
        	InputStream stream  = new FileInputStream(configFile);
        	configProperties = new Properties();
        	configProperties.load(stream);
        	
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

	public RetrieveDARURLsThread(ServletContext context) {
		this.context = context;
	}
	
	public void stopThread() {
		stop = true;
	}
	
	public void run() {
		String ssoStatus = (String) context.getAttribute("SSO_LOGIN_STATUS");
		while(!stop && (ssoStatus == null || !ssoStatus.equals("LOGINFAILED"))) {
			System.out.println("RUN " + Thread.currentThread().getName());
			LogUtility.setLevel(log);
			ssoStatus = (String) context.getAttribute("SSO_LOGIN_STATUS");
			Map<String, Integer> wsUrls = DatabaseUtility.getInstance().getWSUrls(configProperties.getProperty("DMIdentifier"));
        	log.debug("wsUrls size " + wsUrls);
        	Integer currRefresh = null;
        	for(String currWs : wsUrls.keySet()){
        		log.info("RETRIEVING DAR FROM " + currWs);
        		boolean isStopped = DatabaseUtility.getInstance().getStopped(currWs, configProperties.getProperty("DMIdentifier"));
        		boolean tobeContacted = DatabaseUtility.getInstance().getToContact(currWs, configProperties.getProperty("DMIdentifier"));
        		log.debug(currWs + " IS STOPPED " + isStopped);
        		log.debug(currWs + " TO BE CONTACTED " + tobeContacted);
        		if(!isStopped  && tobeContacted){
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
	    	    	DARManager darManager = new DARManager(context);
	    	    	ArrayList<String> monitoringURLs = darManager.getMonitoringURLs(currWs, dmIdentifier ,formatter.format(new Date(time)));
	    	    	currRefresh = DatabaseUtility.getInstance().getRefreshPeriodByWsId(wsUrls.get(currWs));
	    	    	log.info("currRefresh " + currRefresh);
	    	    	Iterator<String> monitoringURLsIt = monitoringURLs.iterator();
	    	    	int wsId = DatabaseUtility.getInstance().getWsId(currWs, dmIdentifier);
	    	    	while(monitoringURLsIt.hasNext()) {
	    	    		String currMonitoringURL = monitoringURLsIt.next();
	    	    		if(DatabaseUtility.getInstance().isMonitoringURLRunning(currMonitoringURL, wsId)) {
	    	    			log.debug("MONITORING URL " + currMonitoringURL + " already running ");
	    	    			continue;
	    	    		}
	    	    		DatabaseUtility.getInstance().insertMonitoringURL(currMonitoringURL, wsId);
	    	    		String jobKey =  currMonitoringURL.hashCode() + "_" + System.currentTimeMillis();
	    	    		log.debug("STARTING NEW MONITOR DAR JOB " + jobKey + " for " + currMonitoringURL);
	    	    		JobDetail job = JobBuilder.newJob(MonitorDARJob.class).withIdentity("DAR_JOB_" + jobKey, "group1").build();
				    	job.getJobDataMap().put(MonitorDARJob.WS_ID, wsId);
				    	job.getJobDataMap().put(MonitorDARJob.MONITORING_URL, currMonitoringURL);
				    	job.getJobDataMap().put(MonitorDARJob.SERVLET_CONTEXT, context);
				    	job.getJobDataMap().put(MonitorDARJob.JOB_KEY, jobKey);
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
	    	    		//darManager.downloadDAR(monitoringURLsIt.next(), wsId);
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
