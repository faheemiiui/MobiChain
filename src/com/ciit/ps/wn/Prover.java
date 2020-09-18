package com.ciit.ps.wn;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;

import com.ciit.lp.entities.AssertedLocationProof;
import com.ciit.lp.entities.LocationAuthorityDetails;
import com.ciit.lp.entities.WitnessDetails;
import com.ciit.lp.messages.ALPACK;
import com.ciit.lp.messages.ALPVerificationReqMsg;
import com.ciit.lp.messages.ALPVerificationResMsg;
import com.ciit.lp.messages.ApprovalMessage;
import com.ciit.lp.messages.LocationProofReq;
import com.ciit.lp.messages.ProofRequestMsg;
import com.ciit.ps.helper.HelperUtil;
import com.ciit.ps.utils.ConfigurationLoader;

import java.util.Date;

import org.apache.log4j.Logger;

public class Prover extends AbstractActor {

	private final static Logger	_log	= Logger.getLogger(Prover.class);
	private ActorRef supervisor = null;
	private String uniqueIdentifier = null;
	private AssertedLocationProof assertedLP = null;
	public boolean isMsgPersistanceEnabled = false;
	
	public Prover(String uniqueIdentifier)
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
	
	public static Props props(String uniqueIdentifier) {
		return Props.create(Prover.class,() -> new Prover(uniqueIdentifier));
	}
	
	
	@Override
	public Receive createReceive() {
		return receiveBuilder()
		.match(ALPVerificationResMsg.class,message -> {
			HelperUtil helperUtil = new HelperUtil();
			String signature = message.getSignature();
			message.setSignature(null);
			String msgJson = helperUtil.getJson(message);
			if(helperUtil.verifySignature(message.getWitnessId(), msgJson ,signature ))
			{
				message.setSignature(signature);
				Date ackTime = new Date();
				_log.info("Asserted Location Proof verification response received by:"+uniqueIdentifier+" -> "+message.isValid());
				ApprovalMessage approvalMsg = assertedLP.getLpAssertReq().getLp().getLpReqMsg().getApprovalMsg();
				ALPACK ack = new ALPACK(assertedLP, message, ackTime, approvalMsg.getDecisionBlockId());
				msgJson = helperUtil.getJson(ack);
				signature = helperUtil.sign(uniqueIdentifier, msgJson);
				ack.setProverSignature(signature);
				if(isMsgPersistanceEnabled)
				{
					helperUtil.saveObject(ack, helperUtil.getMsgPersistancePath()+"/"+approvalMsg.getDecisionBlockId()+"_ALPVerified.msg");
				}
				LocationAuthorityDetails locationAuthority = approvalMsg.getLocationAuthority();
				String address = helperUtil.getAddress(locationAuthority.getIpAddress(), locationAuthority.getPort(), locationAuthority.getWorkerId());
				getContext().system().actorFor(address).tell(ack, getSelf());
				_log.info(uniqueIdentifier+" sending Asserted Location Proof Acknowledgement to: "+locationAuthority.getWorkerId());
				
			}
			else
				_log.error("Signature mismatched for Asserted Location Proof Verification Response:"+msgJson);	
		})
		.match(AssertedLocationProof.class, message -> {
			assertedLP = message;
			LocationProofReq lpReq = assertedLP.getLpAssertReq().getLp().getLpReqMsg();
			Date requestTime = lpReq.getReqTime();
			Date responseTime = message.getAlpTime();
			String locationAuthorityId = message.getLpAssertReq().getLp().getLocationAuthorityId();
			_log.info("Asserted Location Proof received by:"+uniqueIdentifier+" within "+(responseTime.getTime()-requestTime.getTime()));
			
			HelperUtil helperUtil = new HelperUtil();
			String signature = message.getSignature();
			message.setSignature(null);
			String msgJson = helperUtil.getJson(message);
			
			if(helperUtil.verifySignature(locationAuthorityId, msgJson ,signature ))
			{
				message.setSignature(signature);
				// send Asserted Location Proof to witness for cross verification
				Date alpVerifyReqTime = new Date();
				ALPVerificationReqMsg vreq = new ALPVerificationReqMsg(message, alpVerifyReqTime);
				msgJson = helperUtil.getJson(vreq);
				signature = helperUtil.sign(uniqueIdentifier, msgJson);
				vreq.setSignature(signature);
				WitnessDetails witness = message.getLpAssertReq().getLp().getLpReqMsg().getApprovalMsg().getWitness();
				String address = helperUtil.getAddress(witness.getIpAddress(), witness.getPort(), witness.getWorkerId());
				getContext().system().actorFor(address).tell(vreq, getSelf());
				_log.info(uniqueIdentifier+" is sending ALP Verification Request to: "+witness.getWorkerId());
			}
			else
				_log.error("Signature mismatched for Asserted Location Proof:"+msgJson);
		})
		.match(ApprovalMessage.class, message-> {
			supervisor = getSender();
//			String decisionBlockId = message.getDecisionBlockId();
			LocationAuthorityDetails la = message.getLocationAuthority();
//			WitnessDetails witness = message.getWitness();
			ProofRequestMsg preq = message.getPreq();
			
			Date requestTime = preq.getProver().getReqTime();
			Date responseTime = message.getResponseTime();
			
			//"akka.tcp://MobiChain@127.0.0.1:2552/user/CIIT_LPS_SN1"
			
			_log.info("Approval Message received by:"+uniqueIdentifier+" within "+(responseTime.getTime()-requestTime.getTime()));
			
			HelperUtil helperUtil = new HelperUtil();
			String signature = message.getSignature();
			message.setSignature(null);
			String msgJson = helperUtil.getJson(message);
			if(helperUtil.verifySignature(message.getRRSNID(), msgJson ,signature ))
			{
				message.setSignature(signature);
				Date reqTime = new Date();
				LocationProofReq lpReq = new LocationProofReq(message, reqTime, preq.getProver().getProverId());
				msgJson = helperUtil.getJson(lpReq);
				signature = helperUtil.sign(uniqueIdentifier, msgJson);
				lpReq.setSignature(signature);
//				System.out.println("Sending request to "+la.getPath());
				//"akka.tcp://MobiChain@"+la.getIpAddress()+":"+la.getPort()+"/user/"+la.getWorkerId()
				String address = helperUtil.getAddress(la.getIpAddress(), la.getPort(), la.getWorkerId());
				ActorSystem system = getContext().system();
				system.actorFor(address).tell(lpReq, getSelf());
//				System.out.println("Proof Request to Location Authority....");
			}
			else
				_log.error(uniqueIdentifier+" -> Signature mismatched for Approval Message:"+msgJson);
			
		})
		.match(String.class, reply->{
			System.out.println(reply);
		})
//		.match(Object.class, msg-> {
//			supervisor = getSender();
//			_log.info("Communication Channel established with "+msg);
//		})
		.matchAny(msg->{
			System.out.println(uniqueIdentifier+ " received msg:"+msg.getClass());
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
