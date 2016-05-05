package it.acsys.download;

import javax.servlet.ServletContext;

import org.apache.log4j.Logger;

import com.siemens.pse.umsso.client.UmssoUserCredentials;
import com.siemens.pse.umsso.client.UmssoVisualizerCallback;


public class CommandLineCallback implements UmssoVisualizerCallback {
	private static UmssoUserCredentials userCredentials; 
	private int userAttempts = 0;
	private ServletContext context = null;
	
	private static final String loginError = "Wrong SSO username/password.\n Please edit the configuration properties and restart the service.";
	
	private static Logger log = Logger.getLogger(CommandLineCallback.class);
	
	public CommandLineCallback(String username, char[] pwd, ServletContext context) {
		userCredentials = new UmssoUserCredentials(username, pwd);
		this.context = context;
	}

	public UmssoUserCredentials showLoginForm(String message, String spResourceUrl, String idpUrl) {
		System.out.println("user " + userCredentials.getUserName() + " attempt " + userAttempts);
		System.out.println("user pwd " + new String(userCredentials.getPassword()) + " attempt " + userAttempts);
		userAttempts++;
		if(userAttempts >= 2) {
			log.error(loginError);
			if(context != null)
				context.setAttribute("SSO_LOGIN_STATUS", "LOGINFAILED");
			return null;
		}
		return userCredentials;
	}
	
}
