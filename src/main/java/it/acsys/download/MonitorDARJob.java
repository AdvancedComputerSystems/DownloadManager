package it.acsys.download;

import it.acsys.download.ngeo.database.DatabaseUtility;

import java.util.HashMap;
import java.util.List;

import javax.servlet.ServletContext;

import org.apache.log4j.Logger;
import org.quartz.InterruptableJob;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.TriggerKey;
import org.quartz.UnableToInterruptJobException;
import int_.esa.eo.ngeo.downloadmanager.exception.DMPluginException;
import int_.esa.eo.ngeo.downloadmanager.plugin.IDownloadProcess;

public class MonitorDARJob implements InterruptableJob {
	private static Logger log = Logger.getLogger(MonitorDARJob.class);
	public static final String WS_ID = "WS_ID";
	public static final String MONITORING_URL = "MONITORING_URL";
	public static final String SERVLET_CONTEXT = "SERVLET_CONTEXT";
	public static final String JOB_KEY = "JOB_KEY";
	private boolean interrupted = false;
	
	public void execute(JobExecutionContext context) throws JobExecutionException {
		LogUtility.setLevel(log);		
		if(!interrupted) {
			JobDataMap data = context.getJobDetail().getJobDataMap();
			int wsId = (Integer) data.get(WS_ID);
			String monitoringURL = (String) data.get(MONITORING_URL);
			log.debug("POLLING " + monitoringURL);
			ServletContext servletContext = (ServletContext) data.get(SERVLET_CONTEXT);
			DARManager darManager = new DARManager(servletContext);
			String darStatus = darManager.downloadDAR(monitoringURL, wsId);
			DatabaseUtility.getInstance().updateMonitoringURLStatus(monitoringURL, wsId,darStatus);
//			if(darStatus.equalsIgnoreCase("COMPLETED") || darStatus.equalsIgnoreCase("CANCELLED")) {
//				try {
//					this.interrupt();
//					String jobKey = (String) data.get(JOB_KEY);
//					context.getScheduler().unscheduleJob(new TriggerKey("QUERY_TRIGGER_" + jobKey, "group1"));
//					log.debug("STOPPING polling on " + monitoringURL + " because " +  darStatus);
//				} catch (Exception e) { 
//					e.printStackTrace();
//				}
//			} 
//			else 
			if(darStatus.equalsIgnoreCase("PAUSED")) {
				List<String> downloads = DatabaseUtility.getInstance().getActiveDownloadsByDAR(monitoringURL);
				HashMap<String, IDownloadProcess> cache = (HashMap<String, IDownloadProcess>) servletContext.getAttribute("cache");
				for(String download : downloads) {
					try {
						IDownloadProcess down = (IDownloadProcess) cache.get(download);
						down.pauseDownload();
					} catch(DMPluginException ex) {
						ex.printStackTrace();
						log.error("Can not pause download of DAR " + monitoringURL + "\n");
					}
				}
				List<Integer> waitingDownloads = DatabaseUtility.getInstance().getDownloadsIdByDAR(monitoringURL, 2);
				log.debug("STATUS " + darStatus + " " + waitingDownloads.size());
				for(int waiting : waitingDownloads) {
					DatabaseUtility.getInstance().updateDownloadStatus(waiting, 3);
				}
				
			} 
			//RESUME PAUSED DOWNLOADS
			else if(darStatus.equalsIgnoreCase("IN_PROGRESS")) {
				List<String> downloads = DatabaseUtility.getInstance().getDownloadsByDAR(monitoringURL, 3);
				HashMap<String, IDownloadProcess> cache = (HashMap<String, IDownloadProcess>) servletContext.getAttribute("cache");
				for(String download : downloads) {
					try {
						IDownloadProcess down = (IDownloadProcess) cache.get(download);
						down.resumeDownload();
					} catch(DMPluginException ex) {
						ex.printStackTrace();
						log.error("Can not resume download of DAR " + monitoringURL + "\n");
					}
				}
				
				List<Integer> waitingDownloads = DatabaseUtility.getInstance().getDownloadsIdByDAR(monitoringURL, 3);
				for(int waiting : waitingDownloads) {
					DatabaseUtility.getInstance().updateDownloadStatus(waiting, 2);
				}
			}
			//MANAGE CANCELLED STATUS
		    else if(darStatus.equalsIgnoreCase("CANCELLED") || darStatus.equalsIgnoreCase("COMPLETED")) {
		    	List<String> downloads = DatabaseUtility.getInstance().getActiveDownloadsByDAR(monitoringURL);
				HashMap<String, IDownloadProcess> cache = (HashMap<String, IDownloadProcess>) servletContext.getAttribute("cache");
				for(String download : downloads) {
					try {
						IDownloadProcess down = (IDownloadProcess) cache.get(download);
						down.resumeDownload();
						down.cancelDownload();
					} catch(DMPluginException ex) {
						ex.printStackTrace();
						log.error("Can not cancel download of DAR " + monitoringURL + "\n");
					}
				}
				List<Integer> waitingDownloads = DatabaseUtility.getInstance().getDownloadsIdByDAR(monitoringURL, 3);
				for(int waiting : waitingDownloads) {
					DatabaseUtility.getInstance().updateDownloadStatus(waiting, 6);
				}
				try {
					this.interrupt();
					String jobKey = (String) data.get(JOB_KEY);
					context.getScheduler().unscheduleJob(new TriggerKey("QUERY_TRIGGER_" + jobKey, "group1"));
					log.debug("STOPPING polling on " + monitoringURL + " because " +  darStatus);
				} catch (Exception e) { 
					e.printStackTrace();
				}
		    }
		}
	}

	@Override
	public void interrupt() throws UnableToInterruptJobException {
		this.interrupted = true;
	}
}