package com.ciit.ps.helper;

import com.ciit.ps.crypto.ECCSignature;
import com.ciit.ps.crypto.HashingUtil;
import com.ciit.ps.crypto.KeysManager;
import com.ciit.ps.utils.ConfigurationLoader;
import com.google.gson.Gson;

import java.io.File;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.security.PrivateKey;
import java.security.PublicKey;

import org.apache.commons.codec.binary.Hex;

public class HelperUtil {

	public String getAddress(String ipAddress,int port,String workerId)
	{
		//"akka.tcp://MobiChain@"+la.getIpAddress()+":"+la.getPort()+"/user/"+la.getWorkerId()
		String address = "akka.tcp://MobiChain@"+ipAddress+":"+port+"/user/"+workerId;
		return address;
	}
	
	public String getJson(Object obj) throws Exception
	{
		String json = null;
		if(obj!=null)
		{
			Gson gson = new Gson();
			return gson.toJson(obj);
		}
		return json;
	}
	
	public String getHash(String data) throws Exception
	{
		String value = null;
		if(data!=null)
		{
			value = Hex.encodeHexString(HashingUtil.hash(data));
		}
		return value;
	}
	
	
	public String sign(String userIdentifier, String data) throws Exception
	{
		String signature = null;
		PrivateKey privKey = null;
		if(data!=null)
		{
			File keystore = new File(ConfigurationLoader.getProperty("keys_path"));
//			System.out.println("Lookup private key:"+keystore.getAbsolutePath()+"/"+userIdentifier+"_private.key");
			privKey  = (PrivateKey)KeysManager.readKey(keystore.getAbsolutePath()+"/"+userIdentifier+"_private.key");
			byte[] dataBytes = data.getBytes();
			ECCSignature signatory = new ECCSignature();
			signature = Hex.encodeHexString(signatory.sign(privKey,dataBytes));
		}
		return signature;
	}
	
	
	public boolean verifySignature(String userIdentifier,String data,String signedData) throws Exception
	{
		boolean isValid = false;
		PublicKey pubKey =null;
		if(data!=null)
		{
			File keystore = new File(ConfigurationLoader.getProperty("keys_path"));
			pubKey  = (PublicKey)KeysManager.readKey(keystore.getAbsolutePath()+"/"+userIdentifier+"_public.key");
//			System.out.println("Lookup public key:"+keystore.getAbsolutePath()+"/"+userIdentifier+"_public.key");
			byte[] dataBytes = data.getBytes();
			byte[] signedBytes = Hex.decodeHex(signedData.toCharArray());
			ECCSignature signatory = new ECCSignature();
			isValid = signatory.verify(pubKey,dataBytes,signedBytes);
		}
		return isValid;
	}
	
	public String getMsgPersistancePath()
	{
		String path = ConfigurationLoader.getProperty("h2db_base_dir");
		return (path +"/Messages");
	}
	
	public void saveObject(Object obj,String path) throws Exception
	{
		// write object to file
		if(obj!=null && path!=null)
		{
			File dest = new File(path);
			if(!dest.getParentFile().exists())
			{
				dest.getParentFile().mkdirs();
			}
			FileOutputStream fos = new FileOutputStream(dest);
			ObjectOutputStream oos = new ObjectOutputStream(fos);
			oos.writeObject(obj);
			oos.flush();
			oos.close();
		}
		
	}
	
	public static void main(String[] args) {
		String data = "Test";
		try {
			HelperUtil helperUtil = new HelperUtil();
			String signature  = helperUtil.sign("CIIT_LPS_WIT12",data);
			System.out.println(helperUtil.verifySignature("CIIT_LPS_WIT12",data, signature));
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

}
