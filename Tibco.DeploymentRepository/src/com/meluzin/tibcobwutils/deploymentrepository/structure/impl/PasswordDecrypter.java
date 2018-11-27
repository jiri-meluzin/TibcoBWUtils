package com.meluzin.tibcobwutils.deploymentrepository.structure.impl;

import java.io.ByteArrayOutputStream;
import java.security.Key;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.spec.IvParameterSpec;


public class PasswordDecrypter {

	private static final byte[] TIBCO_SECRET = { 28, -89, -101, -111, 91, -113, 26, -70, 98, -80, -23, -53, -118, 93, -83, -17,
			28, -89, -101, -111, 91, -113, 26, -70 };
	public String decrypt(String encrypted) {
		try {
			byte[] base64EncryptedBytes = Base64.getDecoder().decode(encrypted.substring(2));
			Cipher cipher = Cipher.getInstance("DESede/CBC/PKCS5Padding", "SunJCE");;
			int blockSize = cipher.getBlockSize();
			byte[] arrayOfByte = new byte[blockSize];

			readIV(base64EncryptedBytes, arrayOfByte);
			Key key = new RawKey("DESede", TIBCO_SECRET);
			cipher.init(2, (Key) key, new IvParameterSpec(arrayOfByte));
			char[] decrypted = (decrypt(base64EncryptedBytes, blockSize, cipher));

			return new String(decrypted);
		} catch (Exception e) {
			e.printStackTrace();
			return encrypted;
		}
	}

	public String encrypt(String decrypted) {
		try {

			Cipher cipher = Cipher.getInstance("DESede/CBC/PKCS5Padding", "SunJCE");;
			int i = cipher.getBlockSize();
			byte[] randomBytes = new byte[i];
			SecureRandom.getInstance("SHA1PRNG").nextBytes(randomBytes);

//			readIV(paramArrayOfByte1, arrayOfByte);
			Key rawKey = new RawKey("DESede", TIBCO_SECRET);
			cipher.init(Cipher.ENCRYPT_MODE, (Key) rawKey, new IvParameterSpec(randomBytes));
//			char[] decrypted = (decrypt(paramArrayOfByte1, i, localCipher));

		    byte[] decryptedBytes = decrypted.getBytes();
		    byte[] decryptedBytesDouble = new byte[decryptedBytes.length * 2];
		    
		    for (int j = 0; j < decryptedBytes.length; j++)
		      {
		    	decryptedBytesDouble[j*2] = ((byte)(decryptedBytes[j] & 0xFF));
		    	decryptedBytesDouble[j*2 + 1] = ((byte)(decryptedBytes[j] >> '\b'));
		      }
			 ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
			    CipherOutputStream cipherOutputStream = new CipherOutputStream(outputStream, cipher);
				cipherOutputStream.write(decryptedBytesDouble);
			    cipherOutputStream.flush();
			    cipherOutputStream.close();
			    byte[] encryptedBytes = outputStream.toByteArray();
			    byte[] finalBytes = new byte[encryptedBytes.length + cipher.getBlockSize()];
			    System.arraycopy(randomBytes, 0, finalBytes, 0, i);
			    System.arraycopy(encryptedBytes, 0, finalBytes, i, encryptedBytes.length);
			String encrypted = Base64.getEncoder().encodeToString(finalBytes);
			return new String("#!" + encrypted);
		} catch (Exception e) {
			e.printStackTrace();
			return decrypted;
		}
	}

	public void readIV(byte[] paramArrayOfByte1, byte[] paramArrayOfByte2) {
		if (paramArrayOfByte2.length > paramArrayOfByte1.length) {
			throw new RuntimeException();
		}
		System.arraycopy(paramArrayOfByte1, 0, paramArrayOfByte2, 0, paramArrayOfByte2.length);
	}

	public char[] decrypt(byte[] paramArrayOfByte, int paramInt, Cipher paramCipher)
			throws IllegalBlockSizeException, BadPaddingException {
		byte[] arrayOfByte = paramCipher.doFinal(paramArrayOfByte, paramInt, paramArrayOfByte.length - paramInt);
		if (arrayOfByte.length % 2 != 0) {
			throw new IllegalArgumentException("odd length");
		}
		char[] arrayOfChar = new char[arrayOfByte.length / 2];
		int i = 0;
		for (int j = 0; i < arrayOfChar.length; j += 2) {
			arrayOfChar[(i++)] = ((char) ((char) arrayOfByte[(j + 1)] << '\b' | (char) (arrayOfByte[j] & 0xFF)));
		}
		for (i = 0; i < arrayOfByte.length; i++) {
			arrayOfByte[i] = -1;
		}
		return arrayOfChar;
	}

	static class RawKey implements Key {
		private static final long serialVersionUID = -3628033443822988165L;
		String keyAlgorithm;
		byte[] bytes;

		public RawKey(String paramString, byte[] paramArrayOfByte) {
			this.keyAlgorithm = paramString;
			this.bytes = paramArrayOfByte;
		}

		public byte[] getEncoded() {
			return this.bytes;
		}

		public String getAlgorithm() {
			return this.keyAlgorithm;
		}

		public String getFormat() {
			return "RAW";
		}
	}
}
