package com.ciit.ps.wn;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;

import com.ciit.lp.entities.LocationAuthorityDetails;
import com.ciit.lp.entities.ProverDetails;
import com.ciit.lp.entities.WitnessDetails;
import com.ciit.lp.messages.LAConnectMsg;
import com.ciit.lp.messages.ProofRequestMsg;
import com.ciit.lp.messages.WNConnectMsg;
import com.ciit.ps.helper.HelperUtil;
import com.typesafe.config.ConfigFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.util.Date;
import java.util.HashMap;

import org.apache.log4j.Logger;

public class WorkerNodeTestClient {

	private final static Logger	_log	= Logger.getLogger(WorkerNodeTestClient.class);
	private static ActorSystem actorSystem = null;
	private static ActorRef rrsn = null;
	private static HashMap<String, ActorRef> provers = new HashMap<String, ActorRef>();
	static {
		try{
			actorSystem = ActorSystem.apply("MobiChain", ConfigFactory.load("application_wn.conf"));
			 rrsn = getSuperVisor();//actorSystem.actorFor(path);
		}
		catch(Exception ex)
		{
			_log.error(ex);
		}
	}
	public static ActorRef getSuperVisor() throws Exception
	{
		ActorRef remoteSupervisorNode = null;
		String peerPath = "akka.tcp://MobiChain@127.0.0.1:2552/user/CIIT_LPS_SN1";
		try{
			_log.info("Attempt to connect to:"+peerPath);
			remoteSupervisorNode = actorSystem.actorFor(peerPath);
		}
		catch(Exception ex)
		{
			_log.error(ex);
		}
		return remoteSupervisorNode;
	}
	
	public static void generateProofRequest(int concurrentRequest) throws Exception
	{
		long startTime = System.currentTimeMillis();
		if(concurrentRequest==0)
			concurrentRequest=1;
		String proverUniqueId = "CIIT_LPS_PV1";
		HelperUtil helperUtil = new HelperUtil();
		String signature = null;
		Date reqTime = null;
		for(int i=0;i<concurrentRequest;i++)
		{
			String uniqueID = proverUniqueId+i;
			ActorRef prover = provers.get(uniqueID);
			if(prover==null)
			{
				prover = actorSystem.actorOf(Prover.props(uniqueID),uniqueID);
				provers.put(uniqueID, prover);
			}
			reqTime = new Date();
			ProverDetails proverDetails = new ProverDetails(InetAddress.getLoopbackAddress().getHostAddress().toString(), 73.034793+i, 33.685653+i, 3555, reqTime, uniqueID,prover.toString());
			ProofRequestMsg preqmsg = new ProofRequestMsg(proverDetails);
			signature = helperUtil.sign(proverDetails.getProverId(),helperUtil.getJson(preqmsg));
			preqmsg.setSignature(signature);
			rrsn.tell(preqmsg,prover);
//			if(i+1<concurrentRequest) // don't wait on last request
//			{
//				Thread.sleep(1000/concurrentRequest);
//			}
			
		}
		
		long endTime = System.currentTimeMillis();
		_log.info("Requests ("+concurrentRequest+") generated in "+(endTime-startTime)+" ms");
		
	}
	
	public static void remoteCall()
	 {
		 BufferedReader br = null;
			try{
				
//				final String path = "akka.tcp://helloakka@127.0.0.1:2552/user/howdyGreeter";
				
				String laUniqueId="CIIT_LPS_LA1";
				String witnessUniqueId = "CIIT_LPS_WIT1";
//				String proverUniqueId = "CIIT_LPS_PV1";
				
//				ActorRef witness = actorSystem.actorOf(Witness.props(witnessUniqueId));
				
				int MAX_COUNT=800;
				//latitude:33.6007,longitude: 73.0679
				WitnessDetails witnessDetails = null;
				Date reqTime = null;
				String signature = null;
				HelperUtil helperUtil = new HelperUtil();
				for(int i=1;i<=MAX_COUNT;i++)
				{
					// 70.034793, 30.685653 
					reqTime = new Date();
					String uniqueId=witnessUniqueId+i;
					ActorRef witness = actorSystem.actorOf(Witness.props(uniqueId),uniqueId);
					witnessDetails = new WitnessDetails(InetAddress.getLoopbackAddress().getHostAddress().toString(),73.034793+i, 33.685653+i , 3555, reqTime, uniqueId,witness.toString());
					WNConnectMsg wnmsg = new WNConnectMsg(witnessDetails);
					String msgJson = helperUtil.getJson(wnmsg);
					signature = helperUtil.sign(witnessDetails.getWorkerId(),msgJson);
					wnmsg.setSignature(signature);
//					System.out.println(HelperUtil.getJson(wnmsg));
					rrsn.tell(wnmsg,witness);
				}
				
//				ActorRef la = actorSystem.actorOf(LocationAuthority.props(laUniqueId));
				//72.034793, 32.685653
				LocationAuthorityDetails laDetails = null;//new LocationAuthorityDetails(InetAddress.getLocalHost().toString(), 73.034793, 33.685653 , 3555, reqTime, laUniqueId);
				for(int i=1;i<MAX_COUNT;i++)
				{
					String uniqueId=laUniqueId+i;
					ActorRef la = actorSystem.actorOf(LocationAuthority.props(uniqueId),uniqueId);
					reqTime = new Date();
					laDetails = new LocationAuthorityDetails(InetAddress.getLoopbackAddress().getHostAddress().toString(), 73.034793+i, 33.685653+i , 3555, reqTime, uniqueId,la.toString());
					LAConnectMsg lamsg = new LAConnectMsg(laDetails);
					signature = helperUtil.sign(laDetails.getWorkerId(),helperUtil.getJson(lamsg));
					lamsg.setSignature(signature);
					rrsn.tell(lamsg, la);
//					System.out.println(la.toString());
				}
				
						
				br = new BufferedReader(new InputStreamReader(System.in));
				while (true) {

					System.out.println("Type quit to exit...");
					String input = br.readLine();
					if (input.equalsIgnoreCase("quit")) {
						System.out.println("Exit!");
						break;
					}
					else if(input.equalsIgnoreCase("Proof"))
					{
						generateProofRequest(1);
					}
					else if(input.startsWith("Proof-"))
					{
						String cr = input.substring(input.indexOf("-")+1);
						generateProofRequest(Integer.parseInt(cr));
					}

					System.out.println(input);
				}
			}
			catch(Exception ex)
			{
				_log.error(ex);
			}
			finally {
				if (br != null) {
					try {
						br.close();
					} catch (IOException e) {
						_log.error(e);
					}
				}
				actorSystem.terminate();
			}
	 }
	public static void main(String[] args) {
		
		WorkerNodeTestClient.remoteCall();
	}

}
