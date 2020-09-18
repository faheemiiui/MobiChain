package com.ciit.ps.wn;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;

import com.ciit.lp.entities.AssertedLocationProof;
import com.ciit.lp.entities.AssertionStatment;
import com.ciit.lp.entities.LocationAuthorityDetails;
import com.ciit.lp.entities.LocationProof;
import com.ciit.lp.entities.ProverDetails;
import com.ciit.lp.entities.WorkerNode;
import com.ciit.lp.messages.ALPACK;
import com.ciit.lp.messages.ApprovalMessage;
import com.ciit.lp.messages.ApprovalPendingRRSN;
import com.ciit.lp.messages.AssertionResponse;
import com.ciit.lp.messages.LAConnectAck;
import com.ciit.lp.messages.LPAssertionReq;
import com.ciit.lp.messages.LocationProofReq;
import com.ciit.lp.messages.ProofRequestMsg;
import com.ciit.ps.helper.HelperUtil;
import com.ciit.ps.utils.ConfigurationLoader;

import java.util.Date;
import java.util.HashMap;

import org.apache.log4j.Logger;

public class LocationAuthority extends AbstractActor {

	private final static Logger	_log	= Logger.getLogger(LocationAuthority.class);
	private static ActorRef supervisor = null;
	private static HashMap<String, ApprovalMessage> pendingRequests = new HashMap<String, ApprovalMessage>();
	private static HashMap<String,LPAssertionReq> pendingAssertions = new HashMap<String,LPAssertionReq>();
	private String uniqueIdentifier = null; 

	public static Props props(String uniqueIdentifier) {
		return Props.create(LocationAuthority.class,() -> new LocationAuthority(uniqueIdentifier));
	}

	public LocationAuthority(String uniqueIdentifier)
	{
		try{
			if(uniqueIdentifier!=null)
			{
				this.uniqueIdentifier =  uniqueIdentifier;
			}
			else
			{
				setUniqueIdentifier(ConfigurationLoader.getProperty("unique_identifier"));
			}
		}
		catch(Exception ex)
		{
			_log.error(ex);
		}
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(ALPACK.class, message -> {
					_log.info(uniqueIdentifier+" received Asserted Location Proof Acknowledgement from: "+getSender());
					Date lpReqTime = message.getAssertedLP().getLpAssertReq().getLp().getLpReqMsg().getReqTime();
					Date ackTime = new Date();
					_log.info("Location Proof generated in "+(ackTime.getTime()-lpReqTime.getTime())+" ms");
					String signature = message.getProverSignature();
					message.setProverSignature(null);
					HelperUtil helperUtil = new HelperUtil();
					String msgJson =helperUtil.getJson(message);
					LocationProof lp = message.getAssertedLP().getLpAssertReq().getLp();
					ApprovalMessage approvalMsg = lp.getLpReqMsg().getApprovalMsg();
					String rrsnAddress = approvalMsg.getRRSNAddress();
					if(helperUtil.verifySignature(lp.getProverId(), msgJson ,signature ))
					{
						message.setProverSignature(signature);
						msgJson =helperUtil.getJson(message);
						signature = helperUtil.sign(uniqueIdentifier, msgJson);
						message.setLocationAuthoritySignature(signature);
						getContext().system().actorSelection(rrsnAddress).tell(message, getSelf());
						_log.info("After Signing Asserted Location Proof Acknowledgement, sending it to:"+rrsnAddress);
					}
					else
						_log.error("Signature Mismatched for ALP Acknowledge Msg: "+msgJson);
				})
				.match(AssertionResponse.class,message->{
					AssertionStatment as = message.getAssertionStatment();
					_log.info("AssertionResponse received from: "+as.getWitnessId());
					String signature = message.getSignature();
					message.setSignature(null);
					HelperUtil helperUtil = new HelperUtil();
					String msgJson =helperUtil.getJson(message);
					if(helperUtil.verifySignature(as.getWitnessId(), msgJson ,signature ))
					{
						message.setSignature(signature);
						LPAssertionReq lpAssertReq = pendingAssertions.get(as.getHashLPAssertMsg());
						if(lpAssertReq!=null)
						{
							Date alpTime = new Date();
							AssertedLocationProof alp = new AssertedLocationProof(lpAssertReq, message, alpTime,uniqueIdentifier);
							msgJson =helperUtil.getJson(alp);
							signature = helperUtil.sign(uniqueIdentifier, msgJson);
							alp.setSignature(signature);
							ProverDetails prover = lpAssertReq.getLp().getLpReqMsg().getApprovalMsg().getPreq().getProver();
							String address = helperUtil.getAddress(prover.getIpAddress(), prover.getPort(), prover.getProverId());
							getContext().system().actorFor(address).tell(alp, getSender());
							_log.info("Asserted Location Proof sent to : "+prover.getProverId());
						}
						else
							_log.error("No Location Proof Request Identified against Assertion Response..");
					}
					else
						_log.error("Signature Mismatched for Approval Message:"+msgJson);
				})
				.match(LocationProofReq.class, message-> { // location proof request from prover
					ActorRef prover = getSender();
					ApprovalMessage approvalMsg = message.getApprovalMsg();
					ProofRequestMsg preq = approvalMsg.getPreq();
					// check if approvalMsg is in pending requests queue
					String signature = message.getSignature();
					message.setSignature(null);
					HelperUtil helperUtil = new HelperUtil();
					String msgJson =helperUtil.getJson(message);
					ProverDetails p = approvalMsg.getPreq().getProver();
					if(helperUtil.verifySignature(p.getProverId(), msgJson ,signature ))
					{
						String pendingReqKey = helperUtil.getHash(helperUtil.getJson(preq));
						ApprovalMessage validMsg = pendingRequests.get(pendingReqKey);
						if(validMsg!=null)
						{
							String validMsgHash = helperUtil.getHash(helperUtil.getJson(validMsg));
							String inRequestMsgHash = helperUtil.getHash(helperUtil.getJson(approvalMsg));
							if(validMsgHash.equals(inRequestMsgHash))
							{
								// do secure localization with prover
//								long latitude = -1l;
//								long longitude = -1l;
								LocationAuthorityDetails la = approvalMsg.getLocationAuthority();
								WorkerNode witness = approvalMsg.getWitness();
								Date lpTime = new Date();
								LocationProof lp = new LocationProof(p.getProverId(),la.getWorkerId(), message, lpTime);
								LPAssertionReq lpAssertReq = new LPAssertionReq(lp);
								msgJson =helperUtil.getJson(lpAssertReq);
								signature = helperUtil.sign(uniqueIdentifier, msgJson);
								lpAssertReq.setSignature(signature);
								pendingAssertions.put(helperUtil.getHash(msgJson), lpAssertReq);
								String address = helperUtil.getAddress(witness.getIpAddress(), witness.getPort(), witness.getWorkerId());
								getContext().system().actorFor(address).tell(lpAssertReq, getSelf());
								pendingRequests.remove(pendingReqKey);
							}
							else
							{
								prover.tell(new ApprovalPendingRRSN(message.getApprovalMsg().getPreq()) , getSelf());
							}
						}
						else
							_log.error("Fake Approval Message Received From :"+p.getProverId());
					}
					else
						_log.error("Signature Mismatched for Approval Message:"+msgJson);
					
				})
				.match(ApprovalMessage.class, message-> {
					ProofRequestMsg preq = message.getPreq();
					System.out.println("Approval Message received by:"+uniqueIdentifier+" from "+message.getRRSNID()+" for "+preq.getProver().getProverId());
					
					String signature = message.getSignature();
					message.setSignature(null);
					HelperUtil helperUtil = new HelperUtil();
					String msgJson =helperUtil.getJson(message);
					if(helperUtil.verifySignature(message.getRRSNID(), msgJson ,signature ))
					{
						message.setSignature(signature);
						String hash = helperUtil.getHash(helperUtil.getJson(preq));
						pendingRequests.put(hash, message);
					}
					else
						_log.error("Signature mismatched for Approval Message:"+msgJson);
				})
				.match(LAConnectAck.class, message-> {
					supervisor = getSender();
					_log.info("LA Connection Acknowledgement Received");
				})
				.match(String.class, reply->{
					System.out.println(reply);
				})
				.matchAny(msg->{
					System.out.println(uniqueIdentifier+" received msg:"+msg.getClass());
				})
				.build();
	}

	public String getUniqueIdentifier() {
		return uniqueIdentifier;
	}

	public void setUniqueIdentifier(String uniqueIdentifier) {
		this.uniqueIdentifier = uniqueIdentifier;
	}

}
