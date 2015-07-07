<%@page import="java.io.FileReader"%>
<%@page import="java.io.BufferedReader"%>
<%@page import="java.io.IOException"%>
<%@page import="java.util.Properties"%>
<%@page contentType="text/html" pageEncoding="UTF-8"%>
        <%
        	Properties usersProperties = null;
        	Properties configProperties = null;
        
        	try {
	        	java.io.File usersFile = new java.io.File("./etc/users.properties");
	        	java.io.InputStream stream  = new java.io.FileInputStream(usersFile);
	        	usersProperties = new Properties();
	        	usersProperties.load(stream);
	        	stream.close();
	        	
	        	java.io.File configFile = new java.io.File("./config/config.properties");
	        	stream  = new java.io.FileInputStream(configFile);
	        	configProperties = new Properties();
	        	configProperties.load(stream);
	        	stream.close();
	        	
	        	
	        } catch (IOException e) {
	            e.printStackTrace();
	        }        	
        	
        	String jspPath = session.getServletContext().getRealPath("/");
        	BufferedReader reader = null;
        	if(usersProperties.get("CLIUsername").equals("") || (session.getAttribute("username") != null &&  session.getAttribute("password") != null)) {
        		if(configProperties.get("test") != null && configProperties.get("test").equals("true")) {
        			reader = new BufferedReader(new FileReader(jspPath + "/DownloadManager_javascript/index_test.html"));
        		} else {
        			reader = new BufferedReader(new FileReader(jspPath + "/DownloadManager_javascript/index.html"));
        		}
        	} else {
        		reader = new BufferedReader(new FileReader(jspPath + "/DownloadManager_javascript/login.html"));
        	}
        	
            StringBuilder sb = new StringBuilder();
            String line;

            while((line = reader.readLine())!= null){
                sb.append(line+"\n");
            }
            String errorMessage = (String) session.getAttribute("ERROR_MESSAGE");
            String fileContent = sb.toString();
            if(errorMessage != null) {
            	fileContent = fileContent.replace("__ERROR_MESSAGE__", errorMessage);
            } else {
            	fileContent = fileContent.replace("__ERROR_MESSAGE__", "");
            }
            out.println(fileContent.replace("{DM_ID}", (String) getServletContext().getAttribute("DM_ID")));
        %>