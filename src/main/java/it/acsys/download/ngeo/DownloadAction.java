package it.acsys.download.ngeo;


import it.acsys.download.LogUtility;
import it.acsys.download.ngeo.database.DatabaseUtility;

import java.math.BigInteger;
import java.security.SecureRandom;

import javax.servlet.ServletContext;

import org.apache.log4j.Logger;

public class DownloadAction {
	private static DownloadAction downloadAction;
	private static Logger log = Logger.getLogger(DownloadAction.class);
	private SecureRandom random = new SecureRandom();

	
	private DownloadAction() {
	}
	
	public static DownloadAction getInstance() {
	    if (downloadAction == null)
	    {
	    	downloadAction = new DownloadAction();
	    }

	    return downloadAction;
	}
	
	public String  addDownload(String uri, String darURL, int wsId, String oldGid, String darStatus, String productDownloadDirectory, ServletContext context) {
		LogUtility.setLevel(log);
		String realUri = uri.trim();
		String processIdentifier = new BigInteger(130, random).toString(32);
		DatabaseUtility.getInstance().insertNewDownload(processIdentifier, uri, darURL, wsId, 2,  darStatus, realUri, productDownloadDirectory);
		
	    return processIdentifier;
	}
	
}
