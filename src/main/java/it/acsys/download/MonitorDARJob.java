package it.acsys.download;

import it.acsys.download.ngeo.database.DatabaseUtility;

import javax.servlet.ServletContext;

import org.apache.log4j.Logger;
import org.quartz.InterruptableJob;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.TriggerKey;
import org.quartz.UnableToInterruptJobException;

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
			if(darStatus.equalsIgnoreCase("COMPLETED") || darStatus.equalsIgnoreCase("CANCELLED")) {
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