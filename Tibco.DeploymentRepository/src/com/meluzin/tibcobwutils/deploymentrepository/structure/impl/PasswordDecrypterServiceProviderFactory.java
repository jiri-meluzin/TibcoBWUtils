package com.meluzin.tibcobwutils.deploymentrepository.structure.impl;

public class PasswordDecrypterServiceProviderFactory {

	private static PasswordDecrypterServiceProviderFactory instance;
	private static PasswordDecrypterSecretProvider keyProvider;
	static {
		String strategyType = System.getenv("TIBCO_KEY_STRATEGY");

		if (strategyType == null) {
			throw new RuntimeException("Environment variable 'TIBCO_KEY_STRATEGY' is not set. Please set it to 'ENV', 'DYNAMIC', or 'CONSTANT'.");
		} else {
			switch (strategyType.toUpperCase()) {
			case "ENV":
				keyProvider = new EnvironmentKeyProvider();
				break;
			case "DYNAMIC":
				keyProvider = new DynamicKeyProvider();
				break;
			case "SET-LATER": break; // Will be set later via setProvider()
			case "CONSTANT":
			default:
				keyProvider = new ConstantKeyProvider();
				break;
			}
		}
		instance = new PasswordDecrypterServiceProviderFactory();
		instance.setProvider(keyProvider);
	}

	private PasswordDecrypterSecretProvider provider;

	public PasswordDecrypterSecretProvider getProvider() {
		if (provider == null) {
			throw new IllegalStateException("PasswordDecrypterSecretProvider has not been set yet");
		}
		return provider;
	}

	public void setProvider(PasswordDecrypterSecretProvider provider) {
		this.provider = provider;
	}

	public static PasswordDecrypterServiceProviderFactory getInstance() {
		return instance;
	}
}