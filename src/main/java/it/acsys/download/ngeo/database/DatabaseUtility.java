package it.acsys.download.ngeo.database;

import int_.esa.eo.ngeo.downloadmanager.plugin.EDownloadStatus;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.naming.InitialContext;
import javax.sql.DataSource;

import org.apache.log4j.Logger;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;


public class DatabaseUtility {
	
	final ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();
	final Lock r = rwl.readLock();
	final Lock w = rwl.writeLock();
	private static final String connectionError = "Can not connect to database";
			
	private static Logger log = Logger.getLogger(DatabaseUtility.class);
	
	private static DatabaseUtility databaseUtility;
	
	private DatabaseUtility() {
	}

	public static DatabaseUtility getInstance() {

		if(databaseUtility == null) {

			databaseUtility = new DatabaseUtility();
		}

	    return databaseUtility;

	}

	public Connection getConnection() {
    	Connection con = null;
		try {
    		InitialContext ctx = new InitialContext();
    		DataSource ds = (DataSource) ctx.lookup("jdbc/DMdatabase");
    		con =  ds.getConnection();
    	} catch(Exception ex){
    		ex.printStackTrace();
    		log.error(ex.getMessage());
    	}
    	
    	return con;
    }
	
	
	
	public Map<String, Integer> getDownloadsToStop(int wsId, String stopType) {
		r.lock();
		Map<String, Integer> result = new HashMap<String, Integer>();
		String sql = "";
    	if(stopType.equals("STOP")) {
    		sql = "SELECT GID, STATUS_ID FROM DOWNLOADS WHERE WS_ID = ? AND STATUS_ID = 2 or STATUS_ID = 3";
    	} else if(stopType.equals("STOP_IMMEDIATELY")) {
    		sql = "SELECT GID, STATUS_ID FROM DOWNLOADS WHERE WS_ID = ? AND STATUS_ID <= 3";
    	}
		Connection con = getConnection();
		try {
			PreparedStatement pst = con.prepareStatement(sql);
			pst.setInt(1, wsId);
			ResultSet rset = pst.executeQuery();
			while(rset.next()){
				result.put(rset.getString("GID"), rset.getInt("STATUS_ID"));
			}
			rset.close();
		
		} catch(SQLException e){
			e.printStackTrace();
			log.error(e.getMessage());
		} finally {
			r.unlock();
			try {
				con.close();
			} catch(SQLException e) {
				e.printStackTrace();
			}
		}
		return result;
	}
	
	public boolean checkDARDownloading(String darUrl) {
		r.lock();
		Connection con = null;
		boolean result = false;
		try {
			con = getConnection();
			PreparedStatement pst = con.prepareStatement("select  gid from downloads where filesource = ? and (status_id = 1 or status_id = 3)");
			pst.setString(1, darUrl);
			ResultSet rset = pst.executeQuery();
			while(rset.next()) {
				result = true;
			}
			pst.close();
			rset.close();
		} catch (SQLException e) {
	         e.printStackTrace();
	         log.error(e.getMessage());
	     } finally {
	    	 r.unlock();
	    	try {
	    		if(con != null)
	   				con.close();
			} catch(SQLException e) {
				e.printStackTrace();
			}
     	}
		return result;
	}
	
	
	public String getStopped(String WebServiceURL, String downloadManagerId) {
		r.lock();   
		Connection con = getConnection();
			
			try {
				PreparedStatement pst = con.prepareStatement("select stop from WSURL where registrationurl = ? and dm_id = ?");
				pst.setString(1,WebServiceURL);
				pst.setString(2,downloadManagerId);
				ResultSet rset = pst.executeQuery();
				while(rset.next()) {
					return rset.getString("stop");
				}
	            pst.close();
	            rset.close();
	    } catch (SQLException e) {
	        e.printStackTrace();
	        log.error(e.getMessage());
	    } finally {
	    	r.unlock();
			try {
				con.close();
			} catch(SQLException e) {
				e.printStackTrace();
			}
		}
		
		return null;
			
	 }
	
	public void updateLastCall(String WebServiceURL, Timestamp lastCall, String dmIdentifier) {
		   w.lock();
		   Connection con = getConnection();
			
			try {
				PreparedStatement pst = con.prepareStatement("update WSURL set LASTCALL = ? where registrationurl = ? and dm_id = ?");
				pst.setTimestamp(1, lastCall);
				pst.setString(2, WebServiceURL);
				pst.setString(3, dmIdentifier);
				int n = pst.executeUpdate();
	            if(n == 0) {
	            	pst = con.prepareStatement("insert into WSURL (registrationurl, LASTCALL, dm_id) values(?,?, ?)");
	    			pst.setString(1, WebServiceURL);
	            	pst.setTimestamp(2, lastCall);
	            	pst.setString(3, dmIdentifier);
	            	pst.executeUpdate();
	            }
	            pst.close();	           
	    } catch (SQLException e) {
	        e.printStackTrace();
	        log.error(e.getMessage());
	    } finally {
	    	w.unlock();
			try {
				con.close();
			} catch(SQLException e) {
				e.printStackTrace();
			}
		}
			
	 }
	
	public Timestamp getLastCall(String WebServiceURL, String dmIdentifier) {
		r.lock();
		Connection con = getConnection();
		Timestamp lastCall = new Timestamp(0);
		try {
    		PreparedStatement pst = con.prepareStatement("select LASTCALL from WSURL where registrationurl = ? and dm_id = ?");
    		pst.setString(1, WebServiceURL);
    		pst.setString(2, dmIdentifier);
            ResultSet rs=pst.executeQuery();
            while(rs.next()){
                Timestamp lastTimestamp = rs.getTimestamp("LASTCALL");
                lastCall = new Timestamp(lastTimestamp.getTime());
            }
            pst.close();
            rs.close();
     } catch (SQLException e) {
         e.printStackTrace();
     } finally {
    	 r.unlock();
 		try {
				con.close();
			} catch(SQLException e) {
				e.printStackTrace();
			}
 	}
		return lastCall;
	}
	
	public void updateDMRegLastCall(String registrationURL, Timestamp lastCall, String dmIdentifier) {
		   w.lock();
		   Connection con = getConnection();
			
			try {
				PreparedStatement pst = con.prepareStatement("update WSURL set LASTCALL = ?, active = true where REGISTRATIONURL = ? and dm_id = ?");
				pst.setTimestamp(1, lastCall);
				pst.setString(2, registrationURL);
				pst.setString(3, dmIdentifier);
				int n = pst.executeUpdate();
	            if(n == 0) {
	            	pst = con.prepareStatement("insert into WSURL (REGISTRATIONURL, LASTCALL, ACTIVE, dm_id ) values(?,?, true, ?)");
	    			pst.setString(1, registrationURL);
	            	pst.setTimestamp(2, lastCall);
	            	pst.setString(3, dmIdentifier);
	            	pst.executeUpdate();
	            }
	            pst.close();	           
	    } catch (SQLException e) {
	        e.printStackTrace();
	        log.error(e.getMessage());
	    } finally {
	    	w.unlock();
			try {
				con.close();
			} catch(SQLException e) {
				e.printStackTrace();
			}
		}
			
	 }
	
	public int getWsId(String WebServiceURL, String dmIdentifier) {
		r.lock();
		Connection con = getConnection();
		int wsId = -1;
		try {
    		PreparedStatement pst = con.prepareStatement("select id from WSURL where registrationurl = ? and dm_id = ?");
    		pst.setString(1, WebServiceURL);
    		pst.setString(2, dmIdentifier);
    		ResultSet rs=pst.executeQuery();
            while(rs.next()){
            	wsId = rs.getInt("id");
            }
            pst.close();
            rs.close();
        } catch (SQLException e) {
         e.printStackTrace();
         log.error(e.getMessage());
         } finally {
        	 r.unlock();
     		try {
 				con.close();
 			} catch(SQLException e) {
 				e.printStackTrace();
 			}
         }
         return wsId;
	}
	
	public List<Map<String, String>> getDARInformation(String filesource) {
		r.lock();
		List<Map<String, String>> result = new ArrayList<Map<String, String>>();
		String sql = "SELECT GID, FILENAME, VALUE, PROGRESS, FILESIZE FROM DOWNLOADS INNER JOIN STATUS ON STATUS.ID= DOWNLOADS.STATUS_ID" +
				 " WHERE FILESOURCE = ? AND NOTIFIED = FALSE";
    	
		Connection con = getConnection();
		try {
			PreparedStatement pst = con.prepareStatement(sql);
			pst.setString(1, filesource);
			ResultSet rset = pst.executeQuery();
			while(rset.next()){
				Map<String, String> currmap = new HashMap<String, String>();
				currmap.put("product_identifier", rset.getString("GID"));
				currmap.put("product_access_URL", rset.getString("FILENAME").trim());
				String icdStatus = getIcdStatus(rset.getString("value").trim());
				currmap.put("product_download_status", icdStatus);
				currmap.put("product_download_progress", String.valueOf(rset.getInt("progress")));
				currmap.put("product_download_size", String.valueOf(rset.getInt("filesize")).trim());
				result.add(currmap);
			}
			rset.close();
			pst.close();		
		} catch(SQLException e){
			e.printStackTrace();
			log.error(e.getMessage());
		} finally {
			r.unlock();
			try {
				con.close();
			} catch(SQLException e) {
				e.printStackTrace();
			}
		}
		return result;
	}
	
	
	private String getIcdStatus(String dbStatus) {
		if(dbStatus.equalsIgnoreCase("RUNNING") || dbStatus.equalsIgnoreCase("PAUSED") || dbStatus.equalsIgnoreCase("IDLE")) {
			return "DOWNLOADING";
		} else if(dbStatus.equalsIgnoreCase("IN_ERROR")) {
			return "ERROR";
 		} else if(dbStatus.equalsIgnoreCase("CANCELLED")) {
			return "COMPLETED";
 		} else {
 			return dbStatus;
 		}
	}

	public int updateNotificationStatus(String gid) {
	    r.lock();
	    String sql = "update downloads set NOTIFIED = ? WHERE GID = ?";
	    Connection con = getConnection();
	    try {
	    	  PreparedStatement pst = con.prepareStatement(sql);
			  pst.setBoolean(1, true);
			  pst.setString(2, gid);
			  return pst.executeUpdate();
	    } catch(SQLException e) {
	    	e.printStackTrace();
	    	log.error("Can not update notified status into database " + gid);
	    } finally {
	    	r.unlock();
			if (con != null)
				try {
					con.close();
		    	}  catch(SQLException sqlEx) {
		    		sqlEx.printStackTrace();
		    		log.error(connectionError);
		    	}
	    }
	    return -1;
    } 
	
	public int updateErrorMessage(String gid, String errorMessage) {
	    r.lock();
	    String sql = "update downloads set ERROR_MESSAGE = ? WHERE GID = ?";
	    Connection con = getConnection();
	    try {
	    	  PreparedStatement pst = con.prepareStatement(sql);
			  pst.setString(1, errorMessage);
			  pst.setString(2, gid);
			  return pst.executeUpdate();
	    } catch(SQLException e) {
	    	e.printStackTrace();
	    	log.error("Can not update error message into database " + gid);
	    } finally {
	    	r.unlock();
			if (con != null)
				try {
					con.close();
		    	}  catch(SQLException sqlEx) {
		    		sqlEx.printStackTrace();
		    		log.error(connectionError);
		    	}
	    }
	    return -1;
    } 
	
	public Map<String, String> getDownloadsByStatusId(int statusId) {
		r.lock();
	    String sql = "select realuri, GID from downloads WHERE STATUS_ID = ? ";
	    Connection con = getConnection();
	    Map<String, String> result = new HashMap<String, String>();
	    try {
	    	PreparedStatement pst = con.prepareStatement(sql);
	    	pst.setInt(1, statusId);
			ResultSet rset = pst.executeQuery();
			while(rset.next()){
				String url = rset.getString("realuri");
				String gid = rset.getString("GID");
				result.put(gid, url);
			}
			rset.close();
			  
	    } catch(SQLException e) {
	    	e.printStackTrace();
	    	log.error("Can not get downloads from db with state " + statusId);
	    } finally {
	    	r.unlock();
			if (con != null)
				try {
					con.close();
		    	}  catch(SQLException sqlEx) {
		    		sqlEx.printStackTrace();
		    		log.error(connectionError);
		    	}
	    }
	    return result;
	}
	
	
	public  void progress(Integer progress, Long completedLength, EDownloadStatus status, String gid) {
		w.lock();
		Connection con = getConnection();
		try {
			String sqlUpdate = "UPDATE downloads set status_id=?, filesize=?, progress=? where gid = ?";
			PreparedStatement pst = con.prepareStatement(sqlUpdate);
			pst.clearParameters();
			Integer statusId = this.getStatusId(status);
			pst.setInt(1,statusId);
			pst.setLong(2,completedLength);
			pst.setLong(3,progress);
			pst.setString(4,gid);
			pst.executeUpdate();
		    pst.close();
		} catch (SQLException e) {
		    e.printStackTrace();
		    log.error(e.getMessage());
		} finally {
			w.unlock();
			try {
				con.close();
			} catch(SQLException e) {
				e.printStackTrace();
			}
		}
	}
	
	public void purgeDB() {
		w.lock();
		Connection con = null;
		try {
			con = getConnection();
			PreparedStatement pst = con.prepareStatement("delete from downloads where status_id >= 4 and status_id < 7");
			pst.executeUpdate();
//			pst = con.prepareStatement("delete from WSURL");
//			pst.executeUpdate();
			pst.close();
		} catch (SQLException e) {
	         e.printStackTrace();
	         log.error(e.getMessage());
	     } finally {
	    	 w.unlock();
	    	try {
	    		if(con != null)
	   				con.close();
			} catch(SQLException e) {
				e.printStackTrace();
			}
     	}
	}
	
	
	
	private Integer getStatusId(EDownloadStatus status) {
		String sql = "SELECT ID FROM STATUS WHERE VALUE = '" + status.toString()+ "'";
		Connection con = getConnection();
		try {
			Statement statement = con .createStatement();
			ResultSet rset = statement.executeQuery(sql);
			while(rset.next())
				return rset.getInt("id");
			rset.close();
			statement.close();
		} catch(SQLException e){
			e.printStackTrace();
			log.error(e.getMessage());
		} finally {
			try {
				con.close();
			} catch(SQLException e) {
				e.printStackTrace();
			}
		}
		
		return -1;
	}

	public void updateProductDetails(String processIdentifier, String filename, int numFiles, long totalLength) {
		w.lock();
		Connection con = getConnection();
		try {
			String sqlUpdate = "UPDATE downloads set basename=?,  numfiles=?, filesize=? where gid = ?";
			PreparedStatement pst = con.prepareStatement(sqlUpdate);
			pst.setString(1,filename);
			pst.setInt(2,numFiles);
			pst.setLong(3,totalLength);
			pst.setString(4,processIdentifier);
			pst.executeUpdate();
		    pst.close();
		} catch (SQLException e) {
		    e.printStackTrace();
		    log.error(e.getMessage());
		} finally {
			w.unlock();
			try {
				con.close();
			} catch(SQLException e) {
				e.printStackTrace();
			}
		}
	}

	public void killDownload(String gid) {
		w.lock();
		Connection con = getConnection();
		try {
			String sqlUpdate = "UPDATE DOWNLOADS SET STATUS_ID = 6 WHERE GID = ?";
			PreparedStatement pst = con.prepareStatement(sqlUpdate);
			pst.setString(1,gid);
			pst.executeUpdate();
		    pst.close();
		} catch (SQLException e) {
		    e.printStackTrace();
		    log.error(e.getMessage());
		} finally {
			w.unlock();
			try {
				con.close();
			} catch(SQLException e) {
				e.printStackTrace();
			}
		}
		
	}

	public void stopWS(int wsId, String stopType) {
		w.lock();
		Connection con = getConnection();
		try {
			String sqlUpdate = "UPDATE WSURL SET STOP = ? WHERE ID = ?";
			PreparedStatement pst = con.prepareStatement(sqlUpdate);
			pst.setString(1,stopType);
			pst.setInt(2,wsId);
			pst.executeUpdate();
		    pst.close();
		} catch (SQLException e) {
		    e.printStackTrace();
		    log.error(e.getMessage());
		} finally {
			w.unlock();
			try {
				con.close();
			} catch(SQLException e) {
				e.printStackTrace();
			}
		}		
	}
	
	private String formatFileSize(String size) {
		String result = "";
        float filesize = Float.valueOf(size);
        DecimalFormat formatter = new DecimalFormat("#.##");
		if(filesize > 1073741824) {
			result = formatter.format(filesize/1073741824) + " GB." ;
        } else if(filesize > 1048576) {
        	result = formatter.format(filesize/1048576) + " MB." ;
        } else if(filesize >  1024) {
        	result = formatter.format(filesize/1024) + " KB." ;
        } else {
        	result = formatter.format(filesize) + " bytes" ;
        }
        
        return result; 
	}
	
	public List getNewGridData(ArrayList result, JsonObject payloads)  {
		r.lock();
		JsonObject postData = (JsonObject) payloads.get("postData");
		String whereCond = this.createWhereCond(postData);
		String sortCondition = this.createSortCond(postData);
		Integer rows = Integer.valueOf(postData.get("rows").getAsString());
		Integer page = Integer.valueOf(postData.get("page").getAsString());
		
		String nd = postData.get("nd").getAsString();
		Integer start = rows*page-rows;
		String limit = " LIMIT " + start + ", " + rows;
		
		
		if(result == null){
			result = new ArrayList();
		}
		HashMap resp = new HashMap();
		HashMap payload = new HashMap();
		HashMap data = new HashMap();
		ArrayList values = new ArrayList();
		HashMap args = new HashMap();
		int count = 0;
		Connection con = null;
		int maxPriority = 1;
		try {
			con = getConnection();
			PreparedStatement pst = con.prepareStatement("select count(gid) as cl  from downloads " + whereCond );
			ResultSet rs = pst.executeQuery();
			while(rs.next()){
				count = rs.getInt("cl");
			}
			pst = con.prepareStatement("select max(priority) as maxPriority  from downloads");
			rs = pst.executeQuery();
			while(rs.next()){
				maxPriority = rs.getInt("maxPriority");
			}
	        pst = con.prepareStatement("select start_time, realuri, filename, name, status_id, ageNew, filesize, progress, filesource, gid, network, priority, dar_status, error_message from downloads left join monitoringurl on downloads.filesource=monitoringurl.url " + whereCond + sortCondition + limit);
	        
	        pst.clearParameters();
	        rs = pst.executeQuery();
	        
	        
	        while(rs.next()){
	        	HashMap val1 = new HashMap();
	        	val1.put("gid", rs.getString("gid"));
	        	//val1.put("filename",rs.getString("filename"));
	        	val1.put("filename",rs.getString("realuri"));
	        	val1.put("filesize", formatFileSize(rs.getString("filesize")));
	        	int percentage = rs.getInt("progress");
	        	val1.put("progress", percentage);
	        	String darName = rs.getString("name");
	        	if(darName == null || darName.equals("")) {
	        		darName =  rs.getString("filesource");
	        	}
	        	val1.put("filesource", darName); //+ "- " + rs.getString("dar_status"));
	        	val1.put("network", rs.getString("network"));
	        	val1.put("status_id", rs.getInt("status_id"));
	        	val1.put("ageNew", rs.getTimestamp("ageNew").toString());
	        	if(rs.getInt("status_id") == 2 && rs.getInt("priority") == 1 && maxPriority > 1) {
	        		val1.put("selectedRow", "#ffff00");
	        	} else {
	        		val1.put("selectedRow", "");
	        	}
	        	val1.put("error_message", rs.getString("error_message"));
	        	values.add(val1);
	        }
	        rs.close();
	        pst.close();
	     } catch (SQLException e) {
	         e.printStackTrace();
	         log.error(e.getMessage());
	     } finally {
	    	 r.unlock();
    		try {
    			if(con != null)
    				con.close();
			} catch(SQLException e) {
				e.printStackTrace();
			}
    	}
		Integer totalPage = 0;
		if((count%rows) == 0) {
		 totalPage = count/rows;
		} else {
		 totalPage = (count/rows) + 1;
		}
		data.put("page", page);
		data.put("records", count);
		data.put("total", totalPage);
		data.put("rows",values);
		data.put("nd",nd);
		args.put("data", data);
		payload.put("_EXEC_METHOD_NAME_","dataProvider");
		payload.put("_ARGV_",args);
		resp.put("command","EXEC");
		resp.put("targetId","ngeo_if_grid");
		resp.put("payloads",payload);
		result.add(resp);
		
		return result;
	}
	
	private String createWhereCond(JsonObject payloads){
		String whereCond = " where 1=1 ";
		if(payloads.get("filters") != null) {
			JsonParser parser = new JsonParser();
	    	JsonObject filters = (JsonObject) parser.parse(((JsonPrimitive) payloads.get("filters")).getAsString());			
		
	 		ArrayList<String> where_arr = new ArrayList<String>();
	 		
	 			
	 		JsonArray rules = (JsonArray) filters.get("rules");
			if(rules != null) {
				for(int n=0; n<rules.size(); n++) {
					JsonObject currRule = (JsonObject) rules.get(n);
					String field = currRule.get("field").getAsString();
	            	String op = currRule.get("op").getAsString();
	            	String v = currRule.get("data").getAsString();
	            	String comp = "";
	            	if(op.equalsIgnoreCase("eq")) {
	            		comp = "=";
	            	} else if(op.equalsIgnoreCase("ne")) {
	            		comp = "!=";
	            	} else if(op.equalsIgnoreCase("lt")) {
	            		comp = "<";
	            	} else if(op.equalsIgnoreCase("le")) {
	            		comp = "<=";
	            	} else if(op.equalsIgnoreCase("gt")) {
	            		comp = ">";
	            	} else if(op.equalsIgnoreCase("ge")) {
	            		comp = ">=";
	            	} else if(op.equalsIgnoreCase("bw")) {
	            		comp = " like ";
	            		v = v + "%";
	            	} else if(op.equalsIgnoreCase("ew")) {
	            		comp = " like ";
	            		v = "%" + v;
	            	} else if(op.equalsIgnoreCase("bn")) {
	            		comp = " not like ";
	            		v = v + "%";
	            	} else if(op.equalsIgnoreCase("en")) {
	            		comp = " not like ";
	            		v = "%" + v;
	            	} else if(op.equalsIgnoreCase("cn")) {
	            		comp = " like ";
	            		v = "%" + v + "%";
	            	} else if(op.equalsIgnoreCase("nc")) {
	            		comp = " not like ";
	            		v = "%" + v + "%";
	            	} else if(op.equalsIgnoreCase("nu")) {
	            		comp = " is ";
	            		v = "null";
	            	} else if(op.equalsIgnoreCase("nn")) {
	            		comp = " is not ";
	            		v = "null";
	            	}
	            	
	            	if(field.equalsIgnoreCase("ageNew")) {
	            		SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy H:m:s");
	            		try {
	            			Date date = dateFormat.parse(v + " 00:00:00");
	            			dateFormat.applyPattern("yyyy-MM-dd H:m:s");
	            			v = dateFormat.format(date);
	            		} catch(java.text.ParseException ex) {
	            			ex.printStackTrace();
	            		}
	            		
	            	}	            	
	            	if(!field.equals("status_id") || !v.equals("0")) {
	            		where_arr.add( field + comp + "'"+v+"'");
	            	}
				}
				Iterator<String> iterator = where_arr.iterator();
				while(iterator.hasNext()) { 
					whereCond += " AND " + iterator.next();
				}
	        	
			}
		}
		return	whereCond;
	}
	
	private String createSortCond(JsonObject payloads){
		String sortCond = "";
		JsonElement sortField = payloads.get("sidx");
		if(!sortField.getAsString().equals("")) {
			JsonElement sortOrd = payloads.get("sord");
			if(sortField.getAsString().contains("ageNew")) {
				sortCond += " order by  start_time desc " + ", agenew " + sortOrd.getAsString();
			} else {
				sortCond += " order by "+ sortField.getAsString() + " " + sortOrd.getAsString();
			}
		}
		return	sortCond;
	}
	
	public String getInfo(String gid)  {
		r.lock();
		String info = "";
		Connection con = null;
		try {
			con = getConnection();
	        PreparedStatement pst = con.prepareStatement("select id,filename,value, ageNew, filesize, progress, filesource, gid, network from downloads  " +
	        		" inner join status on status.id = downloads.status_id where gid = ?");
	        pst.setString(1, gid);
	        ResultSet rs = pst.executeQuery();
	        while(rs.next()){
	        	info  = "\nFilename "+ rs.getString("filename") + "\nStatus " + rs.getString("value") + "\nDate " + rs.getString("ageNew") + "\nFilesize " + rs.getString("filesize") +
	        			"\nProgress " + rs.getString("progress") +"\nNetwork " + rs.getString("network");
	        }
	        rs.close();
	        pst.close();
	     } catch (SQLException e) {
	         e.printStackTrace();
	         log.error(e.getMessage());
	     } finally {
	    	 r.unlock();
    		try {
    			if(con != null)
    				con.close();
			} catch(SQLException e) {
				e.printStackTrace();
			}
    	}
		
		return info;
	}
	
	
	public List<Integer> getDownloadsIdByDAR(String DARUri, int statusId) {
		w.lock();
		List<Integer> result = new ArrayList<Integer>();
		Connection con = getConnection();
		try {
			PreparedStatement pst = con.prepareStatement("SELECT ID FROM DOWNLOADS WHERE FILESOURCE = ? and status_id = ?");
			pst.setString(1, DARUri);
			pst.setInt(2, statusId);
			ResultSet rset = pst.executeQuery();
			while(rset.next()) {
				result.add(rset.getInt("ID"));
			}
			pst.close();
			rset.close();
		} catch(SQLException e){
			e.printStackTrace();
			log.error("Can not select id of downloads for DAR " + DARUri);
		} finally {
			w.unlock();
			try {
				con.close();
			} catch(SQLException e) {
				e.printStackTrace();
			}
		}
		return result;
		
	}

	public List<String> getDownloadsByDAR(String DARUri, int statusId) {
		w.lock();
		List<String> result = new ArrayList<String>();
		Connection con = getConnection();
		try {
			PreparedStatement pst = con.prepareStatement("SELECT GID FROM DOWNLOADS WHERE FILESOURCE = '" + DARUri + "' and status_id = ?");
			pst.setInt(1, statusId);
			ResultSet rset = pst.executeQuery();
			while(rset.next()) {
				result.add(rset.getString("GID"));
			}
			pst.close();
			rset.close();
		} catch(SQLException e){
			e.printStackTrace();
			log.error("Can not select download of DAR " + DARUri);
		} finally {
			w.unlock();
			try {
				con.close();
			} catch(SQLException e) {
				e.printStackTrace();
			}
		}
		return result;
		
	}
	
	public List<String> getActiveDownloadsByDAR(String dARUri) {
		w.lock();
		List<String> result = new ArrayList<String>();
		Connection con = getConnection();
		try {
			PreparedStatement pst = con.prepareStatement("SELECT GID FROM DOWNLOADS WHERE FILESOURCE = ? and status_id <= 3");
			pst.setString(1, dARUri);
			ResultSet rset = pst.executeQuery();
			while(rset.next()) {
				result.add(rset.getString("GID"));
			}
			pst.close();
			rset.close();
		} catch(SQLException e){
			e.printStackTrace();
			log.error("Can not select download of DAR " + dARUri);
		} finally {
			w.unlock();
			try {
				con.close();
			} catch(SQLException e) {
				e.printStackTrace();
			}
		}
		return result;
		
	}
	
	public boolean checkFileDownloading(String url) {
		r.lock();
		Connection con = null;
		boolean result = false;
		try {
			con = getConnection();
			PreparedStatement pst = con.prepareStatement("select  gid from downloads where filename = ? and (status_id = 1 or status_id = 2)");
			pst.setString(1, url);
			ResultSet rset = pst.executeQuery();
			while(rset.next()) {
				result = true;
			}
			pst.close();
			rset.close();
		} catch (SQLException e) {
	         e.printStackTrace();
	         log.error(e.getMessage());
	     } finally {
	    	 r.unlock();
	    	try {
	    		if(con != null)
	   				con.close();
			} catch(SQLException e) {
				e.printStackTrace();
			}
     	}
		
		return result;
	}
	
	public List<Map<String,String>> listDownloads() {
		r.lock();
		List<Map<String,String>> result = new ArrayList<Map<String,String>>();
		Connection con = null;
		
		try {
			con = getConnection();
			PreparedStatement pst = con.prepareStatement("select filename,value, filesize, progress, filesource, gid, network from downloads inner join status on status.id = downloads.status_id");
            pst.clearParameters();
            ResultSet rset = pst.executeQuery();
			while(rset.next()) {
				HashMap<String,String> map = new HashMap<String,String>();
				map.put("filename", rset.getString("filename"));
				map.put("value", rset.getString("value"));
				map.put("filesize", rset.getString("filesize"));
				map.put("progress", rset.getString("progress"));
				map.put("filesource", rset.getString("filesource"));
				map.put("gid", rset.getString("gid"));
				map.put("network", rset.getString("network"));
				result.add(map);
			}
			pst.close();
			rset.close();
		} catch (SQLException e) {
	         e.printStackTrace();
	         log.error(e.getMessage());
	     } finally {
	    	 r.unlock();
	    	try {
	    		if(con != null)
	   				con.close();
			} catch(SQLException e) {
				e.printStackTrace();
			}
     	}
		
		return result;
	}
	
	public boolean checkFileDownloading(String url, String darUrl) {		
		boolean result = false;
		Connection con = getConnection();
		try {
			PreparedStatement pst = con.prepareStatement("select  gid from downloads where filename = ? and (status_id = 1 or status_id = 3 or status_id = 2)");
			pst.setString(1, url);
			ResultSet rset = pst.executeQuery();
			while(rset.next()) {
				log.warn("Already downloading file " + url);
				return true;
			}
			
			pst = con.prepareStatement("select  gid from downloads where filename = ? and filesource = ? and status_id = 5");
			pst.setString(1, url);
			pst.setString(2, darUrl);
			rset = pst.executeQuery();
			while(rset.next()) {
				log.warn("Already downloaded file " + url + " for DAR " + darUrl);
				return true;
			}
		} catch (SQLException e) {
	         e.printStackTrace();
	         log.error(e.getMessage());
	     } finally {
	    	try {
	    		if(con != null)
	   				con.close();
			} catch(SQLException e) {
				e.printStackTrace();
			}
     	}
		
		return result;
	}
	
	public void moveDownloadOnTopList(String gid) {
		w.lock();
		Connection con = getConnection();
		try {
			PreparedStatement pst = con.prepareStatement("update downloads set priority = priority + 1");
			pst.executeUpdate();
			pst.clearParameters();
			pst = con.prepareStatement("update downloads set priority = 1 where gid = ?");
			pst.setString(1, gid);
			pst.executeUpdate();			
		} catch (SQLException e) {
	         e.printStackTrace();
	         log.error(e.getMessage());
	     } finally {
	    	 w.unlock();
	    	try {
	    		if(con != null)
	   				con.close();
			} catch(SQLException e) {
				e.printStackTrace();
			}
     	}
	}

	public HashMap<String, String> getUriToDownload(int maximum) {
		r.lock();
		HashMap<String, String> result = new HashMap<String, String>();
		Connection con = null;
		try {
			String sql = "SELECT ID, FILENAME, GID  FROM DOWNLOADS " +
					" WHERE STATUS_ID = 2 order by priority limit " + maximum;
			con = getConnection();
			con.setAutoCommit(false);
	    	Statement statement = con .createStatement();
			ResultSet rset = statement.executeQuery(sql);
			PreparedStatement ps = con.prepareStatement("UPDATE DOWNLOADS SET STATUS_ID = 1 WHERE ID = ?");
			while(rset.next()) {
				int downloadId = rset.getInt("ID");
				result.put(rset.getString("GID"),  rset.getString("FILENAME").trim());
				ps.clearParameters();
				ps.setInt(1, downloadId);
				ps.executeUpdate();
			}
			
			con.commit();
			statement.close();
			ps.close();
			rset.close();			
		} catch(SQLException e) {
			e.printStackTrace();
	    	log.error("Can not get uris to download from db");
	    	if (con != null) {
				try {
					con.rollback();
				} catch (SQLException e1) {
					e1.printStackTrace();
				}
			}
	    } finally {
	    	r.unlock();
	    	if (con != null) {
				try {
					con.setAutoCommit(true);
					con.close();
		    	}  catch(SQLException sqlEx) {
		    		log.error(connectionError);
		    	}
			}
	    }
		
		return result;
    } 
	
	
	public int getRunningDownloads() {
		r.lock();
		Connection con = null;
		int running = 0;
		try {
			String sql = "SELECT COUNT(GID)  FROM DOWNLOADS WHERE STATUS_ID = 1";
			con = getConnection();
	    	Statement statement = con .createStatement();
			ResultSet rset = statement.executeQuery(sql);
			while(rset.next()) {
				running = rset.getInt(1);
			}
		} catch(SQLException e) {
			e.printStackTrace();
	    	log.error("Can not get downloads to resume from db");
	    } finally {
	    	r.unlock();
			if (con != null)
				try {
					con.close();
		    	}  catch(SQLException sqlEx) {
		    		sqlEx.printStackTrace();
		    		log.error(connectionError);
		    	}
	    }
		
		return running;
	}

	public void updateDownloadStatus(Integer id, int statusId) {
		w.lock();
		Connection con = getConnection();
		try {
			PreparedStatement pst = con.prepareStatement("update downloads set status_id = ? where id = ?");
			pst.setInt(1, statusId);
			pst.setInt(2, id);
			pst.executeUpdate();		
		} catch (SQLException e) {
	         e.printStackTrace();
	         log.error(e.getMessage());
	     } finally {
	    	 w.unlock();
	    	try {
	    		if(con != null)
	   				con.close();
			} catch(SQLException e) {
				e.printStackTrace();
			}
     	}		
	}
	
	public void updateDownloadStatus(String id, int statusId) {
		w.lock();
		Connection con = getConnection();
		try {
			PreparedStatement pst = con.prepareStatement("update downloads set status_id = ? where gid = ?");
			pst.setInt(1, statusId);
			pst.setString(2, id);
			pst.executeUpdate();		
		} catch (SQLException e) {
	         e.printStackTrace();
	         log.error(e.getMessage());
	     } finally {
	    	 w.unlock();
	    	try {
	    		if(con != null)
	   				con.close();
			} catch(SQLException e) {
				e.printStackTrace();
			}
     	}		
	}
	
	public List<String> getRegistrationUrls() {
		r.lock();   
		Connection con = getConnection();
		List<String> result = new ArrayList<String>();	
			try {
				String sql = "select  registrationurl from WSURL where unreachable = false and active = true and registered = false";
				Statement statement = con .createStatement();
				ResultSet rset = statement.executeQuery(sql);
				while(rset.next()) {
					result.add(rset.getString("registrationurl"));
				}
	            rset.close();
	    } catch (SQLException e) {
	        e.printStackTrace();
	        log.error(e.getMessage());
	    } finally {
	    	r.unlock();
			try {
				con.close();
			} catch(SQLException e) {
				e.printStackTrace();
			}
		}
		
		return result;
	}

	public Map<String, Integer> getWSUrls(String dmIdentifier) {
		r.lock();   
		Connection con = getConnection();
		Map<String, Integer> result = new HashMap<String, Integer>();	
			try {
				String sql = "select  registrationurl, id from WSURL where registered = true and stop is null and active = true and dm_id = ?";
				PreparedStatement pst = con.prepareStatement(sql);
				pst.setString(1, dmIdentifier);
				ResultSet rset = pst.executeQuery();
				while(rset.next()) {
					result.put(rset.getString("registrationurl"), rset.getInt("id"));
				}
	            rset.close();
	    } catch (SQLException e) {
	        e.printStackTrace();
	        log.error(e.getMessage());
	    } finally {
	    	r.unlock();
			try {
				con.close();
			} catch(SQLException e) {
				e.printStackTrace();
			}
		}
		
		return result;
	}
	
	
	public String getWSUrlsStatus(String dmIdentifier) {
		r.lock();   
		Connection con = getConnection();
		String status = "acs_active";
			try {
				String sql = "select  registrationurl, stop, unreachable, active, registered from WSURL where dm_id = ?";
				PreparedStatement pst = con.prepareStatement(sql);
				pst.setString(1, dmIdentifier);
				ResultSet rset = pst.executeQuery();
				
				while(rset.next()) {
//					System.out.println("stop" + rset.getString("stop"));
//					System.out.println("unreachable" + rset.getBoolean("unreachable"));
//					System.out.println("active" + rset.getBoolean("active"));
//					System.out.println("registered" + rset.getBoolean("registered"));
					if((rset.getString("stop") != null) || rset.getBoolean("unreachable") || !rset.getBoolean("active") || !rset.getBoolean("registered")) {
						status = "acs_error";
						break;
					}
				}
	            rset.close();
	    } catch (SQLException e) {
	        e.printStackTrace();
	        log.error(e.getMessage());
	    } finally {
	    	r.unlock();
			try {
				con.close();
			} catch(SQLException e) {
				e.printStackTrace();
			}
		}
		
		return status;
	}
	
	public int getRefreshPeriodByWsId(int wsId) {
		r.lock();   
		Connection con = getConnection();
		int result = 20000;	
			try {
				String sql = "select  refreshperiod from WSURL where id = ?";
				PreparedStatement pst = con.prepareStatement(sql);
				pst.setInt(1, wsId);
				ResultSet rset = pst.executeQuery();
				while(rset.next()) {
					result =  rset.getInt("refreshperiod");
				}
	            rset.close();
	    } catch (SQLException e) {
	        e.printStackTrace();
	        log.error(e.getMessage());
	    } finally {
	    	r.unlock();
			try {
				con.close();
			} catch(SQLException e) {
				e.printStackTrace();
			}
		}
		
		return result;
	}
	
	public Timestamp getWSUrlFirstFailedCall(String registrationUrl, String dmIdentifier) {
		r.lock();   
		Connection con = getConnection();
		Timestamp time = null;	
			try {
				String sql = "select  first_failed_call from WSURL where registrationurl = ? and dm_id = ?";
				PreparedStatement pst = con.prepareStatement(sql);
				pst.setString(1, registrationUrl);
				pst.setString(2, dmIdentifier);
				ResultSet rset = pst.executeQuery();
				while(rset.next()) {
					time = rset.getTimestamp("first_failed_call");
				}
	            rset.close();
	    } catch (SQLException e) {
	        e.printStackTrace();
	        log.error(e.getMessage());
	    } finally {
	    	r.unlock();
			try {
				con.close();
			} catch(SQLException e) {
				e.printStackTrace();
			}
		}
		
		return time;
	}

	public void updateWSUrlFirstFailedCall(String registrationUrl, String dmIdentifier) {
		w.lock();
		Connection con = getConnection();
		try {
			PreparedStatement pst = con.prepareStatement("update WSURL set first_failed_call = ? where registrationurl = ? and dm_id = ?");
			pst.setTimestamp(1, new Timestamp(System.currentTimeMillis()));
			pst.setString(2, registrationUrl);
			pst.setString(3, dmIdentifier);
			int n = pst.executeUpdate();
			if(n==0) {
				 pst = con.prepareStatement("insert into WSURL (registrationurl, first_failed_call,dm_id) values(?, ?, ?)");
				 pst.setString(1, registrationUrl);
				 pst.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
				 pst.setString(3, dmIdentifier);
		         pst.executeUpdate();
			}
		} catch (SQLException e) {
	         e.printStackTrace();
	         log.error(e.getMessage());
	     } finally {
	    	 w.unlock();
	    	try {
	    		if(con != null)
	   				con.close();
			} catch(SQLException e) {
				e.printStackTrace();
			}
     	}
		
	}
	
	public void resetWSUrlFirstFailedcall(String registrationUrl, String dmIdentifier) {
		w.lock();
		Connection con = getConnection();
		try {
			PreparedStatement pst = con.prepareStatement("update WSURL set first_failed_call = ? where registrationurl = ? and dm_id = ?");
			pst.setTimestamp(1, null);
			pst.setString(2, registrationUrl);
			pst.setString(3, dmIdentifier);
			int n = pst.executeUpdate();
			if(n==0) {
				 pst = con.prepareStatement("insert into WSURL (registrationurl, first_failed_call, dm_id) values(?, ?, ?)");
				 pst.setString(1, registrationUrl);
				 pst.setTimestamp(2, null);
				 pst.setString(3, dmIdentifier);
		         pst.executeUpdate();
			}
		} catch (SQLException e) {
	         e.printStackTrace();
	         log.error(e.getMessage());
	     } finally {
	    	 w.unlock();
	    	try {
	    		if(con != null)
	   				con.close();
			} catch(SQLException e) {
				e.printStackTrace();
			}
     	}
		
	}
	
	
	public void updateWSUrlUnreachableStatus(String registrationUrl, String downloadManagerId) {
		w.lock();
		Connection con = getConnection();
		try {
			PreparedStatement pst = con.prepareStatement("update WSURL set unreachable = true where registrationurl = ? and dm_id = ?");
			pst.setString(1, registrationUrl);
			pst.setString(2, downloadManagerId);
			int n =  pst.executeUpdate();
			if(n == 0) {
	        	pst = con.prepareStatement("insert into WSURL (registrationurl, unreachable, dm_id) values(?, true, ?)");
				pst.setString(1, registrationUrl);
				pst.setString(2, downloadManagerId);
	        	pst.executeUpdate();
	        }
	        pst.close();
		} catch (SQLException e) {
	         e.printStackTrace();
	         log.error(e.getMessage());
	     } finally {
	    	 w.unlock();
	    	try {
	    		if(con != null)
	   				con.close();
			} catch(SQLException e) {
				e.printStackTrace();
			}
     	}
		
	}
	
	public void initializeMonitoringUrl(String webServiceURL, String downloadManagerId) {
		 w.lock();
		   Connection con = getConnection();
			
			try {
				PreparedStatement pst = con.prepareStatement("update WSURL set REGISTERED = true where REGISTRATIONURL = ? and dm_id = ?");
				pst.setString(1, webServiceURL);
				pst.setString(2, downloadManagerId);
				pst.executeUpdate();
	            pst.close();	           
	    } catch (SQLException e) {
	        e.printStackTrace();
	        log.error(e.getMessage());
	    } finally {
	    	w.unlock();
			try {
				con.close();
			} catch(SQLException e) {
				e.printStackTrace();
			}
		}
		
	}

	public int getStatusId(String gid) {
		r.lock();   
		Connection con = getConnection();
		int statusId = -1;	
			try {
				String sql = "select  status_id from downloads where gid =?";
				PreparedStatement pst = con.prepareStatement(sql);
				pst.setString(1, gid);
				ResultSet rset = pst.executeQuery();
				while(rset.next()) {
					statusId = rset.getInt("status_id");
				}
	            rset.close();
	    } catch (SQLException e) {
	        e.printStackTrace();
	        log.error(e.getMessage());
	    } finally {
	    	r.unlock();
			try {
				con.close();
			} catch(SQLException e) {
				e.printStackTrace();
			}
		}
		return statusId;
	}

//	public String getDarStatus(String gid) {
//		r.lock();   
//		Connection con = getConnection();
//		String darStatus = "";	
//			try {
//				String sql = "select  dar_status from downloads where gid =?";
//				PreparedStatement pst = con.prepareStatement(sql);
//				pst.setString(1, gid);
//				ResultSet rset = pst.executeQuery();
//				while(rset.next()) {
//					darStatus = rset.getString("dar_status");
//				}
//	            rset.close();
//	    } catch (SQLException e) {
//	        e.printStackTrace();
//	        log.error(e.getMessage());
//	    } finally {
//	    	r.unlock();
//			try {
//				con.close();
//			} catch(SQLException e) {
//				e.printStackTrace();
//			}
//		}
//
//		return darStatus;
//	}

	public void updateDarStatus(String darURL, String darStatus) {
		w.lock();
		   Connection con = getConnection();
			
			try {
				PreparedStatement pst = con.prepareStatement("update downloads set dar_status = ? where filesource=?");
				pst.setString(1, darStatus);
				pst.setString(2, darURL);
				pst.executeUpdate();
	            pst.close();	           
	    } catch (SQLException e) {
	        e.printStackTrace();
	        log.error(e.getMessage());
	    } finally {
	    	w.unlock();
			try {
				con.close();
			} catch(SQLException e) {
				e.printStackTrace();
			}
		}
		
	}

	public boolean existsProcess(String processIdentifier) {
		r.lock();   
		Connection con = getConnection();
		boolean exists = false;	
			try {
				String sql = "select  gid from downloads where gid =?";
				PreparedStatement pst = con.prepareStatement(sql);
				pst.setString(1, processIdentifier);
				ResultSet rset = pst.executeQuery();
				while(rset.next()) {
					exists = true;
				}
	            rset.close();
	    } catch (SQLException e) {
	        e.printStackTrace();
	        log.error(e.getMessage());
	    } finally {
	    	r.unlock();
			try {
				con.close();
			} catch(SQLException e) {
				e.printStackTrace();
			}
		}

		return exists;
	}

	public Timestamp getFirstFailure(String processIdentifier) {
		r.lock();   
		Connection con = getConnection();
		Timestamp time = null;	
			try {
				String sql = "select  first_failure from downloads where gid = ?";
				PreparedStatement pst = con.prepareStatement(sql);
				pst.setString(1, processIdentifier);
				ResultSet rset = pst.executeQuery();
				while(rset.next()) {
					time = rset.getTimestamp("first_failure");
				}
	            rset.close();
	    } catch (SQLException e) {
	        e.printStackTrace();
	        log.error(e.getMessage());
	    } finally {
	    	r.unlock();
			try {
				con.close();
			} catch(SQLException e) {
				e.printStackTrace();
			}
		}
		
		return time;
	}

	public  int insertNewDownload(String identifier, String uri, String darURL, int wsId, int statusId, String darStatus, String realUri, String productDownloadDirectory) {
		w.lock();
		Connection con = getConnection();
		int maxPriority = 1;
		try {
			
			
			//if URL is MANUAL_DOWNLOAD checks if it exists and if not insert it into database
			if(darURL.equals("MANUAL_DOWNLOAD")) {
				PreparedStatement pst = con.prepareStatement("Select * from  monitoringurl where url = 'MANUAL_DOWNLOAD'");
				ResultSet rset = pst.executeQuery();
				if(!rset.next()) {
					pst = con.prepareStatement("insert into monitoringurl (URL, WS_ID,STATUS, start_time, name) values(?, ?, ?, ?, ?)");
					pst.clearParameters();
					pst.setString(1, "MANUAL_DOWNLOAD");
					pst.setInt(2, -1);
					pst.setString(3, "");
					pst.setTimestamp(4, new Timestamp(System.currentTimeMillis()));
					pst.setString(5, "MANUAL_DOWNLOAD");
					pst.executeUpdate();
				}
			}
			
			Statement stat= con.createStatement();
			ResultSet rset = stat.executeQuery("select max(priority) as maxPriority from downloads");
			while(rset.next()) {
				maxPriority = rset.getInt("maxPriority");
				if(maxPriority == 0) {
					maxPriority = 1;
				}
			}
			String sql = "Insert into downloads (filename, filesource, ws_id, gid, status_id, priority, dar_status, realuri, product_download_dir) values(?,?,?,?,?,?,?,?,?)";		    
		    PreparedStatement pst = con.prepareStatement(sql);
			pst.setString(1, uri);
			pst.setString(2,darURL.trim());
			pst.setInt(3,wsId);
			pst.setString(4,identifier);
			pst.setInt(5,statusId);
			pst.setInt(6,maxPriority);
			pst.setString(7,darStatus);
			pst.setString(8,realUri);
			pst.setString(9,productDownloadDirectory);
			return pst.executeUpdate();
		} catch(SQLException e) {
			e.printStackTrace();
			log.error(e.getMessage());
	    } finally {
	    	w.unlock();
			if (con != null)
				try {
					con.close();
		    	}  catch(SQLException sqlEx) {
		    		sqlEx.printStackTrace();
		    		log.error(connectionError);
		    	}
	    }
		return -1;
	  }
	
	public void insertNewDownload(String processIdentifier, String uri,
			String darURL, int wsId, int statusId, String darStatus, Timestamp timestamp,String productDownloadDirectory) {
		w.lock();
		Connection con = getConnection();
		int maxPriority = 1;
		try {
			Statement stat= con.createStatement();
			ResultSet rset = stat.executeQuery("select max(priority) as maxPriority from downloads");
			while(rset.next()) {
				maxPriority = rset.getInt("maxPriority");
				if(maxPriority == 0) {
					maxPriority = 1;
				}
			}
			String sql = "Insert into downloads (filename, filesource, ws_id, gid, status_id, priority, dar_status, first_failure, product_download_dir) values(?,?,?,?,?,?, ?, ?, ?)";
			PreparedStatement pst = con.prepareStatement(sql);
			  pst.setString(1, uri);
			  pst.setString(2,darURL.trim());
			  pst.setInt(3,wsId);
			  pst.setString(4,processIdentifier);
			  pst.setInt(5,statusId);
			  pst.setInt(6,maxPriority);
			  pst.setString(7,darStatus);
			  pst.setTimestamp(8, timestamp);
			  pst.setString(9,productDownloadDirectory);
			  pst.executeUpdate();
		} catch(SQLException e) {
			e.printStackTrace();
			log.error(e.getMessage());
	    } finally {
	    	w.unlock();
			if (con != null)
				try {
					con.close();
		    	}  catch(SQLException sqlEx) {
		    		sqlEx.printStackTrace();
		    		log.error(connectionError);
		    	}
	    }		
	}

	public boolean isRegistered(String currRegistrationURL, String downloadManagerId) {
		boolean isRegistered = false;
		r.lock();   
		Connection con = getConnection();
			try {
				String sql = "select registered from wsurl where registrationurl = ? and dm_id = ?";
				PreparedStatement pst = con.prepareStatement(sql);
				pst.setString(1, currRegistrationURL);
				pst.setString(2, downloadManagerId);
				ResultSet rset = pst.executeQuery();
				while(rset.next()) {
					isRegistered = rset.getBoolean("registered");
				}
				pst.close();
	            rset.close();
	    } catch (SQLException e) {
	        e.printStackTrace();
	        log.error(e.getMessage());
	    } finally {
	    	r.unlock();
			try {
				con.close();
			} catch(SQLException e) {
				e.printStackTrace();
			}
		}
		
		return isRegistered;
	}
	
	public void resetWSURLs() {
		w.lock();
		   Connection con = getConnection();
			try {
				PreparedStatement pst = con.prepareStatement("delete from WSURL");
				pst.executeUpdate();
	            pst.close();	           
	    } catch (SQLException e) {
	        e.printStackTrace();
	        log.error(e.getMessage());
	    } finally {
	    	w.unlock();
			try {
				con.close();
			} catch(SQLException e) {
				e.printStackTrace();
			}
		}
		
	}

	public void updateRefreshTime(String monitoringURL, long l, String downloadManagerId) {
		w.lock();
		   Connection con = getConnection();
			try {
				PreparedStatement pst = con.prepareStatement("update WSURL set refreshperiod = ? where registrationurl = ? and dm_id = ?");
				pst.setInt(1, (int) l);
				pst.setString(2, monitoringURL);
				pst.setString(3, downloadManagerId);
				pst.executeUpdate();
	            pst.close();	           
	    } catch (SQLException e) {
	        e.printStackTrace();
	        log.error(e.getMessage());
	    } finally {
	    	w.unlock();
			try {
				con.close();
			} catch(SQLException e) {
				e.printStackTrace();
			}
		}
		
	}

	public boolean getToContact(String currWs, String downloadManagerId) {
		boolean toBeContacted = true;
		r.lock();
		   Connection con = getConnection();
			try {
				PreparedStatement pst = con.prepareStatement("select lastcall, refreshperiod from WSURL where registrationurl = ? and dm_id = ?");
				pst.setString(1, currWs);
				pst.setString(2, downloadManagerId);
				ResultSet rset = pst.executeQuery();
				Timestamp lastCall = null;
				long refreshPeriod = 0L;
				while(rset.next()) {
					lastCall = rset.getTimestamp("lastcall");
				}
				if(lastCall == null) {
					lastCall = new Timestamp(System.currentTimeMillis());
				}
				if((System.currentTimeMillis()-lastCall.getTime())<refreshPeriod) {
					toBeContacted = false;
				}
	    } catch (SQLException e) {
	        e.printStackTrace();
	        log.error(e.getMessage());
	    } finally {
	    	r.unlock();
			try {
				con.close();
			} catch(SQLException e) {
				e.printStackTrace();
			}
		}
		return toBeContacted;
	}

	public String getRealUriById(String processIdentifier) {
		r.lock();
	    String realUri = null;
	    Connection con = getConnection();
	    try {
	    	PreparedStatement pst = con.prepareStatement("select realuri from downloads WHERE GID = ? ");
	    	pst.setString(1, processIdentifier);
			ResultSet rset = pst.executeQuery();
			while(rset.next()){
				realUri = rset.getString("realuri");
			}
			rset.close();
			  
	    } catch(SQLException e) {
	    	e.printStackTrace();
	    	log.error("Can not get realuri for gid " + processIdentifier);
	    } finally {
	    	r.unlock();
			if (con != null)
				try {
					con.close();
		    	}  catch(SQLException sqlEx) {
		    		sqlEx.printStackTrace();
		    		log.error(connectionError);
		    	}
	    }
	    return realUri;
	}

	public String getFileNameById(String processIdentifier) {
		r.lock();
	    String filename = null;
	    Connection con = getConnection();
	    try {
	    	PreparedStatement pst = con.prepareStatement("select filename from downloads WHERE GID = ? ");
	    	pst.setString(1, processIdentifier);
			ResultSet rset = pst.executeQuery();
			while(rset.next()){
				filename = rset.getString("filename");
			}
			rset.close();
			  
	    } catch(SQLException e) {
	    	e.printStackTrace();
	    	log.error("Can not get filename for gid " + processIdentifier);
	    } finally {
	    	r.unlock();
			if (con != null)
				try {
					con.close();
		    	}  catch(SQLException sqlEx) {
		    		sqlEx.printStackTrace();
		    		log.error(connectionError);
		    	}
	    }
	    return filename;
	}

	public void updateDownloadStatisctics(String id, String name, boolean handlePause) {
		w.lock();
		   Connection con = getConnection();
			
			try {
				PreparedStatement pst = con.prepareStatement("select last_start from downloads WHERE gid = ? ");
		    	pst.setString(1, id);
				ResultSet rset = pst.executeQuery();
				Timestamp lastStart = null;
				while(rset.next()) {
					lastStart = rset.getTimestamp("last_start");
				}
				if(lastStart != null) {
					long increment = System.currentTimeMillis() - lastStart.getTime();
					pst = con.prepareStatement("update downloads set last_start = ?, spent_time = spent_time + ?, plugin_name = ?, handle_pause=? where gid=?");
					pst.setTimestamp(1, new Timestamp(System.currentTimeMillis()));
					pst.setLong(2, increment);
					pst.setString(3, name);
					pst.setBoolean(4, handlePause);
					pst.setString(5, id);
					pst.executeUpdate();
				} else {
					pst = con.prepareStatement("update downloads set last_start = ?, spent_time = 0, plugin_name = ?, handle_pause=?  where gid=?");
					pst.setTimestamp(1, new Timestamp(System.currentTimeMillis()));
					pst.setString(2, name);
					pst.setBoolean(3, handlePause);
					pst.setString(4, id);
					pst.executeUpdate();
				}
	            pst.close();	           
	    } catch (SQLException e) {
	        e.printStackTrace();
	        log.error(e.getMessage());
	    } finally {
	    	w.unlock();
			try {
				con.close();
			} catch(SQLException e) {
				e.printStackTrace();
			}
		}
		
		
	}
	
	public boolean handlePause(String gid) {
		w.lock();
		   Connection con = getConnection();
			boolean handle_pause = false;
			try {
				PreparedStatement pst = con.prepareStatement("select handle_pause from downloads WHERE gid = ? ");
		    	pst.setString(1, gid);
				ResultSet rset = pst.executeQuery();
				while(rset.next()) {
					handle_pause = rset.getBoolean("handle_pause");
				}
	            pst.close();
	            rset.close();
	    } catch (SQLException e) {
	        e.printStackTrace();
	        log.error(e.getMessage());
	    } finally {
	    	w.unlock();
			try {
				con.close();
			} catch(SQLException e) {
				e.printStackTrace();
			}
		}
		
		return handle_pause;
		
	}

	public void updateDownloadStatisctics(String gid) {
		w.lock();
		   Connection con = getConnection();
			
			try {
				PreparedStatement pst = con.prepareStatement("select last_start from downloads WHERE gid = ? ");
		    	pst.setString(1, gid);
				ResultSet rset = pst.executeQuery();
				Timestamp lastStart = null;
				while(rset.next()) {
					lastStart = rset.getTimestamp("last_start");
				}
				if(lastStart != null) {
					long increment = System.currentTimeMillis() - lastStart.getTime();
					pst = con.prepareStatement("update downloads set last_start = ?, spent_time = spent_time + ? where gid=?");
					pst.setTimestamp(1, new Timestamp(System.currentTimeMillis()));
					pst.setLong(2, increment);
					pst.setString(3, gid);
					pst.executeUpdate();
				} else {
					pst = con.prepareStatement("update downloads set last_start = ?, spent_time = 0 where gid=?");
					pst.setTimestamp(1, new Timestamp(System.currentTimeMillis()));
					pst.setString(2, gid);
					pst.executeUpdate();
				}
	            pst.close();	           
	    } catch (SQLException e) {
	        e.printStackTrace();
	        log.error(e.getMessage());
	    } finally {
	    	w.unlock();
			try {
				con.close();
			} catch(SQLException e) {
				e.printStackTrace();
			}
		}
		
	}
	
	
	public void updateStartTime(String gid) {
		w.lock();
	   Connection con = getConnection();
		
		try {
			PreparedStatement pst = con.prepareStatement("update downloads set last_start = ? where gid=?");
			pst.setTimestamp(1, new Timestamp(System.currentTimeMillis()));
			pst.setString(2, gid);
			pst.executeUpdate();
            pst.close();	           
	    } catch (SQLException e) {
	        e.printStackTrace();
	        log.error(e.getMessage());
	    } finally {
	    	w.unlock();
			try {
				con.close();
			} catch(SQLException e) {
				e.printStackTrace();
			}
		}
		
	}

	public void updatePluginStatistics(String processIdentifier) {
		w.lock();
	   Connection con = getConnection();
		try {
			PreparedStatement pst = con.prepareStatement("select plugin_name, spent_time, filesize from downloads where gid=?");
			pst.setString(1, processIdentifier);
			ResultSet rset = pst.executeQuery();
			String pluginName = "";
			int speed = 0;
			while(rset.next()) {
				pluginName = rset.getString("plugin_name");
				BigDecimal fileSize = rset.getBigDecimal("filesize");
				if(rset.getInt("spent_time") != 0) {
					speed = (int) (fileSize.floatValue()/rset.getInt("spent_time"))*1000;
				}
			}
			pst = con.prepareStatement("select * from plugin_statistics where plugin_name=?");
			pst.setString(1, pluginName);
			rset = pst.executeQuery();
			if(rset.next()){
				pst = con.prepareStatement("update plugin_statistics set  speed = speed + ?, downloads_number = downloads_number + 1 where plugin_name=?");
				pst.setInt(1, speed);
				pst.setString(2, pluginName);
			} else {
				pst = con.prepareStatement("insert into plugin_statistics (plugin_name, speed, downloads_number) values (?,?, 1)");
				pst.setString(1, pluginName);
				pst.setInt(2, speed);
			}
			
			pst.executeUpdate();
            pst.close();	           
	    } catch (SQLException e) {
	        e.printStackTrace();
	        log.error(e.getMessage());
	    } finally {
	    	w.unlock();
			try {
				con.close();
			} catch(SQLException e) {
				e.printStackTrace();
			}
		}
			
	}
	
	public void updateHostStatistics(String processIdentifier) {
		w.lock();
	   Connection con = getConnection();
		try {
			PreparedStatement pst = con.prepareStatement("select realuri, spent_time, filesize from downloads where gid=?");
			pst.setString(1, processIdentifier);
			ResultSet rset = pst.executeQuery();
			String hostname = "";
			int speed = 0;
			while(rset.next()) {
				URI uri = new URI(rset.getString("realuri").trim().replace("{", "%7B").replace("}", "%7D").replace(":", "%3A"));
				hostname = uri.getHost();
				BigDecimal fileSize = rset.getBigDecimal("filesize");
				if(rset.getInt("spent_time") != 0) {
					speed = (int) (fileSize.floatValue()/rset.getInt("spent_time"))*1000;
				}
			}
			pst = con.prepareStatement("select * from host_statistics where host_name=?");
			pst.setString(1, hostname);
			rset = pst.executeQuery();
			if(rset.next()){
				pst = con.prepareStatement("update host_statistics set  speed = speed + ?, downloads_number = downloads_number + 1 where host_name=?");
				pst.setInt(1, speed);
				pst.setString(2, hostname);
			} else {
				pst = con.prepareStatement("insert into host_statistics (host_name, speed, downloads_number ) values (?,?, 1)");
				pst.setString(1, hostname);
				pst.setInt(2, speed);
			}
			
			pst.executeUpdate();
            pst.close();	           
	    } catch (Exception e) {
	        e.printStackTrace();
	        log.error(e.getMessage());
	    } finally {
	    	w.unlock();
			try {
				con.close();
			} catch(SQLException e) {
				e.printStackTrace();
			}
		}
			
	}

	public HashMap<String, Integer> getPluginStatistics() {
	   r.lock();
	   Connection con = getConnection();
	   HashMap<String, Integer> result = new HashMap<String, Integer>();
		try {
			PreparedStatement pst = con.prepareStatement("select plugin_name, speed, downloads_number from plugin_statistics");
			ResultSet rset = pst.executeQuery();
			while(rset.next()) {
				String pluginName = rset.getString("plugin_name");
				int downloadsNumber = rset.getInt("downloads_number");
				int speed = 0;
				if(downloadsNumber != 0) {
					speed = rset.getBigDecimal("speed").intValue() / downloadsNumber;
				}
				result.put(pluginName, speed);
			}
            pst.close();	           
	    } catch (SQLException e) {
	        e.printStackTrace();
	        log.error(e.getMessage());
	    } finally {
	    	r.unlock();
			try {
				con.close();
			} catch(SQLException e) {
				e.printStackTrace();
			}
		}
		
		return result;
	}

	public void resetStatistics() {
	   w.lock();
	   Connection con = getConnection();
		try {
			PreparedStatement pst = con.prepareStatement("update  plugin_statistics set speed = 0, downloads_number=0");
			pst.executeUpdate();
			pst = con.prepareStatement("update  host_statistics set speed = 0, downloads_number=0");
			pst.executeUpdate();
            pst.close();	           
	    } catch (SQLException e) {
	        e.printStackTrace();
	        log.error(e.getMessage());
	    } finally {
	    	w.unlock();
			try {
				con.close();
			} catch(SQLException e) {
				e.printStackTrace();
			}
		}
	}

	public BigInteger getBitrateByHost(String hostname) {
		r.lock();
	   Connection con = getConnection();
	   BigInteger result = null;
		try {
			PreparedStatement pst = con.prepareStatement("select speed, downloads_number from host_statistics where host_name=?");
			pst.setString(1, hostname);
			ResultSet rset = pst.executeQuery();
			while(rset.next()) {
				int downloadsNumber = rset.getInt("downloads_number");
				if(downloadsNumber != 0) {
					result = BigInteger.valueOf(rset.getInt("speed")/downloadsNumber);
				}
			}
            pst.close();	           
	    } catch (SQLException e) {
	        e.printStackTrace();
	        log.error(e.getMessage());
	    } finally {
	    	r.unlock();
			try {
				con.close();
			} catch(SQLException e) {
				e.printStackTrace();
			}
		}
		
		return result;
	}

	public String getProductDownloadDir(String gid) {
		r.lock();   
		Connection con = getConnection();
		String downloadDir = "";	
			try {
				String sql = "select product_download_dir from downloads where gid =?";
				PreparedStatement pst = con.prepareStatement(sql);
				pst.setString(1, gid);
				ResultSet rset = pst.executeQuery();
				while(rset.next()) {
					downloadDir = rset.getString("product_download_dir");
				}
	            rset.close();
	    } catch (SQLException e) {
	        e.printStackTrace();
	        log.error(e.getMessage());
	    } finally {
	    	r.unlock();
			try {
				con.close();
			} catch(SQLException e) {
				e.printStackTrace();
			}
		}

		return downloadDir;
	}

	public long getTimeByWSUrl(String currWs, String downloadManagerId) {
		r.lock();   
		Connection con = getConnection();
		long serverTime = 0l;	
		try {
			PreparedStatement pst = con.prepareStatement("select server_time from WSURL where registrationurl = ? and dm_id = ?");
			pst.setString(1,currWs);
			pst.setString(2,downloadManagerId);
			ResultSet rset = pst.executeQuery();
			while(rset.next()) {
				serverTime = rset.getLong("server_time");
			}
            pst.close();
            rset.close();
	    } catch (SQLException e) {
	        e.printStackTrace();
	        log.error(e.getMessage());
	    } finally {
	    	r.unlock();
			try {
				con.close();
			} catch(SQLException e) {
				e.printStackTrace();
			}
		}
		
		return serverTime;
	}

	public void updateMonitoringUrlTime(String darUrl, long serverTime, String downloadManagerId) {
		w.lock();   
		Connection con = getConnection();
		try {
			PreparedStatement pst = con.prepareStatement("update wsurl set server_time = ? where registrationurl = ? and dm_id = ?");
			pst.setLong(1,serverTime);
			pst.setString(2,darUrl);
			pst.setString(3,downloadManagerId);
			pst.executeUpdate();
            pst.close();
	    } catch (SQLException e) {
	        e.printStackTrace();
	        log.error(e.getMessage());
	    } finally {
	    	w.unlock();
			try {
				con.close();
			} catch(SQLException e) {
				e.printStackTrace();
			}
		}
		
	}

	public void resetWsUrlActive() {
		w.lock();   
		Connection con = getConnection();
		try {
			PreparedStatement pst = con.prepareStatement("update wsurl set active = false");
			pst.executeUpdate();
            pst.close();
	    } catch (SQLException e) {
	        e.printStackTrace();
	        log.error(e.getMessage());
	    } finally {
	    	w.unlock();
			try {
				con.close();
			} catch(SQLException e) {
				e.printStackTrace();
			}
		}
		
	}

	public String getMonitoringURLByProcessIdentifier(String processIdentifier) {
		r.lock();   
		Connection con = getConnection();
		String monitoringUrl = null;
		try {
			PreparedStatement pst = con.prepareStatement("select filesource from downloads where gid =?");
			pst.setString(1, processIdentifier);
			ResultSet rset = pst.executeQuery();
			while(rset.next()) {
				monitoringUrl = rset.getString("filesource");
			}
			
			pst.close();
	    } catch (SQLException e) {
	        e.printStackTrace();
	        log.error(e.getMessage());
	    } finally {
	    	r.unlock();
			try {
				con.close();
			} catch(SQLException e) {
				e.printStackTrace();
			}
		}
		
		return  monitoringUrl;
	}

	public String getFileSizeById(String processIdentifier) {
		r.lock();
	    String filesize = null;
	    Connection con = getConnection();
	    try {
	    	PreparedStatement pst = con.prepareStatement("select filesize from downloads WHERE GID = ? ");
	    	pst.setString(1, processIdentifier);
			ResultSet rset = pst.executeQuery();
			while(rset.next()){
				filesize = String.valueOf(rset.getInt("filesize")).trim();
			}
			rset.close();
			  
	    } catch(SQLException e) {
	    	e.printStackTrace();
	    	log.error("Can not get filename for gid " + processIdentifier);
	    } finally {
	    	r.unlock();
			if (con != null)
				try {
					con.close();
		    	}  catch(SQLException sqlEx) {
		    		sqlEx.printStackTrace();
		    		log.error(connectionError);
		    	}
	    }
	    return filesize;
	}

	public boolean isMonitoringURLRunning(String currMonitoringURL, int wsId) {
		boolean monitoringUrlExists = false;
		w.lock();
		Connection con = getConnection();
		try {
			//CHECK IF MONITORING URL ALREADY EXISTS
			PreparedStatement pst = con.prepareStatement("select id from  MONITORINGURL where URL = ? and  WS_ID = ?");
			pst.setString(1, currMonitoringURL);
			pst.setInt(2, wsId);
			ResultSet rset = pst.executeQuery();
			while(rset.next()) {
				monitoringUrlExists = true;
			}
			rset.close();
			pst.close();
		} catch (SQLException e) {
	         e.printStackTrace();
	         log.error(e.getMessage());
	     } finally {
	    	 w.unlock();
	    	try {
	    		if(con != null)
	   				con.close();
			} catch(SQLException e) {
				e.printStackTrace();
			}
     	}
		
		return monitoringUrlExists;
	}
	public void insertMonitoringURL(String currMonitoringURL, int wsId) {
		w.lock();
		Connection con = getConnection();
		try {
			PreparedStatement pst = con.prepareStatement("insert into MONITORINGURL (URL, WS_ID,STATUS, start_time) values(?, ?, ?, ?)");
			pst.clearParameters();
			pst.setString(1, currMonitoringURL);
			pst.setInt(2, wsId);
			pst.setString(3, "IN_PROGRESS");
			pst.setTimestamp(4, new Timestamp(System.currentTimeMillis()));
			pst.executeUpdate();
			pst.close();
		} catch (SQLException e) {
	         e.printStackTrace();
	         log.error(e.getMessage());
	     } finally {
	    	 w.unlock();
	    	try {
	    		if(con != null)
	   				con.close();
			} catch(SQLException e) {
				e.printStackTrace();
			}
     	}
		
	}
	
	public void updateMonitoringURLStatus(String currMonitoringURL, int wsId, String status) {
		w.lock();
		Connection con = getConnection();
		try {
			PreparedStatement pst = con.prepareStatement("update MONITORINGURL set STATUS = ? where URL = ? and WS_ID = ?");
			pst.setString(1, status);
			pst.setString(2, currMonitoringURL);
			pst.setInt(3, wsId);
			pst.executeUpdate();
			pst.close();
		} catch (SQLException e) {
	         e.printStackTrace();
	         log.error(e.getMessage());
	     } finally {
	    	 w.unlock();
	    	try {
	    		if(con != null)
	   				con.close();
			} catch(SQLException e) {
				e.printStackTrace();
			}
     	}
		
	}
	
	public void updateMonitoringURLName(String currMonitoringURL, int wsId, String name) {
		w.lock();
		Connection con = getConnection();
		try {
			PreparedStatement pst = con.prepareStatement("update MONITORINGURL set NAME = ? where URL = ? and WS_ID = ?");
			pst.setString(1, name);
			pst.setString(2, currMonitoringURL);
			pst.setInt(3, wsId);
			pst.executeUpdate();
			pst.close();
		} catch (SQLException e) {
	         e.printStackTrace();
	         log.error(e.getMessage());
	     } finally {
	    	 w.unlock();
	    	try {
	    		if(con != null)
	   				con.close();
			} catch(SQLException e) {
				e.printStackTrace();
			}
     	}
		
	}

	public Map<String, Integer> getMonitoringUrlToResume() {
		r.lock();
	    String sql = "select url, ws_id from monitoringurl WHERE STATUS = \'IN_PROGRESS\'";
	    Connection con = getConnection();
	    Map<String, Integer> result = new HashMap<String, Integer>();
	    try {
	    	Statement statement = con .createStatement();
			ResultSet rset = statement.executeQuery(sql);
			while(rset.next()){
				String url = rset.getString("url");
				int wsId = rset.getInt("ws_id");
				result.put(url, wsId);
			}
			rset.close();
			  
	    } catch(SQLException e) {
	    	e.printStackTrace();
	    	log.error("Can not get monitoring url to resume from db");
	    } finally {
	    	r.unlock();
			if (con != null)
				try {
					con.close();
		    	}  catch(SQLException sqlEx) {
		    		sqlEx.printStackTrace();
		    		log.error(connectionError);
		    	}
	    }
	    return result;
	}

	public void resetMonitoringUrls() {
		w.lock();
		   Connection con = getConnection();
			try {
				PreparedStatement pst = con.prepareStatement("delete from monitoringurl");
				pst.executeUpdate();
	            pst.close();	           
	    } catch (SQLException e) {
	        e.printStackTrace();
	        log.error(e.getMessage());
	    } finally {
	    	w.unlock();
			try {
				con.close();
			} catch(SQLException e) {
				e.printStackTrace();
			}
		}
		
	}

	public String getStopped(String gid) {
		r.lock();   
		Connection con = getConnection();
			
			try {
				PreparedStatement pst = con.prepareStatement("select stop from WSURL inner join downloads on ws_id = wsurl.id where gid = ? ");
				pst.setString(1,gid);
				ResultSet rset = pst.executeQuery();
				while(rset.next()) {
					return rset.getString("stop");
				}
	            pst.close();
	            rset.close();
	    } catch (SQLException e) {
	        e.printStackTrace();
	        log.error(e.getMessage());
	    } finally {
	    	r.unlock();
			try {
				con.close();
			} catch(SQLException e) {
				e.printStackTrace();
			}
		}
		
		return null;
		
	}
}
