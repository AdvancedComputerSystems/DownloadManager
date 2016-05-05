package it.acsys.download.ngeo;

import it.acsys.download.ConfigUtility;
import it.acsys.download.LogUtility;
import it.acsys.download.ngeo.database.DatabaseUtility;

import java.io.ByteArrayInputStream;
import java.util.List;

import javax.servlet.ServletContext;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.UnmarshalException;
import javax.xml.bind.Unmarshaller;

import org.apache.log4j.Logger;

import _int.esa.eo.ngeo.iicd_d_ws._1.DataAccessMonitoringResp;
import _int.esa.eo.ngeo.iicd_d_ws._1.ProductAccessStatusType;
import _int.esa.eo.ngeo.iicd_d_ws._1.DataAccessMonitoringResp.ProductAccessList;
import _int.esa.eo.ngeo.iicd_d_ws._1.DataAccessMonitoringResp.ProductAccessList.ProductAccess;

public class DARParser {
	private static Logger log = Logger.getLogger(DARParser.class);

	
	public String parse(String content, String darURL, int wsId, ServletContext context) {
		LogUtility.setLevel(log);
		String url = null;
		String darStatus = "IN_PROGRESS";
		  try {
			JAXBContext jaxbContext = JAXBContext.newInstance(DataAccessMonitoringResp.class);
			Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
			DataAccessMonitoringResp darResponse = (DataAccessMonitoringResp) jaxbUnmarshaller.unmarshal(new ByteArrayInputStream(content.getBytes()));
			darStatus = darResponse.getMonitoringStatus();
			DatabaseUtility.getInstance().updateDarStatus(darURL, darStatus);
			DatabaseUtility.getInstance().updateMonitoringURLName(darURL, wsId, darResponse.getName());
			ProductAccessList paList = darResponse.getProductAccessList();
			if(paList == null) {
				return darStatus;
			}
			List<ProductAccess> productAccessList = paList.getProductAccess();
			for(ProductAccess currProdAccess : productAccessList) {
				url = currProdAccess.getProductAccessURL();
				if(url.indexOf("?") != -1) {
					String queryUri = url.trim().split("\\?")[1].replace("{", "%7B").replace("}", "%7D").replace(":", "%3A");
					url = url.trim().split("\\?")[0] + "?" +  queryUri;
				}
				String repositoryDir = currProdAccess.getProductDownloadDirectory();
				if(repositoryDir == null) { 
					  repositoryDir = "";
				}
				ProductAccessStatusType status = currProdAccess.getProductAccessStatus();
			    if(status.equals(ProductAccessStatusType.READY) && !DatabaseUtility.getInstance().checkFileDownloading(url, darURL)) {
		    	  log.info("Downloading : " + url);
		    	  String finalRep = ConfigUtility.getFileDownloadDirectory(url, repositoryDir).getAbsolutePath();
		    	  DownloadAction dwnloadAction = DownloadAction.getInstance();
			      dwnloadAction.addDownload(url, darURL, wsId, darStatus, finalRep, context);
			    }
			}
		  } catch (UnmarshalException ex) {
			ex.printStackTrace();
			log.error("DAR CORRUPTED " + ex.getMessage());
			log.error("CAN'T START AUTOMATIC DOWNLOADS");
			ex.printStackTrace();
		  } catch (Exception e) {
			e.printStackTrace();
			log.error("Can not perform download of product " + url + " " + e.getMessage());
		  } 
		  
		  return darStatus;
	  }
}
