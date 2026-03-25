package com.meluzin.tibcobwutils.deploymentrepository.structure.impl;

public class ConstantKeyProvider implements PasswordDecrypterSecretProvider {
    private static final byte[] SECRET = { 28, -89, -101, -111, 91, -113, 26, -70, 98, -80, -23, -53, -118, 93, -83, -17,
    				28, -89, -101, -111, 91, -113, 26, -70 };

    @Override
    public byte[] getSecret() {
        return SECRET;
    }
}
