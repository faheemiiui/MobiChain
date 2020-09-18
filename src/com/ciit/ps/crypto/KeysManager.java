package com.ciit.ps.crypto;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;


public class KeysManager {

	public static void saveKey(Object obj,String path) throws Exception
	{
		FileOutputStream fos = new FileOutputStream(new File(path));
		ObjectOutputStream oos = new ObjectOutputStream(fos);
		oos.writeObject(obj);
		oos.flush();
		oos.close();
		fos.close();
	}
	
	public static Object readKey(String path) throws Exception
	{
		FileInputStream fis = new FileInputStream(new File(path));
		ObjectInputStream ois = new ObjectInputStream(fis);
		Object obj = ois.readObject();
		ois.close();
		fis.close();
		return obj;
	}
	
	public static void main(String[] args) throws Exception {
		String path = "D:/";
		KeyPairGenerator keyGen = KeyPairGenerator.getInstance("DSA");
		keyGen.initialize(512);
		KeyPair keyPair = keyGen.generateKeyPair();
		PrivateKey sk = keyPair.getPrivate();
		PublicKey pubKey= keyPair.getPublic();
		KeysManager.saveKey(sk, path+"/.sk");
		KeysManager.saveKey(pubKey, path+"/.pk");
	}

}
