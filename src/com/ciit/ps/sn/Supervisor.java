package com.ciit.ps.sn;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;

import com.ciit.lp.entities.DecisionBlock;
import com.ciit.lp.entities.LocationAuthorityDetails;
import com.ciit.lp.entities.ProverDetails;
import com.ciit.lp.entities.WitnessDetails;
import com.ciit.lp.entities.Worker;
import com.ciit.lp.entities.WorkerNode;
import com.ciit.lp.messages.ALPACK;
import com.ciit.lp.messages.ApprovalMessage;
import com.ciit.lp.messages.ChosenWorkersMsg;
import com.ciit.lp.messages.ConnectedWorkerMsg;
import com.ciit.lp.messages.IMessage;
import com.ciit.lp.messages.LAConnectAck;
import com.ciit.lp.messages.LAConnectMsg;
import com.ciit.lp.messages.NewLAEntryMsg;
import com.ciit.lp.messages.NewWitnessEntryMsg;
import com.ciit.lp.messages.NoLAColocatedMsg;
import com.ciit.lp.messages.NoWitnessColocatedMsg;
import com.ciit.lp.messages.ProofRequestMsg;
import com.ciit.lp.messages.SNConnectMsg;
import com.ciit.lp.messages.WNConnectAck;
import com.ciit.lp.messages.WNConnectMsg;
import com.ciit.lp.messages.WitnessSelectionReqMsg;
import com.ciit.ps.helper.HelperUtil;
import com.ciit.ps.helper.WorkerDistance;
import com.ciit.ps.utils.ConfigurationLoader;
import com.grum.geocalc.Coordinate;
import com.grum.geocalc.DegreeCoordinate;
import com.grum.geocalc.EarthCalc;
import com.grum.geocalc.Point;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.apache.log4j.Logger;


public class Supervisor extends AbstractActor {

	private final static Logger	_log	= Logger.getLogger(Supervisor.class);
	private static String uniqueIdentifier = null;

	private ConcurrentHashMap<String, ActorRef> peers = new ConcurrentHashMap<String, ActorRef>();
	private ConcurrentHashMap<String, Worker> workers = new ConcurrentHashMap<String, Worker>();
	private ConcurrentHashMap<String,Date> approvedProofReq = new ConcurrentHashMap<String,Date>();
	private ConcurrentHashMap<String,List<ChosenWorkersMsg>> proofReqQueue = new ConcurrentHashMap<String, List<ChosenWorkersMsg>>();
	private HashMap<String,ActorRef> provers = new HashMap<String,ActorRef>();
	
	public boolean isMsgPersistanceEnabled = false;
	
	private Supervisor() {

		try{
			uniqueIdentifier = ConfigurationLoader.getProperty("unique_identifier");
			peers.put(uniqueIdentifier, getSelf());
		}
		catch(Exception ex)
		{
			_log.error(ex);
		}
	}

	public static Props props() {
		return Props.create(Supervisor.class,() -> new Supervisor());
	}

	public void broadcastToSNPeers(IMessage msg,ActorRef sender) throws Exception
	{
		if(msg!=null)
		{
			Set<Entry<String,ActorRef>> entries = peers.entrySet();
			ActorRef peer = null;
			for(Entry<String,ActorRef> entry:entries)
			{
				peer = entry.getValue();
				if(!peer.path().equals(getSelf().path()))
				{
					if(sender!=null)
					{
						peer.tell(msg, sender);
					}
					else
					{
						peer.tell(msg, getSelf());
					}
				}
			}
		}
	}

	public List<WorkerDistance> chooseWorkers(double longitude, double latitude) // choose witness/la to assist prover in proof generation
	{
		WorkerDistance wd = null;
		Double distance = null;

		Coordinate lat = null;
		Coordinate lng = null;
		Point proverLocation = null;

		lat = new DegreeCoordinate(latitude);
		lng = new DegreeCoordinate(longitude);
		proverLocation = new Point(lat, lng);

		Point workerLocation = null;
		WorkerNode wn = null;
		List<WorkerDistance> list = new ArrayList<WorkerDistance>();

		Set<Entry<String, Worker>> entries = workers.entrySet();

		for(Entry<String, Worker> entry:entries)
		{
			wn = entry.getValue().getWorkerNode();
			lat = new DegreeCoordinate(wn.getLatitude());
			lng = new DegreeCoordinate(wn.getLongitude());
			workerLocation = new Point(lat, lng);

			distance = EarthCalc.getVincentyDistance(workerLocation, proverLocation); //in meters
			wd = new WorkerDistance(wn, distance);
			list.add(wd);
		}

		if(!list.isEmpty())
		{
			list.sort(Comparator.comparingDouble(WorkerDistance::getDistance));
		}
//		_log.info("Workers Distance from Prover:"+list);
		return list.subList(0, 2);
	}

	private LocationAuthorityDetails getColocatedLA(Map<WorkerNode,Long> countVotesForWorkerNodes)
	{
		Map<WorkerNode,Long> locationAuthorities = (Map<WorkerNode,Long>)countVotesForWorkerNodes.entrySet().stream() 
				.filter(map -> map.getKey() instanceof LocationAuthorityDetails) 
				.collect(Collectors.toMap(map -> map.getKey(), map -> map.getValue()));
		LocationAuthorityDetails la = (LocationAuthorityDetails)Collections.min(locationAuthorities.entrySet(), Comparator.comparingLong(Map.Entry::getValue)).getKey();
		return la;
	}

	private WitnessDetails getColocatedWitness(Map<WorkerNode,Long> countVotesForWorkerNodes)
	{
		Map<WorkerNode,Long> witnesses = (Map<WorkerNode,Long>)countVotesForWorkerNodes.entrySet().stream() 
				.filter(map -> map.getKey() instanceof WitnessDetails) 
				.collect(Collectors.toMap(map -> map.getKey(), map -> map.getValue()));
		WitnessDetails witness = (WitnessDetails)Collections.min(witnesses.entrySet(), Comparator.comparingLong(Map.Entry::getValue)).getKey();
		return witness;
	}

	@Override
	public Receive createReceive(){
		return receiveBuilder()
				.match(ALPACK.class, message -> {
					_log.info(uniqueIdentifier+" received Asserted Location Proof Acknowledgment from:"+getSender());
					// make message part of location provenance
				})
				.match(ChosenWorkersMsg.class, message-> {
					try {
						/* For distributed consensus, RRSN keeps on collecting 'ChosenWorkersMsg' from SN(s) 
						 *  and finally builds the decision block and reports the prover about the chosen witness
						 *  and location authority. 
						 */
//						_log.info("ChosenWorkerMsg received from:"+getSender());
						HelperUtil helperUtil = new HelperUtil();
						ProofRequestMsg preq = message.getPreq();
						ProverDetails prover = preq.getProver();
						String signature = message.getSignature();
						message.setSignature(null);
						String msgJson =helperUtil.getJson(message);
						if(helperUtil.verifySignature(message.getSupervisorId(), msgJson ,signature ))
						{
							message.setSignature(signature);
							signature = preq.getSignature();
							preq.setSignature(null);
							msgJson =helperUtil.getJson(preq);
							if(helperUtil.verifySignature(prover.getProverId(), msgJson ,signature ))
							{
								preq.setSignature(signature);
								
								Date timeElapsed = approvedProofReq.get(helperUtil.getJson(preq));//(helperUtil.getHash(helperUtil.getJson(preq)));
								if(timeElapsed!=null) // already processed
								{
//									long currenTime = System.currentTimeMillis();
//									long timepassed = currenTime - timeElapsed.getTime();
									// need to create a thread to clear the approved map
									return;
								}
								
								final List<ChosenWorkersMsg> confirmations;//(helperUtil.getHash(helperUtil.getJson(preq)));
								if(proofReqQueue.get(helperUtil.getJson(preq))==null)
								{
									confirmations = new ArrayList<ChosenWorkersMsg>();
								}
								else 
									confirmations = proofReqQueue.get(helperUtil.getJson(preq));
								confirmations.add(message);
								if(confirmations!=null)
								{
//									int consensus_threshold=ConfigurationLoader.getConsensusThreshold();
//									System.out.println("Confirmations:"+confirmations);
									// find the prover's co-located workers(la,witness)
									int consensusThreshold = ((peers.size()/2)+1);
									if(confirmations.size()>=consensusThreshold) // all confirmations received
									{
										Thread rrsnThread = new Thread(new Runnable() {
											
											@Override
											public void run() {
												try {
													_log.info("Decision taken with confirmations count:"+consensusThreshold);
													List<WorkerNode> chosenWorkers = new ArrayList<WorkerNode>();
													for(ChosenWorkersMsg cwm:confirmations)
													{
														chosenWorkers.add(cwm.getLocationAuthority());
														chosenWorkers.add(cwm.getWitness());
													}
													Map<WorkerNode,Long> countVotesForWorkerNodes = chosenWorkers.stream().collect(Collectors.groupingBy(i-> i,Collectors.counting()));
//													_log.info(countVotesForWorkerNodes);
													LocationAuthorityDetails locationAuthority = null;
													WitnessDetails witness = null;
													locationAuthority = getColocatedLA(countVotesForWorkerNodes);
													witness = getColocatedWitness(countVotesForWorkerNodes);
//													System.out.println("LA:"+locationAuthority);
//													System.out.println("Witness:"+witness);
													Date responseTime = new Date();
													// prepare approval message and decision block
													
													String rrsnAddress = getContext().system().actorSelection(getSelf().path()).toSerializationFormat();
													ApprovalMessage apm = new ApprovalMessage(locationAuthority, witness, responseTime, preq,uniqueIdentifier,rrsnAddress);
													String decisionBlockId = null;
													DecisionBlock db = new DecisionBlock(apm, uniqueIdentifier,confirmations);
													String dbData = helperUtil.getJson(db);
													decisionBlockId = helperUtil.getHash(dbData);
													
													if(isMsgPersistanceEnabled)
													{
														helperUtil.saveObject(db, helperUtil.getMsgPersistancePath()+"/"+decisionBlockId+"_db.msg");
													}
													apm.setDecisionBlockId(decisionBlockId);
													String signature = helperUtil.sign(uniqueIdentifier,helperUtil.getJson(apm));
													apm.setSignature(signature);
													_log.info("Sending Approval Message to Location Authority:"+locationAuthority.getWorkerId());
													workers.get(locationAuthority.getWorkerId()).getActor().tell(apm, getSelf());
													_log.info("Sending Approval Message to Witness:"+witness.getWorkerId());
													workers.get(witness.getWorkerId()).getActor().tell(apm, getSelf());
													_log.info("Sending Approval Message to Prover:"+prover.getProverId());
													provers.get(prover.getProverId()).tell(apm, getSelf());
													String preqHash = (helperUtil.getJson(preq));//helperUtil.getHash(helperUtil.getJson(preq));
													proofReqQueue.remove(preqHash);
													approvedProofReq.put(preqHash, new Date());
//													locationAuthority.getWorkerNodeRef().tell(apm, getSelf());
//													witness.getWorkerNodeRef()witness
//													prover.getProver().tell(apm, getSelf());
												}
												catch(Exception ex)
												{
													_log.error(ex);
												}
												
											}
										});
										rrsnThread.start();
										
									}
									else
									{
										proofReqQueue.put((helperUtil.getJson(preq)),confirmations);
									}
								}
							}
							else
							_log.error("Signature mismatched for Proof Request Message:"+msgJson);
						}
						else
							_log.error("Signature mismatched for Chosen Workers Message:"+msgJson);
						
					} catch (Exception e) {
						e.printStackTrace();
						_log.error(e);
					}
				})
				.match(WitnessSelectionReqMsg.class, message-> { // For distributed consensus WitnessSelectionReqMsg is sent by RRSN
					try {
						final ActorRef rrsn = getSender();
						ProofRequestMsg preq = message.getProofReqMsg();
						ProverDetails prover = preq.getProver();
						_log.info("Witness selection request for Prover at location(Longitude:"+prover.getLongitude()+",Latitude:"+prover.getLatitude()+") with ID:"+prover.getProverId()+" by peer:"+rrsn);
						String signature = message.getSignature();
						message.setSignature(null);
						HelperUtil helperUtil= new HelperUtil();
						String msgJson =helperUtil.getJson(message);
						if(helperUtil.verifySignature(message.getRRSNId(), msgJson ,signature ))
						{
							message.setSignature(signature);
							signature = preq.getSignature();
							preq.setSignature(null);
							msgJson =helperUtil.getJson(preq);
							if(helperUtil.verifySignature(prover.getProverId(), msgJson ,signature ))
							{
								final String signature1 = signature;
								Thread workerThread = new Thread(new Runnable() {
									
									@Override
									public void run() {
										try {
											preq.setSignature(signature1);
											List<WorkerDistance> workers = chooseWorkers(prover.getLongitude(), prover.getLatitude());
											Date responseTime = new Date();
											
											ChosenWorkersMsg cwm = new ChosenWorkersMsg(preq,responseTime);
											for(WorkerDistance wd:workers)
											{
												if(wd.getWorker() instanceof LocationAuthorityDetails)
												{
													cwm.setLocationAuthority((LocationAuthorityDetails)wd.getWorker());
												}
												else if(wd.getWorker() instanceof WitnessDetails)
												{
													cwm.setWitness((WitnessDetails)wd.getWorker());
												}
											}
											cwm.setSupervisorId(uniqueIdentifier);
											if(cwm.getLocationAuthority()==null)
											{
												rrsn.tell(new NoLAColocatedMsg(preq), getSelf());
												
											}
											else if(cwm.getWitness()==null)
											{
												rrsn.tell(new NoWitnessColocatedMsg(preq), getSelf());
											}
											else{
												String json = helperUtil.getJson(cwm);
//												_log.debug("ChosenWorkerMsg:\n"+json);
												String signature = helperUtil.sign(uniqueIdentifier,json); // sign the message using private key of supervisor
												cwm.setSignature(signature);
//												_log.info(getSelf()+" -> sending ChosenWorkerMsg to:"+rrsn);
												rrsn.tell(cwm, getSelf());	
											}
											
											
										}
										catch(Exception ex)
										{
											_log.info(ex);
										}
										
									}
								});
								workerThread.start();
							}
							else
								_log.error("Signature mismatched for Proof Request:"+msgJson);
							
						}
						else
							_log.error("Signature mismatched for Witness Selection Request:"+msgJson);
						
					} catch (Exception e) {
						e.printStackTrace();
						_log.error(e);
					}
				})
				.match(ProofRequestMsg.class, message-> { // SN recieving Proof Request is named RRSN
					try {
						HelperUtil helperUtil=new HelperUtil();
						ProverDetails prover = message.getProver();
						String signature = message.getSignature();
						message.setSignature(null);
						String msgJson =helperUtil.getJson(message);
						if(helperUtil.verifySignature(prover.getProverId(), msgJson ,signature ))
						{
							message.setSignature(signature);
							_log.info("Prover request from location(Longitude:"+prover.getLongitude()+",Latitude:"+prover.getLatitude()+") with ID:"+prover.getProverId());
							// broadcast to sn peers
							Date witnessSelectionReqTime = new Date();
							provers.put(prover.getProverId(), getSender());
							WitnessSelectionReqMsg wsmsg = new WitnessSelectionReqMsg(message, witnessSelectionReqTime);
							wsmsg.setRRSNId(uniqueIdentifier);
							msgJson = helperUtil.getJson(wsmsg);
							signature = helperUtil.sign(uniqueIdentifier,msgJson); 
							wsmsg.setSignature(signature);
//							_log.info(msgJson);
							getSelf().tell(wsmsg, getSelf());
							broadcastToSNPeers(wsmsg, getSelf());
							
						}
						else
							_log.error("Signature mismatched for Proof Request:"+msgJson);
					} catch (Exception e) {
						_log.error(e);
					}
				})
				.match(NewWitnessEntryMsg.class, message-> {
					try {
						HelperUtil helperUtil = new HelperUtil();
						final ActorRef sender = getSender();
						WitnessDetails wn = message.getWitness();
						String signature = message.getSignature();
						message.setSignature(null);
						_log.info("New Witness ("+wn.getWorkerId()+") connected from location(Longitude:"+wn.getLongitude()+",Latitude:"+wn.getLatitude()+")");
						String msgJson = helperUtil.getJson(message);
						if(helperUtil.verifySignature(message.getRRSNId(), msgJson ,signature ))
						{
							workers.put(wn.getWorkerId(), new Worker(wn, sender));
						}
						else
							_log.error("Signature mismatched for New Witness Connect Request:"+msgJson);
						
					} catch (Exception e) {
						_log.error(e);
					}
				})
				.match(WNConnectMsg.class, message-> {
					try {
						final ActorRef sender = getSender();
						WitnessDetails wn = message.getWitness();
						String signature = message.getSignature();
						message.setSignature(null);
						HelperUtil helperUtil =new HelperUtil();
						String msgJson = helperUtil.getJson(message);
						_log.info("New Witness  ("+wn.getWorkerId()+") connected "+sender+" from location(Longitude:"+wn.getLongitude()+",Latitude:"+wn.getLatitude()+")");
						if(helperUtil.verifySignature(wn.getWorkerId(), msgJson ,signature ))
						{
							workers.put(wn.getWorkerId(), new Worker(wn, sender));
							sender.tell(new WNConnectAck(), getSelf());
							NewWitnessEntryMsg msgEntry = new NewWitnessEntryMsg(wn);
							msgEntry.setRRSNId(uniqueIdentifier);
							msgJson = helperUtil.getJson(msgEntry);
							signature = helperUtil.sign(uniqueIdentifier,msgJson);
							msgEntry.setSignature(signature);
							broadcastToSNPeers(msgEntry,sender);
						}
						else
							_log.error("Signature mismatched for Witness Connect Request:"+msgJson);
					} catch (Exception e) {
						_log.error(e);
					}
				})
				.match(NewLAEntryMsg.class, message-> {
					try {
						final ActorRef sender = getSender();
						LocationAuthorityDetails la = message.getLocationAuthority();
						String signature = message.getSignature();
						message.setSignature(null);
						HelperUtil helperUtil = new HelperUtil();
						_log.info("New LA ("+la.getWorkerId()+")connected from location(Longitude:"+la.getLongitude()+",Latitude:"+la.getLatitude()+")");
						String msgJson = helperUtil.getJson(message);
						if(helperUtil.verifySignature(message.getRRSNId(), msgJson ,signature ))
						{
							workers.put(la.getWorkerId(),new Worker(la, sender));
						}
						else
							_log.error("Signature mismatched for New LA Connect Request:"+msgJson);
					} catch (Exception e) {
						_log.error(e);
					}
				})
				.match(LAConnectMsg.class, message-> {
					try {
						final ActorRef sender = getSender();
						String signature = message.getSignature();
						message.setSignature(null);
						LocationAuthorityDetails la = message.getLocationAuthority();
						HelperUtil helperUtil = new HelperUtil();
						String msgJson = helperUtil.getJson(message);
						if(helperUtil.verifySignature(la.getWorkerId(), msgJson ,signature ))
						{
							_log.info("New LA ("+la.getWorkerId()+") connected "+sender+" from location(Longitude:"+la.getLongitude()+",Latitude:"+la.getLatitude()+")");
//							message.setSignature(signature);
							workers.put(la.getWorkerId(), new Worker(la, sender));
							sender.tell(new LAConnectAck(), getSelf());
							NewLAEntryMsg msg = new NewLAEntryMsg(la);
							msg.setRRSNId(uniqueIdentifier);
							msgJson = helperUtil.getJson(msg);
							signature = helperUtil.sign(uniqueIdentifier,msgJson);
							msg.setSignature(signature);
							broadcastToSNPeers(msg,sender);
						}
						else
							_log.error("Signature mismatched for LA Connect Request:"+msgJson);
						
					} catch (Exception e) {
						_log.error(e);
					}
				})
				.match(ConnectedWorkerMsg.class, message-> { // existings workers list provided by SN to whom connection established
					try {
						// sender contains actually the ActorRef of
						String signature = message.getSignature();
						message.setSignature(null);
						WorkerNode worker = message.getWorkerNode();
						HelperUtil helperUtil = new HelperUtil();
						String msgJson =  helperUtil.getJson(message);
						if(helperUtil.verifySignature(worker.getWorkerId(),msgJson ,signature ))
						{
							workers.put(worker.getWorkerId(),new Worker(worker, getSender()));
						}
						else
							_log.error("Signature mismatched for Connected Worker Message:"+msgJson);
//						_log.info("Workers list received from :"+getSender());
					} catch (Exception e) {
						_log.error(e);
					}
				})
				.match(SNConnectMsg.class, message-> { // new SN connects so send it the online workers list
					try {
						final ActorRef sender = getSender();
						_log.info("New supervisor node connected..."+message.getSupervisorNodeId());
						String signature = message.getSignature();
						message.setSignature(null);
						HelperUtil helperUtil = new HelperUtil();
						if(helperUtil.verifySignature(message.getSupervisorNodeId(), helperUtil.getJson(message) ,signature ))
						{
							peers.put(message.getSupervisorNodeId(), sender);
							Set<Entry<String,Worker>> entries = workers.entrySet();
							WorkerNode wn = null;
							for(Entry<String,Worker> worker:entries)
							{	
								wn = worker.getValue().getWorkerNode();
								ConnectedWorkerMsg msg = new ConnectedWorkerMsg(wn);
								signature = helperUtil.sign(wn.getWorkerId(),helperUtil.getJson(msg));
								msg.setSignature(signature);
								sender.tell(msg,worker.getValue().getActor());
							}
						}
						
					} catch (Exception e) {
						_log.error(e);
					}
				})
				.match(NoLAColocatedMsg.class, message->{
					final ActorRef sender = getSender();
					ProofRequestMsg preq = message.getProofReqMsg();
					ProverDetails prover = preq.getProver();
					_log.info("No location authority available to fullfil Prover request from location(Longitude:"+prover.getLongitude()+",Latitude:"+prover.getLatitude()+") with ID:"+prover.getProverId()+" - by "+sender);
				})
				.match(NoWitnessColocatedMsg.class, message->{
					final ActorRef sender = getSender();
					ProofRequestMsg preq = message.getProofReqMsg();
					ProverDetails prover = preq.getProver();
					_log.info("No witness available to fullfil Prover request from location(Longitude:"+prover.getLongitude()+",Latitude:"+prover.getLatitude()+") with ID:"+prover.getProverId()+" - by "+sender);
				})
				.match(Object.class, reply->{
					System.out.println(reply);
				})
				.build();
	}

}
