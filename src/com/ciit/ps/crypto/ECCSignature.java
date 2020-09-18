package com.ciit.ps.crypto;
// The following code is from http://www.academicpub.org/PaperInfo.aspx?PaperID=14496 .
import com.ciit.ps.helper.HelperUtil;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;

public class ECCSignature {
	
	private final String signAlgo = "SHA1withECDSA";
	
	public byte[] sign(PrivateKey privKey,byte[] data) throws Exception
	{
		Signature ecdsa = Signature.getInstance(signAlgo,"SunEC");
		ecdsa.initSign(privKey);
		ecdsa.update(data);
		byte[] baSignature = ecdsa.sign();
		return baSignature;
	}
	
	// function to verify signed data of other parties using their public key
	public boolean verify(PublicKey pubKey, byte[] data, byte[] signedData) throws Exception
	{
		Signature ecdsa = Signature.getInstance(signAlgo,"SunEC");
		ecdsa.initVerify(pubKey);
		ecdsa.update(data);
		boolean result = ecdsa.verify(signedData);
		return result;
	}
	
	public static void main(String[] args) throws Exception {

//		String uniqueIdentifier="CIIT_LPS_LA1";
		String uniqueIdentifier= "CIIT_LPS_WIT12";
//		String uniqueIdentifier="CIIT_LPS_PV1";


		String text = "{\"witness\":{\"ipAddress\":\"Faheem-NB/172.23.1.149\",\"longitude\":76.034793,\"latitude\":36.685653,\"port\":3555,\"reqTime\":\"Apr 12, 2018 5:33:26 PM\",\"workerId\":\"CIIT_LPS_WIT12\",\"isLocationAuthority\":false},\"rrsnId\":\"CIIT_LPS_SN1\"}";
		System.out.println("Text: " + text);
		HelperUtil helperUtil = new HelperUtil();
		String signature = helperUtil.sign("CIIT_LPS_WIT12", text);
		System.out.println("Signature:"+signature);
		System.out.println(helperUtil.verifySignature("CIIT_LPS_WIT12", text, signature));
//		byte[] baText = text.getBytes("UTF-8");
//
//		byte[] baSignature = ECCSignature.sign(baText);
//		long signEnd = System.currentTimeMillis();    
//		System.out.println("Signature: 0x" + (new BigInteger(1, baSignature).toString(16)).toUpperCase()+" generated in "+(signEnd-signStart)+" ms");
//
//		signStart = System.currentTimeMillis();
//		boolean result = ECCSignature.verify(baText, baSignature);
//		signEnd = System.currentTimeMillis();
//		System.out.println("Verify Valid: " + result+" in "+(signEnd-signStart)+" ms");
	}
}