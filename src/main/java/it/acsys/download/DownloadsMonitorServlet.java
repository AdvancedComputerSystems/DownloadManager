package it.acsys.download;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.log4j.Logger;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.org.apache.xml.internal.security.utils.Base64;

import int_.esa.eo.ngeo.downloadmanager.exception.DMPluginException;
import int_.esa.eo.ngeo.downloadmanager.plugin.EDownloadStatus;
import int_.esa.eo.ngeo.downloadmanager.plugin.IDownloadProcess;
import it.acsys.download.ngeo.DARParser;
import it.acsys.download.ngeo.DownloadAction;
import it.acsys.download.ngeo.database.DatabaseUtility;

/**
 * Servlet implementation class DownloadsMonitorServlet
 */
public class DownloadsMonitorServlet extends HttpServlet {
       
	private static HashMap<String, IDownloadProcess> cache = null;
	  
	
	private static Logger log = Logger.getLogger(DownloadsMonitorServlet.class);
	
	
	private static String usersConfigPath = ConfigUtility.loadConfig().getProperty("usersConfigPath");

	
    /**
     * @see HttpServlet#HttpServlet()
     */
    
	  
	public DownloadsMonitorServlet() {
        super();
    }
    	
	private ArrayList closePopUp(ArrayList result) throws IOException {
		if(result == null) {
			result = new ArrayList();
		}	
		HashMap resp = new HashMap();
		HashMap payload = new HashMap();
		resp.put("command", "EXEC");
		resp.put("targetId", "ngeo_if_EDITCONFIG");
		payload.put("_EXEC_METHOD_NAME_", "_CLOSE_POPUP_");
		resp.put("payloads", payload);
		result.add(resp);
		
		return result;
	}
	
	
	private ArrayList sendMessage(ArrayList result, String message) throws IOException {
		if(result == null) {
			result = new ArrayList();
		}
		HashMap resp = new HashMap();
		HashMap payload = new HashMap();
		payload.put("message",message);
		payload.put("title","Warning");
		resp.put("command", "MESSAGE");
		resp.put("payloads", payload);
		result.add(resp);
		
		return result;
	}
	
	private ArrayList triggerRefrehGrid(ArrayList result, String gridId) throws IOException {
		if(result == null) {
			result = new ArrayList();
		}
		HashMap resp = new HashMap();
		HashMap payload = new HashMap();
		payload.put("_EXEC_METHOD_NAME_", "triggerRefresh");
		resp.put("command", "EXEC");
		resp.put("payloads", payload);
		//resp.put("methodName", "triggerRefresh");
		resp.put("targetId", gridId);
		result.add(resp);
		
		return result;
	}
	
	private ArrayList getEditConfInterface(ArrayList result)  {
		if(result == null)
			result = new ArrayList();
		HashMap resp = new HashMap();
		HashMap payload = new HashMap();
		BufferedReader br = null;
		try {
			FileInputStream editConfigFile = new FileInputStream("./templates/EditConfigInterface.txt");
		  	DataInputStream in = new DataInputStream(editConfigFile);
		  	br = new BufferedReader(new InputStreamReader(in));
		  	StringBuffer sb = new StringBuffer();
		  	String strLine;
		  	while((strLine = br.readLine()) != null) {
		  		  sb.append(strLine);
		  	}
	  	    String filecontent = sb.toString();
	  	    in.close();
	  	    
	  	    
	  	    Properties properties = ConfigUtility.loadConfig();
			Properties usersProperties = null;
			try {
			  	
			  	InputStream usersStream  = new FileInputStream(new File(usersConfigPath));
			  	usersProperties = new Properties();
			  	usersProperties.load(usersStream);
			  	usersStream.close();
			  	
			} catch (IOException e) {
			      e.printStackTrace();
			}
			String umssouser = (String) usersProperties.get("umssouser");
//			com.sun.org.apache.xml.internal.security.Init.init();
			AESCryptUtility aesCryptUtility = new AESCryptUtility();
			String umssopwd  = aesCryptUtility.decryptString((String) usersProperties.getProperty("umssopwd"));
			String DMServerURL = (String) properties.get("WebServiceURLs");
			String repositoryDir = (String) properties.get("repositoryDir");
			String logLevel = (String) properties.get("loglevel");
			String acceptCertificate = (String) properties.get("acceptCertificate");
			String downNumb = (String) properties.get("maxDownNumb");
	        
	        String[] fatalsEceptions = ((String) properties.getProperty("fatalExceptions")).split("\\|\\|");
			if(Arrays.asList(fatalsEceptions).contains("FileSystemWriteException")) {
				filecontent = filecontent.replace("{checked_FileSystemWriteException}", "checked");
			} else {
				filecontent = filecontent.replace("{checked_FileSystemWriteException}", "");
			}
			if(Arrays.asList(fatalsEceptions).contains("AuthenticationException")) {
				filecontent = filecontent.replace("{checked_AuthenticationException}", "checked");
			} else {
				filecontent = filecontent.replace("{checked_AuthenticationException}", "");
			}
			if(Arrays.asList(fatalsEceptions).contains("ProductUnavailableException")) {
				filecontent = filecontent.replace("{checked_ProductUnavailableException}", "checked");
			} else {
				filecontent = filecontent.replace("{checked_ProductUnavailableException}", "");
			}
			
	        
	        filecontent = filecontent.replace("{username_value}", umssouser);
	        filecontent = filecontent.replace("{password_value}", umssopwd);
	        filecontent = filecontent.replace("{confirm_pass_value}", umssopwd);
	        filecontent = filecontent.replace("{down_path_value}", repositoryDir);
	        filecontent = filecontent.replace("{server_urls_value}", DMServerURL);
	        
	        
	        if(logLevel.equalsIgnoreCase("debug")) {
	        	filecontent = filecontent.replace("{selected_debug}", "selected");
	        	filecontent = filecontent.replace("{selected_info}", "");
	        	filecontent = filecontent.replace("{selected_error}", "");
	        } else if (logLevel.equalsIgnoreCase("info")) {
	        	filecontent = filecontent.replace("{selected_debug}", "");
	        	filecontent = filecontent.replace("{selected_info}", "selected");
	        	filecontent = filecontent.replace("{selected_error}", "");	        	
	        } else if (logLevel.equalsIgnoreCase("error")) {
	        	filecontent = filecontent.replace("{selected_debug}", "");
	        	filecontent = filecontent.replace("{selected_info}", "");
	        	filecontent = filecontent.replace("{selected_error}", "selected");	        	
	        }
	        
	        if(acceptCertificate.equalsIgnoreCase("true")) {
	        	filecontent = filecontent.replace("{certficate_true}", "selected");
	        	filecontent = filecontent.replace("{certficate_false}", "");
	        } else {
	        	filecontent = filecontent.replace("{certficate_true}", "");
	        	filecontent = filecontent.replace("{certficate_false}", "selected");
	        }
	        filecontent = filecontent.replace("{down_numb_value}", downNumb);
	        
	        filecontent = filecontent.replace("{cli_username_value}", (String) usersProperties.get("CLIUsername"));
	        filecontent = filecontent.replace("{cli_password_value}", aesCryptUtility.decryptString((String) usersProperties.getProperty("CLIPwd")));
	        filecontent = filecontent.replace("{confirm_cli_pass_value}", aesCryptUtility.decryptString((String) usersProperties.getProperty("CLIPwd")));
	        
	        String scriptCommand = "";
	        if(properties.get("script_command") != null) {
	        	scriptCommand =(String) properties.get("script_command");
	        }
//	        System.out.println("scriptCommand " + scriptCommand);
	        
	        filecontent = filecontent.replace("{script_command}", scriptCommand);
	        
			payload.put("content", filecontent);
			payload.put("objectType", "dialog");
			HashMap options = new HashMap();
			options.put("autoOpen", true);
			options.put("closeOnEscape", false);
			options.put("resizable", false);
			options.put("height", 420);
			options.put("width", 600);
			Gson gson = new Gson();
			payload.put("options", gson.toJson(options));
			resp.put("command", "ADDPOPUP");
			resp.put("targetId", "ngeo_if_EDITCONFIG");
			resp.put("interfaceId", "ngeo_if_EDITCONFIG");
			resp.put("interfaceClass", "ngeo_if_LIST");
			resp.put("payloads", payload);
			result.add(resp);
			
		} catch(Exception e) {
	    	e.printStackTrace();
	        log.error("Can not read EditConfigInterface file");
	        
	    } finally {
			try {
				if (br != null) {
					br.close();
				}
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		}
		
		return result;
	}
	
	private ArrayList refreshGrid(JsonObject payloads, String message) throws IOException{
		ArrayList result = (ArrayList) DatabaseUtility.getInstance().getNewGridData(null, payloads);
		
		HashMap resp = new HashMap();
		HashMap payload = new HashMap();
	    payload.put("_EXEC_METHOD_NAME_", "callMethodAndSetProperties");
	    List data = new ArrayList();
	    HashMap event1 = new HashMap();
	    HashMap event2 = new HashMap();
	    event1.put("objName","DM_running");
	    event1.put("methodName","empty");
	    event1.put("args","");
	    event2.put("objName","DM_running");
	    event2.put("methodName","append");
	    event2.put("args",message);
	    data.add(event1);
	    data.add(event2);
	    
	    
	    
	    
	    HashMap event3 = new HashMap();
	    event3.put("objName","wsData");
	    event3.put("methodName","html");
	    StringBuffer htmlWsText = new StringBuffer();
	    Properties configProperties = ConfigUtility.loadConfig();
	    String wsUrlsStatus = DatabaseUtility.getInstance().getWSUrlsStatus(configProperties.getProperty("DMIdentifier"));
	    htmlWsText.append("<p class=\"" + wsUrlsStatus + "\"></p>");

	    event3.put("args",htmlWsText);
	    data.add(event3);
	    
	    
	    
	    HashMap argv = new HashMap();
	    argv.put("data", data);
		payload.put("_ARGV_", argv);
			
		resp.put("interfaceClass",  "ngeo_if_LIST");
		resp.put("interfaceId",  "ngeo_if_LIST");
		resp.put("command",  "EXEC");
		resp.put("targetId",  "DM_running");
		resp.put("payloads",  payload);
		
//		ADD PANEL ABOUT SERVERS STATUS
		HashMap resp1 = new HashMap();
		HashMap payload1= new HashMap();
		payload1.put("_EXEC_METHOD_NAME_", "callMethodAndSetProperties");
	    List data1 = new ArrayList(); 
	    HashMap eventServers = new HashMap();
	    eventServers.put("objName","serverData");
	    eventServers.put("methodName","html");
	    StringBuffer htmlText = new StringBuffer();
	    HashMap<String, Integer> pluginStatistics = DatabaseUtility.getInstance().getPluginStatistics();
	    htmlText.append("<ul>");
	    for(String pluginName : pluginStatistics.keySet()) {
		    htmlText.append("<li>" +  pluginName + " speed (Bytes/sec): " + pluginStatistics.get(pluginName)  + "</li>");
	    }
	    htmlText.append("</ul>");
	    eventServers.put("args",htmlText);
	    data1.add(eventServers);
	    HashMap argv1 = new HashMap();
	    argv1.put("data", data1);
	    payload1.put("_ARGV_", argv1);
	    
	    resp1.put("interfaceClass",  "ngeo_if_LIST");
	    resp1.put("interfaceId",  "ngeo_if_LIST");
	    resp1.put("command",  "EXEC");
	    resp1.put("targetId",  "serverData");
	    resp1.put("payloads",  payload1);
		
		result.add(resp);
		result.add(resp1);
		
		return result;
	}
	
	private boolean checkCredential(String username, String password) {
		boolean result = false;
		Properties usersProperties = new Properties();
		try {
        	File configFile = new File(usersConfigPath);
        	InputStream stream  = new FileInputStream(configFile);
        	usersProperties.load(stream);
        	stream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
		String CLIUsername = usersProperties.getProperty("CLIUsername");
		if(CLIUsername.equals("")) {
			return true;
		}
		if(username != null && password != null) {
			try {
				AESCryptUtility aesCryptUtility = new AESCryptUtility();
				String CLIPwd = aesCryptUtility.decryptString(usersProperties.getProperty("CLIPwd"));
				if(username.equals(CLIUsername) && password.equals(CLIPwd)) {
					result = true;
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		
		return result;
	}

	/**
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		cache = (HashMap<String, IDownloadProcess>) getServletContext().getAttribute("cache");
		LogUtility.setLevel(log);
		response.setContentType("text/html");
        PrintWriter out= response.getWriter();             
        if(request.getParameter("acs_sibAction") != null)  {
        	try {
	        	String acsSibAction =  new String(Base64.decode(request.getParameter("acs_sibAction")));
//	        	System.out.println("acsSibAction " + acsSibAction);
	        	ArrayList result = null;
	        	JsonParser parser = new JsonParser();
	        	JsonObject obj = (JsonObject) parser.parse(acsSibAction);
	        	JsonElement command = obj.get("command");
	        	JsonObject payloads = (JsonObject) obj.get("payloads");
	        	
    	        if(command.getAsString().equalsIgnoreCase("click")) {
    	        	JsonElement targetId = obj.get("targetId");
	        		if (targetId.getAsString().equalsIgnoreCase("__btnLogin__")) {
	        			JsonObject targetObj = (JsonObject) payloads.get("__btnLogin__");
	        			JsonObject properties = (JsonObject) targetObj.get("properties");
	        		
	        			String  username = ((JsonElement) properties.get("username")).getAsString();
	        			String  password = ((JsonElement) properties.get("password")).getAsString();
	        			
	        	        if (checkCredential(username, password)) {
	        	            request.getSession().setAttribute("username", username);
	        	            request.getSession().setAttribute("password", password);
	        	            request.getSession().setAttribute("ERROR_MESSAGE", null);
	        	        }
	        	        
	        	        result = reloadPage();
	        	
	        		}
	        		
    	        }
	        	if(!checkCredential((String) request.getSession().getAttribute("username"), 
	        			(String) request.getSession().getAttribute("password"))) {
	        		request.getSession().setAttribute("ERROR_MESSAGE", "Wrong username or password.");
	        		result = reloadPage();
	        	}
	        	
	        	String ssoStatus = (String) getServletContext().getAttribute("SSO_LOGIN_STATUS");
	        	if(command.getAsString().equalsIgnoreCase("click")) {
	        		JsonElement targetId = obj.get("targetId");
	        		if (targetId.getAsString().equalsIgnoreCase("__btnEditConf__")) {
	        			result = this.getEditConfInterface(result);
	        		} else if (targetId.getAsString().equalsIgnoreCase("__btnEditSaveConfig__")) {
	        			JsonObject targetObj = (JsonObject) payloads.get("__btnEditSaveConfig__");
	        			JsonObject properties = (JsonObject) targetObj.get("properties");
	        			//System.out.println(properties.toString());
	        			String umssouser = properties.get("username").getAsString();
	        			String pwd = properties.get("password").getAsString();
	        			String confirmPwd = properties.get("confirm_pass").getAsString();
	        			String repositoryDir = properties.get("down_path").getAsString();
	        			String scriptCommand = properties.get("script_command").getAsString();
	        			
	        			String fatalExceptions = "";
	        			JsonElement FileSystemWriteException = properties.get("FileSystemWriteException");
	        			if(FileSystemWriteException != null) {
	        				fatalExceptions += "FileSystemWriteException||";
	        			}
	        			
	        			JsonElement AuthenticationException = properties.get("AuthenticationException");
	        			if(AuthenticationException != null) {
	        				fatalExceptions += "AuthenticationException||";
	        			}
	        			
	        			JsonElement ProductUnavailableException = properties.get("ProductUnavailableException");
	        			if(ProductUnavailableException != null) { 
	        				fatalExceptions += "ProductUnavailableException||";
		        		}
	        			
	        			String CLIUsername = properties.get("cli_username").getAsString();
	        			String CLIPwd = properties.get("cli_password").getAsString();
	        			String confirmCLIPwd = properties.get("confirm_cli_pass").getAsString();
	        			if(pwd.equals(confirmPwd) && CLIPwd.equals(confirmCLIPwd)) {
	        			try {
	        			  	Properties usersProperties = null;
	    				  	InputStream usersStream  = new FileInputStream(new File(usersConfigPath));
	    				  	usersProperties = new Properties();
	    				  	usersProperties.load(usersStream);
	    				  	usersStream.close();
	    		        	
	    		        	usersProperties.put("umssouser", umssouser);
	    		        	AESCryptUtility aesCryptUtility = new AESCryptUtility();
	    		        	usersProperties.put("umssopwd", aesCryptUtility.encryptString(pwd));
	    		        	Properties configProperties = ConfigUtility.loadConfig();
	    		        	configProperties.put("repositoryDir", repositoryDir);
	    		        	configProperties.put("script_command", StringEscapeUtils.escapeXml(scriptCommand));
	    		        	configProperties.put("fatalExceptions", fatalExceptions);
	    		        	usersProperties.put("CLIPwd",aesCryptUtility.encryptString(CLIPwd));
	    		        	usersProperties.put("CLIUsername",CLIUsername);
	    		        	
	    		        	request.getSession().setAttribute("username", CLIUsername);
	    		        	request.getSession().setAttribute("password", CLIPwd);
	        		        
	    		        	String configPath = System.getProperty("configPath"); 
						  	File configFile = new File(configPath);
        		        	configProperties.store(new FileOutputStream(configFile), "");
	        		        	
        		        	usersProperties.store(new FileOutputStream(usersConfigPath), "");
	        		        	
        		        	result = this.closePopUp(null);
        		        	result = this.sendMessage(result,"Saved new configuration.");
        		        } catch (Exception e) {
        		            e.printStackTrace();
        		        }
        			} else 	{
        				try {
        					result = this.closePopUp(null);
        					result = this.sendMessage(null, "Password and confirm password are different!");
        					
        				} catch (Exception e) {
        		            e.printStackTrace();
        		        }
        			}
	        	} else {
	        		if(ssoStatus != null && ssoStatus.equals("LOGINFAILED")) {
//		        		SHOW ERROR POP UP
		        		result = this.sendMessage(result, "Wrong SSO username/password.\n Please edit the configuration properties and restart the service.");
		        	} else {
		        		if (targetId.getAsString().equalsIgnoreCase("__btnPurgeDB__")) {
		        			//delete from db
		        			DatabaseUtility.getInstance().purgeDB();
		        			result = this.triggerRefrehGrid(result, "ngeo_if_grid");
		        		} else if (targetId.getAsString().equalsIgnoreCase("__btnPurgeWSURLS__")) {
		        			//delete wsurls from db
		        			DatabaseUtility.getInstance().resetMonitoringUrls();
		        			DatabaseUtility.getInstance().resetWSURLs();
		        		} else if (targetId.getAsString().equalsIgnoreCase("__btnResetStatistics__")) {
		        			//delete from db
		        			DatabaseUtility.getInstance().resetStatistics();
		        			result = this.triggerRefrehGrid(result, "ngeo_if_grid");
		        		} else if (targetId.getAsString().equalsIgnoreCase("__btnPause__")) {
		        			JsonObject targetObj = (JsonObject) payloads.get("__btnPause__");
		        			JsonObject buttonProperties = (JsonObject) targetObj.get("properties");
		        			JsonObject grid = (JsonObject) buttonProperties.get("ngeo_if_grid");
		        			JsonObject properties = (JsonObject) grid.get("properties");
		        			JsonArray selectedItems = properties.get("selectedItems").getAsJsonArray();
		        			Iterator<JsonElement> iterator = selectedItems.iterator();
		        			ArrayList<String> noPause = new ArrayList<String>();
		        			while(iterator.hasNext()) {
		        				JsonObject currEl = (JsonObject) iterator.next();
		        				String gid = currEl.get("gid").getAsString();
//		        				System.out.println("gid " + gid);
		        				int statusId = currEl.get("status_id").getAsInt();
		        				String filename = currEl.get("filename").getAsString();
		        				if(statusId ==  1) {
		        					IDownloadProcess down = (IDownloadProcess) cache.get(gid);
		        					if(!DatabaseUtility.getInstance().handlePause(gid)) {
		        						noPause.add(filename);
		        					} else {
				        				down.pauseDownload();
				        				DatabaseUtility.getInstance().updateDownloadStatisctics(gid);		        				
				        				log.debug("Paused download with id " + gid);
		        					}
		        				} else 	{
		        					noPause.add(filename);
		        				}
		        			}
	        				if(!noPause.isEmpty()) {
	        					Iterator<String> noPauseIt = noPause.iterator();
	        					String message = "";
	        					while(noPauseIt.hasNext()) {
	        						message += noPauseIt.next() + "\n";
	        					}
	//        					result = DatabaseUtility.getInstance().getNewGridData(result, grid);
	        					result = this.sendMessage(result, "Cannot pause download " + message);
	        					log.debug("Cannot pause download " + message);
	        				}
	        				result = this.triggerRefrehGrid(result, "ngeo_if_grid");	        				
		        		} else if (targetId.getAsString().equalsIgnoreCase("__btnResume__")) {
		        			JsonObject targetObj = (JsonObject) payloads.get("__btnResume__");
		        			JsonObject buttonProperties = (JsonObject) targetObj.get("properties");
		        			JsonObject grid = (JsonObject) buttonProperties.get("ngeo_if_grid");
		        			JsonObject properties = (JsonObject) grid.get("properties");
		        			JsonArray selectedItems = properties.get("selectedItems").getAsJsonArray();
		        			Iterator<JsonElement> iterator = selectedItems.iterator();
		        			ArrayList<String> noResume = new ArrayList<String>();
		        			while(iterator.hasNext()) {
		        				JsonObject currEl = (JsonObject) iterator.next();
		        				String gid = currEl.get("gid").getAsString();
		        				int statusId = currEl.get("status_id").getAsInt();
		        				String filename = currEl.get("filename").getAsString();
		        				if(statusId ==  3) {
		        					/*IDownloadProcess down = (IDownloadProcess) cache.get(gid);
			        				down.resumeDownload();
			        				DatabaseUtility.getInstance().updateStartTime(gid);*/
		        					DatabaseUtility.getInstance().updateDownloadStatus(gid, 2);
			        				log.debug("Resumed download with id " + gid);
		        				} else 	{
		        					noResume.add(filename);
		        				}
		        			}
		        			if(!noResume.isEmpty()) {
	        					Iterator<String> noResumeIt = noResume.iterator();
	        					String message = "";
	        					while(noResumeIt.hasNext()) {
	        						message += noResumeIt.next() + "\n";
	        					}
	        					//result = DatabaseUtility.getInstance().getNewGridData(result, grid);
	        					result = this.sendMessage(result, "Cannot resume download " + message);
	        					log.debug("Cannot resume download " + message);
	        				}
		        			result = this.triggerRefrehGrid(result, "ngeo_if_grid");
		        		} else if (targetId.getAsString().equalsIgnoreCase("__btnStop__")) {
		        			JsonObject targetObj = (JsonObject) payloads.get("__btnStop__");
		        			JsonObject buttonProperties = (JsonObject) targetObj.get("properties");
		        			JsonObject grid = (JsonObject) buttonProperties.get("ngeo_if_grid");
		        			JsonObject properties = (JsonObject) grid.get("properties");
		        			JsonArray selectedItems = properties.get("selectedItems").getAsJsonArray();
		        			Iterator<JsonElement> iterator = selectedItems.iterator();
		        			ArrayList<String> noStop = new ArrayList<String>();
		        			while(iterator.hasNext()) {
		        				JsonObject currEl = (JsonObject) iterator.next();
		        				String gid = currEl.get("gid").getAsString();
		        				int statusId = currEl.get("status_id").getAsInt();
		        				String filename = currEl.get("filename").getAsString();
		        				if(statusId ==  1) {
		        					IDownloadProcess down = (IDownloadProcess) cache.get(gid);
			        				down.cancelDownload();
			        				log.debug("Stopped download with id " + gid);
		        				} else 	{
		        					noStop.add(filename);
		        				}
		        			}
		        			
	        				if(!noStop.isEmpty()) {
	        					Iterator<String> noStopIt = noStop.iterator();
	        					String message = "";
	        					while(noStopIt.hasNext()) {
	        						message += noStopIt.next() + "\n";
	        					}
	        					
	        					//result = DatabaseUtility.getInstance().getNewGridData(result, grid);
	        					result = this.sendMessage(result, "Cannot stop download " + message);
	        					log.debug("Cannot stop download " + message);
	        				}
	        				result = this.triggerRefrehGrid(result, "ngeo_if_grid");
		        		}  else if (targetId.getAsString().equalsIgnoreCase("__btnChangePos__")) {
		        			JsonObject targetObj = (JsonObject) payloads.get("__btnChangePos__");
		        			JsonObject buttonProperties = (JsonObject) targetObj.get("properties");
		        			JsonObject grid = (JsonObject) buttonProperties.get("ngeo_if_grid");
		        			JsonObject properties = (JsonObject) grid.get("properties");
		        			JsonArray selectedItems = properties.get("selectedItems").getAsJsonArray();
		        			Iterator<JsonElement> iterator = selectedItems.iterator();
		        			ArrayList<String> noChange = new ArrayList<String>();
		        			while(iterator.hasNext()) {
		        				JsonObject currEl = (JsonObject) iterator.next();
		        				String gid = currEl.get("gid").getAsString();
		        				int statusId = currEl.get("status_id").getAsInt();
		        				String filename = currEl.get("filename").getAsString();
		        				if(statusId ==  2) {
		        					DatabaseUtility.getInstance().moveDownloadOnTopList(gid);
			        				log.info("Moved on top of waiting queue download with id " + gid);
		        				} else 	{
		        					noChange.add(filename);
		        				}
		        			}
		        			
	        				if(!noChange.isEmpty()) {
	        					Iterator<String> noChangeIt = noChange.iterator();
	        					String message = "";
	        					while(noChangeIt.hasNext()) {
	        						message += noChangeIt.next() + "\n";
	        					}
	        					//result = DatabaseUtility.getInstance().getNewGridData(result, grid);
	        					result = this.sendMessage(result, "Cannot change position download " + message);
	        					log.info("Cannot change position download " + message);
	        				} else {
	        					//result = DatabaseUtility.getInstance().getNewGridData(result, grid);
	        					result = this.sendMessage(result, "The selected download has been moved at top of waiting list.");
	        				}
	        				result = this.triggerRefrehGrid(result, "ngeo_if_grid");
		        		}
		        		else if (targetId.getAsString().equalsIgnoreCase("__btnRetry__")) {
		        			JsonObject targetObj = (JsonObject) payloads.get("__btnRetry__");
		        			JsonObject buttonProperties = (JsonObject) targetObj.get("properties");
		        			JsonObject grid = (JsonObject) buttonProperties.get("ngeo_if_grid");
		        			JsonObject properties = (JsonObject) grid.get("properties");
		        			JsonArray selectedItems = properties.get("selectedItems").getAsJsonArray();
		        			Iterator<JsonElement> iterator = selectedItems.iterator();
		        			ArrayList<String> noRetry = new ArrayList<String>();
		        			while(iterator.hasNext()) {
		        				JsonObject currEl = (JsonObject) iterator.next();
		        				String gid = currEl.get("gid").getAsString();
		        				int statusId = currEl.get("status_id").getAsInt();
		        				String filename = currEl.get("filename").getAsString();
		        				if(statusId ==  4) {
		        					//RETRY
//		        					System.out.println("REMOVING FROM CACHE " + gid);
		        					cache.remove(gid);
		        					if(!DatabaseUtility.getInstance().checkFileDownloading(filename)) {
		        						log.debug("Retrying to download " + filename);
		        						//reset error message
		        						DatabaseUtility.getInstance().updateErrorMessage(gid, "");
				        				DatabaseUtility.getInstance().progress(0, 0l, EDownloadStatus.NOT_STARTED, gid);					        			
				        			} else {
				        				result = this.sendMessage(result, "The download " + filename + " is already running.");
				        			}
				        			result = this.triggerRefrehGrid(result, "ngeo_if_grid");
		        				} else 	{
		        					noRetry.add(filename);
		        				}
		        			}
		        			
	        				if(!noRetry.isEmpty()) {
	        					Iterator<String> noRetryIt = noRetry.iterator();
	        					String message = "";
	        					while(noRetryIt.hasNext()) {
	        						message += noRetryIt.next() + "\n";
	        					}
	        					result = this.sendMessage(result, "Cannot retry download " + message);
	        					log.info("Cannot retry download " + message);
	        				}
	        				
	        				result = this.triggerRefrehGrid(result, "ngeo_if_grid");
		        		}
		        		else if (targetId.getAsString().equalsIgnoreCase("__btnDownload__")) {
		        			JsonObject targetObj = (JsonObject) payloads.get("__btnDownload__");
		        			JsonObject properties = (JsonObject) targetObj.get("properties");
		        			String DARUri = ((JsonElement) properties.get("down_new_path")).getAsString();
		        			if(!DatabaseUtility.getInstance().checkDARDownloading(DARUri)) {
		        				executeDAR(DARUri, result, out);
		        			} else {
		        				result = this.sendMessage(result, "The Download of DAR " + DARUri + " is already running.");
		        			}
		        			result = this.triggerRefrehGrid(result, "ngeo_if_grid");
		        		} else if (targetId.getAsString().equalsIgnoreCase("__btnProductDownload__")) {
		        			JsonObject targetObj = (JsonObject) payloads.get("__btnProductDownload__");
		        			JsonObject properties = (JsonObject) targetObj.get("properties");
		        			String productUri = ((JsonElement) properties.get("down_product_new_path")).getAsString();
		        			boolean isUrlValid = true;
		        			try {
		        				URI uri = new URI(productUri);
		        			} catch(URISyntaxException ex) {
		        				isUrlValid = false;
		        			}
		        			if(!isUrlValid) {
		        				result = this.sendMessage(result, "Inserted URL " + productUri + " is not valid.");
		        			} else {
		        				if(!DatabaseUtility.getInstance().checkFileDownloading(productUri)) {
			        				DownloadAction dwnloadAction = DownloadAction.getInstance();
			        		        dwnloadAction.addDownload(productUri, "MANUAL_DOWNLOAD", -1, "", ConfigUtility.getFileDownloadDirectory(productUri, "").getAbsolutePath(), getServletContext());
				        			
			        			} else {
			        				result = this.sendMessage(result, "The download " + productUri + " is already running.");
			        			}
		        			}
		        			
		        			
		        			result = this.triggerRefrehGrid(result, "ngeo_if_grid");
		        		} 
		        	}
	        	}	
	        } else if(ssoStatus != null && ssoStatus.equals("LOGINFAILED") && getServletContext().getAttribute("SHOW_POPUP")==null) {
//        		SHOW ERROR POP UP
	        	log.error("Wrong SSO username/password.\n Please edit the configuration properties and restart the service.");
        		result = this.sendMessage(result, "Wrong SSO username/password.\n Please edit the configuration properties and restart the service.");
        		getServletContext().setAttribute("SHOW_POPUP", false);
        	} else if(command.getAsString().equalsIgnoreCase("fxgridevent_refresh")) {
        		 if(payloads.get("originalEventName").getAsString().equals("load_ngeo_if_grid")) {
        			//REFRSH REQUEST TO WS
//        			 System.out.println("REFRSH REQUEST TO WS");
        			RetrieveDARURLsThread retrieveDAR = (RetrieveDARURLsThread) getServletContext().getAttribute("RetrieveDARURLsThread");
        			retrieveDAR.stopThread();
        			retrieveDAR = new RetrieveDARURLsThread(getServletContext());
        			retrieveDAR.start();
        			getServletContext().setAttribute("RetrieveDARURLsThread", retrieveDAR);
        		 }
        		JsonObject grid = (JsonObject) payloads.get("ngeo_if_grid");
        		JsonObject properties = (JsonObject) grid.get("properties");
        		Long currTime = System.currentTimeMillis();
        		Long initialTime = (Long) getServletContext().getAttribute("initialTime");
        		result = this.refreshGrid(properties, "ngEO Download Manager uptime: " + (currTime-initialTime)/60000  + " minutes.");
    		 
        	}
	     
	        	
        	sendData(result, response);
	        	

	        } catch(Exception ex) {
	        	ex.printStackTrace();
	        }			
        } else {
	        String commandType = request.getParameter("commandType");
	        if(commandType.equals(new String("checkCredential"))) {
	        	String username = request.getParameter("username");
	        	String password = request.getParameter("password");
	        	if(checkCredential(username, password)) {
	        		request.getSession().setAttribute("username", username);
    	            request.getSession().setAttribute("password", password);
	        		out.print("JSESSIONID=" +request.getSession().getId());
	        	} else {
	        		out.print(false);
	        	}
				out.flush();
	        }
	        
	        if(!checkCredential((String) request.getSession().getAttribute("username"), 
	        			(String) request.getSession().getAttribute("password"))) {
	        	out.print("You are not logged in");
	        } else {
	        
		        if(commandType.equals(new String("-listAll"))) {
		        	List<Map<String,String>> downloads = DatabaseUtility.getInstance().listDownloads();
		        	out.write("List of downloads: Filename Status FileSize Progress FileSource DownloadId Network \n");
		        	for(Map<String,String> download : downloads) {
		        		 out.write(download.get("filename") + " ");
			                out.write(download.get("value") + " ");
			                out.write(download.get("filesize") + " ");
			                out.write(download.get("progress") + " ");
			                out.write(download.get("filesource") + " ");
			                out.write(download.get("gid") + " ");
			                out.write(download.get("network") + " ");
			                out.write("\n");
		        	}
				 } else if(commandType.equals(new String("-showConfig"))) {
					 Properties configProperties = ConfigUtility.loadConfig();
					 Enumeration<Object> keys = configProperties.keys();
					 while(keys.hasMoreElements()) {
						 String key = ( String) keys.nextElement();
						 out.print( key + ": " +  ( String) configProperties.getProperty(key) + "\n");
						 log.debug( key + ": " +  ( String) configProperties.getProperty(key) + "\n");
					 }			 
				 } else if(commandType.equals(new String("-testConfig"))) {
					 String[] values = new String[]{"WebServiceURLs","loglevel", "DMIdentifier", "repositoryDir"};
					 String[] usersValues = new String[]{"umssouser", "umssopwd"};
					 boolean isOK = true;
					 Properties properties = null;
					 Properties usersProperties = null;
					 try {
					  	String configPath = System.getProperty("configPath"); 
					  	File configFile = new File(configPath);
					  	InputStream stream  = new FileInputStream(configFile);
					  	properties = new Properties();
					  	properties.load(stream);
					  	stream.close();
					 } catch (IOException e) {
					      e.printStackTrace();
					 }
					 for (int n=0; n<values.length; n++) {
						 if(!properties.containsKey(values[n])) {
							 isOK = false;
							 out.print("Configuration file does not contain key " +values[n] + "\n");
							 log.debug("Configuration file does not contain key " +values[n] + "\n");
						 }
					 }
	 
	 
					 try {
						  	File usersFile = new File(usersConfigPath);
						  	InputStream stream  = new FileInputStream(usersFile);
						  	usersProperties = new Properties();
						  	usersProperties.load(stream);
						  	stream.close();
					  } catch (IOException e) {
					      e.printStackTrace();
					  }
					for (int m=0; m<usersValues.length; m++) {
						 if(!usersProperties.containsKey(usersValues[m])) {
							 isOK = false;
							 out.print("Users Configuration file does not contain key " +usersValues[m] + "\n");
							 log.error(" Users Configuration file does not contain key " +usersValues[m] + "\n");
						 }
					}
	
					if(isOK) {
						out.print("Configuration file well formed \n");
						log.debug("Configuration file well formed \n");
					}
					 
				 } else if(commandType.equals(new String("startDownload"))) {
					String url = request.getParameter("URI");
	//				Thread thread = new Thre)ad(new DownloadThread(url));
	//				thread.start();
					String gid = null;
					if(!DatabaseUtility.getInstance().checkFileDownloading(url)) {
	    				DownloadAction dwnloadAction = DownloadAction.getInstance();
	    				gid = dwnloadAction.addDownload(url, "MANUAL_DOWNLOAD", -1, "", ConfigUtility.getFileDownloadDirectory(url, "").getAbsolutePath(), getServletContext());
						out.print("Started download " + url + " with id " + gid);
					} else {
						out.print("The download " + url + " is already running.");
					 }
				 } else if(commandType.equals(new String("-info"))) {
					String gid = request.getParameter("gid");
					try  {					
						//GET DOWNLOAD INFORMATION FROM DATABASE
						out.print(DatabaseUtility.getInstance().getInfo(gid));
					} catch(Exception ex) {
						log.error(ex.getMessage());
						out.print(ex.getMessage());
					}
				} else if(commandType.equals(new String("-pause"))) {
					String gid = request.getParameter("gid");
					try {
						//IDownloadProcess down = (IDownloadProcess) Class.forName("it.acsys.aria2wrapper.IDownloadProcessImpl").getConstructor(String.class, String.class, it.acsys.download.downloadplugin.IProductDownloadListener.class).newInstance(rpcHost,gid,null);
						IDownloadProcess down = (IDownloadProcess) cache.get(gid);
						if(down.getStatus().equals(EDownloadStatus.RUNNING)){
							down.pauseDownload();
							out.print("Paused download with id " + gid);
							log.debug("Paused download with id " + gid);
						} else {
							out.print("Can not pause download with id " + gid);
							log.debug("Can not pause download with id " + gid);
						}
					} catch(Exception ex) {
						out.print(ex.getMessage());
						log.error(ex.getMessage());
					}
					
				} else if(commandType.equals(new String("-resume"))) {
					String gid = request.getParameter("gid");
					try {
						int statusId = DatabaseUtility.getInstance().getStatusId(gid);
						if(statusId == 3) {
							DatabaseUtility.getInstance().updateDownloadStatus(gid, 2);
							log.debug("Resumed download with id " + gid);
						} else {
							out.print("Can not resume download with id " + gid);
							log.debug("Can not resume download with id " + gid);
						}
					} catch(Exception ex) {
						log.error(ex.getMessage());
						out.print(ex.getMessage());
					}
				} else if(commandType.equals(new String("-remove"))) {
					String gid = request.getParameter("gid");
					try {
						IDownloadProcess down = (IDownloadProcess) cache.get(gid);
						if(down.getStatus().equals(EDownloadStatus.RUNNING)){
							down.cancelDownload();
							out.print("Removed download with id " + gid);
							log.debug("Removed download with id " + gid);
						} else {
							out.print("Can not remove download with id " + gid);
							log.debug("Can not remove download with id " + gid);
						}
					} catch(DMPluginException ex) {
						log.error(ex.getMessage());
						out.print(ex.getMessage());
					} catch(Exception ex) {
						log.error(ex.getMessage());
						out.print(ex.getMessage());
					}
				} else if(commandType.equals(new String("-changePriority"))) {
					String gid = request.getParameter("gid");
					IDownloadProcess down = (IDownloadProcess) cache.get(gid);
					if(down.getStatus().equals(EDownloadStatus.NOT_STARTED)){
						DatabaseUtility.getInstance().moveDownloadOnTopList(gid);
						out.print("Put download with id " + gid +" on top of waiting list");
						log.debug("Put download with id " + gid +" on top of waiting list");
					} else {
						out.print("Can not change priority for download with id " + gid);
						log.debug("Can not change priority for download with id " + gid);
					}
				}  else if(commandType.equals(new String("-setConfig"))) {
					String key = request.getParameter("key");
					String value = request.getParameter("value");
					Properties configProperties = ConfigUtility.loadConfig();
					if(!configProperties.containsKey(key)) {
						out.print( "Key " + key  + " not found in configuration file\n");
						log.error( "Key " + key  + " not found in configuration file\n");
					} else {
						String configPath = System.getProperty("configPath");
						if(key.equals("umssopwd") || key.equals("CLIPwd")) {
							try {
								AESCryptUtility aesCryptUtility = new AESCryptUtility();
								configProperties.put(key, aesCryptUtility.encryptString(value));
							} catch (Exception e) {
								e.printStackTrace();
							}
							
						} else {
							configProperties.put(key, value);
						}
						configProperties.save(new FileOutputStream(new File(configPath)), "");
						out.print( "New value " + value + " set for key " + key  + " in configuration file\n");
						log.debug("New value " + value + " set for key " + key  + " in configuration file\n");
					}
					
				} else if(commandType.equals(new String("-addDAR"))) {
					String DARUri = request.getParameter("DARUri");
					if(!DatabaseUtility.getInstance().checkDARDownloading(DARUri)) {
						executeDAR(DARUri, null, out);
					} else {
						out.print("DAR " + DARUri + " already downloading.");
					}
				} else if(commandType.equals(new String("-cancelDAR"))) {
					String DARUri = request.getParameter("DARUri");
					List<String> downloads = DatabaseUtility.getInstance().getDownloadsByDAR(DARUri, 1);
					StringBuilder sb = new StringBuilder();
					for(String download : downloads) {
						try {
							IDownloadProcess down = (IDownloadProcess) cache.get(download);
							down.cancelDownload();
							sb.append("Canceled download " + download + "\n");
						} catch(DMPluginException ex) {
							sb.append("Can not cancel download of DAR " + DARUri + "\n");
							out.print(sb.toString());
						}
					}
					sb.append("Canceled DAR " + DARUri + "\n");
					out.print(sb.toString());
				} else if(commandType.equals(new String("-pauseDAR"))) {
					String DARUri = request.getParameter("DARUri");
					List<String> downloads = DatabaseUtility.getInstance().getDownloadsByDAR(DARUri, 1);
					for (String download : downloads) {
						try {
							IDownloadProcess down = (IDownloadProcess) cache.get(download);
							down.pauseDownload();
							out.print("Paused download of DAR " + DARUri);
							log.debug("Paused download of DAR " + DARUri);
						} catch(DMPluginException e){
							e.printStackTrace();
							log.error("Can not pause DAR" + DARUri);
						}
						
					} 
				} else if(commandType.equals(new String("-resumeDAR"))) {
					String DARUri = request.getParameter("DARUri");
					List<String> downloads = DatabaseUtility.getInstance().getDownloadsByDAR(DARUri, 3);
					for (String download : downloads) {
						try {
							IDownloadProcess down = (IDownloadProcess) cache.get(download);
							down.resumeDownload();
							out.print("Resumed download of DAR " + DARUri);
							log.debug("Resumed download of DAR " + DARUri);
						} catch(DMPluginException e){
							log.error("Can not resume download of DAR " + DARUri);
						}
						
					} 
				}
	        }
	        out.flush();
        }
	}


	private void executeDAR(String DARUri, ArrayList result, PrintWriter out) throws IOException {
		PostMethod darMethod = new PostMethod(DARUri);
    	HttpClient client = new HttpClient();
	    client.getParams().setParameter("http.useragent", "My Browser");
	    try{
    	  FileInputStream monitoringFile = new FileInputStream("./templates/DMDARMonitoring.xml");
    	  DataInputStream in = new DataInputStream(monitoringFile);
    	  BufferedReader br = new BufferedReader(new InputStreamReader(in));
    	  StringBuffer sb = new StringBuffer();
    	  String strLine;
    	  while((strLine = br.readLine()) != null) {
    		  sb.append(strLine);
    	  }
    	  String filecontent = sb.toString();
    	  monitoringFile.close();
    	  in.close();
    	  br.close();
    	  Properties configProperties = ConfigUtility.loadConfig();
    	  filecontent = filecontent.replace("{DM_ID}", configProperties.getProperty("DMIdentifier"));    	  
    	  darMethod.setRequestBody(filecontent);
    	  darMethod.setRequestHeader(new Header("Content-Type", "application/xml"));
    	  client.executeMethod(darMethod);
    	  byte[] responseBody =  darMethod.getResponseBody();
    	  FileWriter fstream = new FileWriter(((String) configProperties.getProperty("DARsDir"))+"/DAR"+"_"+System.currentTimeMillis());
	      BufferedWriter outBuff = new BufferedWriter(fstream);
	      outBuff.write(new String(responseBody));
	      outBuff.close();
    	  DARParser DARParser = new DARParser();
    	  DARParser.parse( new String(responseBody), DARUri, -1, getServletContext());
    	  out.print("Started download of DAR " + DARUri);
		  log.debug("Started download of DAR " + DARUri);
	    } catch(Exception e) {
	    	if(result != null) {
	    		result = this.sendMessage(result, "The download " + DARUri + " gives the following error\n" + e.getMessage());
	    	}
	        log.error("Can not start download of DAR " + DARUri);
	    } finally {
	    	darMethod.releaseConnection();
	    	//result = DatabaseUtility.getInstance().getNewGridData(result, grid);
	    }
	}
	private void sendData(ArrayList result, HttpServletResponse response) throws IOException {
		Gson gson = new Gson();
		String json = gson.toJson(result);
		PrintWriter out= response.getWriter();
		out.write(Base64.encode(json.getBytes()));
		out.flush();
		response.flushBuffer();		
	}


	private ArrayList reloadPage() {
		ArrayList result = new ArrayList();
		HashMap resp = new HashMap();
		HashMap payload = new HashMap();
		resp.put("command", "EXEC");
		resp.put("targetId", "ngeo_if_EDITCONFIG");
		payload.put("_EXEC_METHOD_NAME_", "_RELOAD_SELF_");
		resp.put("payloads", payload);
		result.add(resp);
		
		return result;
	}
	
}




