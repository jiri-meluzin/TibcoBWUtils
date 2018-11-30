package com.meluzin.tibcobwutils.earcomparer;

import com.meluzin.tibcobwutils.deploymentrepository.structure.impl.PasswordDecrypter;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.Namespace;

public class Decrypter {
	private static final String DECRYPT = "decrypt";
	private static final String ENCRYPT = "encrypt";
	
	public static void main(String[] args) {
		ArgumentParser argParser = ArgumentParsers.newArgumentParser("Tibco passwords de/encryptor", true, "-")
				.description("Encrypts or decrypts Tibco password from fullConfigs");
		argParser.addArgument("-action").choices(ENCRYPT, DECRYPT).required(true).help("What to do - encrypt or decrypt. Ex: decrypt");
		argParser.addArgument("-password").type(String.class).required(true).help("Password string to be encrypted or decrypted. Ex: #!St7Uzipt4y6Od6iTLGNtwSUiLk00LuMB");
		
		Namespace res = argParser.parseArgsOrFail(args);
		
		String action = res.getString("action");
		String password = res.getString("password");
		
		switch (action) {
		case ENCRYPT: System.out.println(new PasswordDecrypter().encrypt(password));
			break;
		case DECRYPT: System.out.println(new PasswordDecrypter().decrypt(password));
			break;
		}
		
	}
}
