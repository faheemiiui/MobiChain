package com.ciit.ps.crypto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.apache.commons.codec.binary.Hex;

public class HashingUtil {
	
	private static MessageDigest digest = null;
	
	static{
		try {
			digest = MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
	}
	
	public static byte[] hash(String data)
	{
		byte[] hashedValue = null;
		hashedValue = digest.digest(data.getBytes(StandardCharsets.UTF_8));
		return hashedValue;
	}

	public static void main(String[] args) {
		byte[] hashedValue = HashingUtil.hash("decision_genesis_block_id");
		System.out.println("Hash(\"decision_genesis_block_id\") = "+ Hex.encodeHexString(hashedValue));
		
		hashedValue = HashingUtil.hash("provenance_genesis_block_id");
		System.out.println("Hash(\"provenance_genesis_block_id\") = "+ Hex.encodeHexString(hashedValue));
	}

}
