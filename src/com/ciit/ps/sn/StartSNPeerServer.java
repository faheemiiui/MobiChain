package com.ciit.ps.sn;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;

import com.ciit.lp.messages.SNConnectMsg;
import com.ciit.ps.dbsrv.H2DBServerManager;
import com.ciit.ps.helper.HelperUtil;
import com.ciit.ps.utils.ConfigurationLoader;
import com.typesafe.config.ConfigFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import org.apache.log4j.Logger;

public class StartSNPeerServer {
	private final static Logger	_log	= Logger.getLogger(StartSNPeerServer.class);
	private static ActorSystem system = null;
	private static ActorRef supervisor = null;
	
	private StartSNPeerServer() throws Exception
	{

	}

	public static void initialize(String applicationConfig,String dbServerPort) throws Exception
	{
		system = ActorSystem.create("MobiChain", ConfigFactory.load(applicationConfig));
//		H2DBServerManager.startDatabaseServer(dbServerPort);
		Props props = Supervisor.props();
		supervisor = system.actorOf(props,ConfigurationLoader.getProperty("unique_identifier"));
		System.out.println(system.actorSelection(supervisor.path()).toSerializationFormat());
		// if starting first time then connect to default sn specified in configuration otherwise read list from database
		// and connect to one
		ActorRef remoteSupervisorNode = null;
		String prefix = "supervisor_peer_";
		int i=1;
		String peerPath = ConfigurationLoader.getProperty(prefix+i);
		while(peerPath!=null)
		{
			try{
				_log.info("Attempt to connect to:"+peerPath);
				remoteSupervisorNode = system.actorFor(peerPath);
				if(!supervisor.path().equals(remoteSupervisorNode.path()))
				{
					HelperUtil helperUtil = new HelperUtil();
					SNConnectMsg msg = new SNConnectMsg(ConfigurationLoader.getProperty("unique_identifier"), "Provide me list of connected supervisor nodes");
					String signature = helperUtil.sign(ConfigurationLoader.getProperty("unique_identifier"),helperUtil.getJson(msg));
					msg.setSignature(signature);
					remoteSupervisorNode.tell(msg, supervisor);
					break;
				}
				
			}
			catch(Exception ex)
			{
				_log.error(ex);
				peerPath = null;
			}
			finally{
				i++;
				peerPath = ConfigurationLoader.getProperty(prefix+i);
				Thread.sleep(3000);
			}

		}


	}

	public static void shutdown()
	{
		if(system!=null)
		{
			system.terminate();
		}
		H2DBServerManager.stopDatabaseServer();
	}

	protected void finalize() throws Throwable {
		shutdown();
	}

	public static void main(String[] args) {
		BufferedReader br = null;
		try {
			if(args.length>=3)
			{
				ConfigurationLoader.loadProperties(args[2]);
			}
			StartSNPeerServer.initialize(args[0],args[1]);
			br = new BufferedReader(new InputStreamReader(System.in));

			while (true) {

				System.out.print("Type quit to exit...");
				String input = br.readLine();

				if (input.equalsIgnoreCase("quit")) {
					System.out.println("Exit!");
					break;
				}
				_log.info(input);
			}

		} catch (Exception ex) {
			_log.error(ex);
		} finally {
			if (br != null) {
				try {
					br.close();
				} catch (IOException e) {
					_log.error(e);
				}
			}
			StartSNPeerServer.shutdown();
			
		}
	}
}