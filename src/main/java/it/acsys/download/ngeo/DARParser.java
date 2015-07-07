package it.acsys.download.ngeo;

import it.acsys.download.LogUtility;
import it.acsys.download.ngeo.database.DatabaseUtility;

import java.io.StringBufferInputStream;

import javax.servlet.ServletContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class DARParser {
	private static Logger log = Logger.getLogger(DARParser.class);

	
	public void parse(String content, String darURL, int wsId, ServletContext context) {
		
		LogUtility.setLevel(log);
		String url = null;
		  try {
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			Document doc = dBuilder.parse(new StringBufferInputStream(content));
			doc.getDocumentElement().normalize();
	 
			//DAR FROM WS
			log.debug("Root element :" + doc.getDocumentElement().getNodeName());
			NodeList nList = doc.getElementsByTagName("ProductAccess");
			String darStatus = getTagValue("MonitoringStatus", doc.getDocumentElement());
			doc.getElementsByTagName("MonitoringStatus").item(0).getNodeValue();
			DatabaseUtility.getInstance().updateDarStatus(darURL, darStatus);
			log.debug("-----------------------");
			
			for (int temp = 0; temp < nList.getLength(); temp++) {
			   Node nNode = nList.item(temp);
			   if (nNode.getNodeType() == Node.ELEMENT_NODE) {
	 
			      Element eElement = (Element) nNode;
			      url = getTagValue("ProductAccessURL", eElement);
			      
				  if(url.indexOf("?") != -1) {
						String queryUri = url.trim().split("\\?")[1].replace("{", "%7B").replace("}", "%7D").replace(":", "%3A");
						url = url.trim().split("\\?")[0] + "?" +  queryUri;
				  }
				  
				  String repositoryDir = getTagValue("ProductDownloadDirectory", eElement);
				  if(repositoryDir == null) { 
					  repositoryDir = "";
				  }
			      if(!DatabaseUtility.getInstance().checkFileDownloading(url, darURL)) {
			    	  log.info("Downloading : " + url);
			    	  DownloadAction dwnloadAction = DownloadAction.getInstance();
				      dwnloadAction.addDownload(url, darURL, wsId, null, darStatus, repositoryDir, context);
			      } else {
			    	  log.warn("Already downloading file: " + url + " for DAR: " + darURL);
			      }
			      
			   }
			}
			//DAR FROM ACS
			log.debug("Root element :" + doc.getDocumentElement().getNodeName());
//			nList = doc.getElementsByTagName("productAccess");
//			log.info("-----------------------");
//			for (int temp = 0; temp < nList.getLength(); temp++) {
//			   Node nNode = nList.item(temp);
//			   if (nNode.getNodeType() == Node.ELEMENT_NODE) {
//	 
//			      Element eElement = (Element) nNode;
//			      url = getTagValue("productAccessURL", eElement);
//			      log.info("Downloading : " + url);
//			      DownloadAction dwnloadAction = DownloadAction.getInstance();
//			      dwnloadAction.addDownload(url, darURL, null, false, wsId, null);
//			   }
//			}
		  } catch (org.xml.sax.SAXException ex) {
			log.error("DAR CORRUPTED " + ex.getMessage());
			log.error("CAN'T START AUTOMATIC DOWNLOADS");
			ex.printStackTrace();
		  } catch (Exception e) {
			e.printStackTrace();
			log.error("Can not perform download of product " + url + " " + e.getMessage());
		  }
	  }
	 
	  private static String getTagValue(String sTag, Element eElement) {
		  NodeList nlList = null;
		  NodeList nlList1 = eElement.getElementsByTagName(sTag);
		  if(nlList1 != null) {
			  if(nlList1.item(0) != null) {
				  nlList = nlList1.item(0).getChildNodes();
			  	  Node nValue = (Node) nlList.item(0);
			  	  if(nValue != null) 
			  		  return nValue.getNodeValue();
			  	  else {
			  		return null; 
			  	  }
			  } else {
				  return null;
			  }
		  } else {
			  return null;
		  }
	  }
}
