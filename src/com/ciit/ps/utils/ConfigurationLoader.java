package com.ciit.ps.utils;

import java.io.FileInputStream;
import java.util.Properties;

import org.apache.log4j.Logger;

public class ConfigurationLoader {
	
	private static final String CONFIG_PATH = "conf.properties";
	private final static Logger	_log	= Logger.getLogger(ConfigurationLoader.class);
	private static Properties properties = null;
	
	public static void loadProperties()
	{
		try {
			properties = new Properties();
			ConfigurationLoader.properties.load(ConfigurationLoader.class.getClassLoader().getResourceAsStream(CONFIG_PATH));
			_log.info("Configurations loaded successfully....");
		} catch (Exception ex) {
			properties = null;
			_log.error("Error = " + ex.getMessage(), ex);
		}
	}
	
	public static void loadProperties(String configPath)
	{
		try {
			properties = new Properties();
			properties.load(new FileInputStream(configPath));
			_log.info("Configurations loaded successfully from Path("+configPath+")");
		} catch (Exception ex) {
			properties = null;
			_log.error("Error = " + ex.getMessage(), ex);
		}
	}

	public static String getProperty(String property) {
		if(properties==null)
		{
			loadProperties();
		}
		return properties.getProperty(property);
	}
	
	public static String getProperty(String property,String defaultValue)
	{
		if(properties==null)
		{
			loadProperties();
		}
		return properties.getProperty(property, defaultValue);
	}
	
	public static int getConsensusThreshold()
	{
		String threshold = getProperty("consensus_threshold","2");
		try{
			return Integer.parseInt(threshold);
		}
		catch(NumberFormatException ex)
		{
			_log.error(ex);
		}
		return 2;
	}
	
	public static String getH2DBBaseDir()
	{
		return getProperty("h2db_base_dir");
	}
	
	public static void main(String[] args){
		System.out.println(ConfigurationLoader.getProperty("supervisor_peer_*"));
	}
}
