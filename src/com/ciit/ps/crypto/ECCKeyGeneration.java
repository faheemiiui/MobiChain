package com.ciit.ps.crypto;
// The following code is from http://www.academicpub.org/PaperInfo.aspx?PaperID=14496 .
import java.io.File;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.ECGenParameterSpec;

public class ECCKeyGeneration {

	// keytool -genkeypair -keyalg EC -keystore ecckeystore.jks -keysize 256 -alias ecckeyname
	public static void generateKeys(String keysPath,String keyPrefix) throws Exception
	{
		PrivateKey privKey = null;
		PublicKey pubKey = null;
		KeyPair kp = null;
		
		KeyPairGenerator kpg;
	    kpg = KeyPairGenerator.getInstance("EC","SunEC");

	    ECGenParameterSpec ecsp;
	    ecsp = new ECGenParameterSpec("secp224r1");;//("secp521r1"); //("secp384r1")
	    kpg.initialize(ecsp);

	    kp = kpg.genKeyPair();
	    privKey = kp.getPrivate();
	    pubKey = kp.getPublic();
//	    System.out.println(privKey.toString());
//	    System.out.println(pubKey.toString());
	    KeysManager.saveKey(privKey,keysPath+"/"+keyPrefix+"_private.key");
	    KeysManager.saveKey(pubKey,keysPath+"/"+keyPrefix+"_public.key");

	}

	public static void main(String[] args) throws Exception {
		File keystore = new File("E:/Education Data/PHD Work/Thesis Idea/Source Code/Crypto");
		for(int i=0;i<101;i++)
		{
//			ECCKeyGeneration.generateKeys(keystore.getAbsolutePath(),"CIIT_LPS_LA1"+i);
//			ECCKeyGeneration.generateKeys(keystore.getAbsolutePath(),"CIIT_LPS_WIT1"+i);
			ECCKeyGeneration.generateKeys(keystore.getAbsolutePath(),"CIIT_LPS_PV1"+i);
//			ECCKeyGeneration.generateKeys(keystore.getAbsolutePath(),"CIIT_LPS_SN"+i);
		}
		System.out.println("All keys generated");
//		ECCKeyGeneration.generateKeys(keystore.getAbsolutePath(),"CIIT_LPS_PV1");
//		ECCKeyGeneration.generateKeys(keystore.getAbsolutePath(),"CIIT_LPS_SN7");
//		ECCKeyGeneration.generateKeys(keystore.getAbsolutePath(),"CIIT_LPS_SN8");
//		ECCKeyGeneration.generateKeys(keystore.getAbsolutePath(),"CIIT_LPS_SN9");
//		KeyPairGenerator kpg;
//		kpg = KeyPairGenerator.getInstance("EC","SunEC");
//		ECGenParameterSpec ecsp;
//		ecsp = new ECGenParameterSpec("secp192r1");
//		kpg.initialize(ecsp);
//
//		KeyPair kp = kpg.genKeyPair();
//		PrivateKey privKey = kp.getPrivate();
//		PublicKey pubKey = kp.getPublic();
//
//		System.out.println(privKey.toString());
//		System.out.println(pubKey.toString());
	}
}