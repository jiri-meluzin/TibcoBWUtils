package com.meluzin.tibcobwutils.deploymentrepository.structure.impl;

public class DynamicKeyProvider implements PasswordDecrypterSecretProvider {
	@Override
	public byte[] getSecret() {
		String providerClassName = System.getenv("TIBCO_KEY_PROVIDER_CLASS");
		if (providerClassName == null) {
			throw new RuntimeException(
					"Environment variable 'TIBCO_KEY_PROVIDER_CLASS' is not set. Please set it to the fully qualified class name of the key provider.");
		}
		try {
			Class<?> providerClass = Class.forName(providerClassName);
			if (!PasswordDecrypterSecretProvider.class.isAssignableFrom(providerClass)) {
				throw new IllegalStateException("Specified class '" + providerClassName
						+ "' does not implement PasswordDecrypterSecretProvider");
			}
			PasswordDecrypterSecretProvider providerInstance = (PasswordDecrypterSecretProvider) providerClass
					.getDeclaredConstructor().newInstance();
			return providerInstance.getSecret();
		} catch (ClassNotFoundException e) {
			throw new IllegalStateException("Specified class '" + providerClassName + "' not found", e);
		} catch (Exception e) {
			throw new IllegalStateException("Failed to instantiate key provider class '" + providerClassName + "'", e);
		}
	}
}
