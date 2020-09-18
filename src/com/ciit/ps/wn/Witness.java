package com.ciit.ps.wn;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;

import com.ciit.lp.entities.AssertionStatment;
import com.ciit.lp.entities.LocationProof;
import com.ciit.lp.entities.WitnessDetails;
import com.ciit.lp.messages.ALPVerificationReqMsg;
import com.ciit.lp.messages.ALPVerificationResMsg;
import com.ciit.lp.messages.ApprovalMessage;
import com.ciit.lp.messages.AssertionResponse;
import com.ciit.lp.messages.LPAssertionReq;
import com.ciit.lp.messages.ProofRequestMsg;
import com.ciit.lp.messages.WNConnectAck;
import com.ciit.ps.helper.HelperUtil;
import com.ciit.ps.utils.ConfigurationLoader;

import java.util.Date;
import java.util.HashMap;

import org.apache.log4j.Logger;

public class Witness extends AbstractActor {

	private final static Logger	_log	= Logger.getLogger(Witness.class);
	private static ActorRef supervisor = null;
	private static HashMap<String, ApprovalMessage> pendingRequests = new HashMap<String, ApprovalMessage>();
	private static HashMap<String,AssertionResponse> pendingAssertionResponseVerifications = new HashMap<String,AssertionResponse>();
	private String uniqueIdentifier = null; 

	public static Props props(String uniqueIdentifier) {
		return Props.create(Witness.class,() -> new Witness(uniqueIdentifier));
	}

	public Witness(String uniqueIdentifier) {

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
				.match(ALPVerificationReqMsg.class,message->{
					_log.info(uniqueIdentifier+" recevied Asserted Location Proof Verification Request");
					HelperUtil helperUtil = new HelperUtil();
					String signature = message.getSignature();
					message.setSignature(null);
					String msgJson = helperUtil.getJson(message);
					String proverId = message.getAssertedLP().getLpAssertReq().getLp().getProverId();
					if(helperUtil.verifySignature(proverId, msgJson ,signature ))
					{
						message.setSignature(signature);
						AssertionResponse ar = message.getAssertedLP().getAssertionResponse();
						msgJson = helperUtil.getJson(ar);
						String alpVerificationReq = helperUtil.getHash(msgJson);
						AssertionResponse validAR = pendingAssertionResponseVerifications.get(alpVerificationReq);
						Date alpVerifyResTime = new Date();
						ALPVerificationResMsg resp = new ALPVerificationResMsg(alpVerificationReq, alpVerifyResTime, uniqueIdentifier);
						if(validAR!=null)
						{
							pendingAssertionResponseVerifications.remove(alpVerificationReq);
							String arHash = helperUtil.getHash(helperUtil.getJson(validAR));
							if(arHash.equals(alpVerificationReq))
							{
								resp.setValid(true);
							}
							else
							{
								resp.setValid(false);
								_log.error("Assertion Response is tampered...");
							}
						}
						else
						{
							resp.setValid(false);
							_log.error("Location Proof is not asserted by: "+uniqueIdentifier);
						}
						
						msgJson = helperUtil.getJson(resp);
						signature = helperUtil.sign(uniqueIdentifier, msgJson);
						resp.setSignature(signature);
						getSender().tell(resp,getSelf());
						_log.info("Sending verification response to :"+getSender());

					}
					else
						_log.error("Signature mismatched for Asserted Location Proof Verification:"+msgJson);
				})
				.match(LPAssertionReq.class, message-> { 
					LocationProof lp = message.getLp();
					String locationAuthorityId = lp.getLocationAuthorityId();
					
					ApprovalMessage approvalMsg = lp.getLpReqMsg().getApprovalMsg(); 
					HelperUtil helperUtil = new HelperUtil();
					String pendingApprovalMessageKey = helperUtil.getHash(helperUtil.getJson(approvalMsg));
					ApprovalMessage validMsg = pendingRequests.get(pendingApprovalMessageKey);
					if(validMsg!=null)
					{
						String validMsgHash = helperUtil.getHash(helperUtil.getJson(validMsg));
						String inRequestMsgHash = helperUtil.getHash(helperUtil.getJson(approvalMsg));
						if(validMsgHash.equals(inRequestMsgHash))
						{
							String decisionBlockId = approvalMsg.getDecisionBlockId();
							String proverId = lp.getProverId();
							String signature = message.getSignature();
							message.setSignature(null);
							String msgJson =helperUtil.getJson(message);
							WitnessDetails witness = lp.getLpReqMsg().getApprovalMsg().getWitness();
							_log.info(uniqueIdentifier+" -> LocationProof Assertion Request received from: "+lp.getLocationAuthorityId());
							if(helperUtil.verifySignature(lp.getLocationAuthorityId(), msgJson ,signature ))
							{
								if(witness.getWorkerId().equals(uniqueIdentifier))
								{
									Date assertionTime = new Date();
									String hashLPAssertMsg = helperUtil.getHash(helperUtil.getJson(message)); //H(LpAssertReqMsg)
									AssertionStatment as = new AssertionStatment(decisionBlockId, proverId, locationAuthorityId, uniqueIdentifier, hashLPAssertMsg, assertionTime);
									AssertionResponse ar = new AssertionResponse(as);
									msgJson =helperUtil.getJson(ar);
									signature = helperUtil.sign(uniqueIdentifier, msgJson);
									ar.setSignature(signature);
									ActorRef locationAuthority = getSender();
									pendingRequests.remove(pendingApprovalMessageKey);
									msgJson =helperUtil.getJson(ar);
									locationAuthority.tell(ar,getSelf());
									pendingAssertionResponseVerifications.put(helperUtil.getHash(msgJson), ar);
									
								}
								else
									_log.error("Mismatched witness. Message was intended for:"+witness.getWorkerId());
							}
							else
								_log.error("Signature mismatched for LocationProof Assertion Message:"+msgJson);
						}
						else
							_log.error("Fake Approval Message Received From :"+locationAuthorityId);
					}
					
					
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
						String hash = helperUtil.getHash(helperUtil.getJson(message));
						pendingRequests.put(hash, message);
					}
					else
						_log.error("Signature mismatched for Approval Message:"+msgJson);
				})
				.match(WNConnectAck.class, message-> {
					supervisor = getSender();
					_log.info("Witness Connection Acknowledgement Received");
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
