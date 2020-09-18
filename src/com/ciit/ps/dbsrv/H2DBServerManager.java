package com.ciit.ps.dbsrv;

import com.ciit.ps.utils.ConfigurationLoader;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import org.apache.log4j.Logger;
import org.h2.tools.Server;

public class H2DBServerManager {

	private final static Logger	_log	= Logger.getLogger(H2DBServerManager.class);
	private static String driver_name = "";
	private static String db_url = "";
	private static String user_name = "sa";
	private static String password = null;
	
	private static Server dbServer = null;
	private static String dbServerPort = "9092";
	
	private static final String supervisors_infotbl_script = "CREATE TABLE IF NOT EXISTS supervisor_nodes(ID varchar(200) PRIMARY KEY, IP_Address varchar(50), Actor_Path varchar(1000), Actor_Name varchar(200))";
	
	static{
		try{
			H2DBServerManager.dbServerPort = ConfigurationLoader.getProperty("db_server_port");
			H2DBServerManager.driver_name = ConfigurationLoader.getProperty("db_driver_name");
			H2DBServerManager.db_url = ConfigurationLoader.getProperty("db_url");
			H2DBServerManager.user_name = ConfigurationLoader.getProperty("db_username");
			H2DBServerManager.password = ConfigurationLoader.getProperty("db_password");
			
		}
		catch(Exception ex)
		{
			_log.error(ex);
		}
	}
	
	private H2DBServerManager()
	{
		
	}
	
	public synchronized static void startDatabaseServer(String dbServerPort) throws Exception
	{
		if(dbServerPort!=null && !dbServerPort.isEmpty())
		{
			H2DBServerManager.dbServerPort = dbServerPort;
		}
		dbServer = Server.createTcpServer("-tcpPort", H2DBServerManager.dbServerPort, "-baseDir", ConfigurationLoader.getH2DBBaseDir());
		dbServer.start();
	}
	
	public static Connection getConnection() throws Exception
	{
		Class.forName(H2DBServerManager.driver_name);
        Connection conn = DriverManager.
            getConnection(H2DBServerManager.db_url, H2DBServerManager.user_name, H2DBServerManager.password);
        return conn;
	}
	
	protected void finalize() throws Throwable {
		stopDatabaseServer();
	}
	
	public static void stopDatabaseServer()
	{
		if(dbServer!=null)
		{
			dbServer.stop();
		}
	}
	
	public static void main(String[] args) throws Exception {
		H2DBServerManager.startDatabaseServer(null);
		try{
	        Connection conn = H2DBServerManager.getConnection();
	        // add application code here
	        Statement stmt = conn.createStatement();
	        stmt.executeUpdate(supervisors_infotbl_script); // create in-memory temporary table for holding peers data
//	        stmt.execute("insert into supervisor_nodes(ID, IP_Address, Actor_Path, Actor_Name) values('123','127.0.0.1','/test123','test123')");
	        ResultSet rs = stmt.executeQuery("SELECT * FROM SUPERVISOR_NODES");
	        while(rs.next())
	        {
	        	System.out.println(rs.getString(2));
	        }
	        rs.close();
	        stmt.close();
	        conn.close();
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
		}
		
		System.out.println(">>> Press ENTER to exit <<<");
	    System.in.read();
	    H2DBServerManager.stopDatabaseServer();
	}

}
