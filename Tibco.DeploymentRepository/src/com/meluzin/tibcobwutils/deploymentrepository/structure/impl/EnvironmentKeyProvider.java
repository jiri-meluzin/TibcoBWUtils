package com.meluzin.tibcobwutils.deploymentrepository.structure.impl;

public class EnvironmentKeyProvider implements PasswordDecrypterSecretProvider {
    private static final String ENV_KEY_NAME = "TIBCO_DECRYPTION_KEY";

    @Override
    public byte[] getSecret() {
        String key = System.getenv(ENV_KEY_NAME);
        if (key == null) {
            throw new IllegalStateException("Environment variable '" + ENV_KEY_NAME + "' not set");
        }
        return key.getBytes();
    }
}
