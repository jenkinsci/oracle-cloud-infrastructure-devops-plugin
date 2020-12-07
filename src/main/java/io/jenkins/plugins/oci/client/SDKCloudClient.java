/*
 * Copyright (c) 2021, Oracle and/or its affiliates.  All rights reserved.
 * This software is licensed to you under the Universal Permissive License (UPL) 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */

package io.jenkins.plugins.oci.client;

import com.oracle.bmc.http.DefaultConfigurator;
import io.jenkins.plugins.oci.messages.Messages.CloudCredentialMessages;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.oracle.bmc.ClientRuntime;
import com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider;
import com.oracle.bmc.identity.Identity;
import com.oracle.bmc.identity.IdentityClient;
import com.oracle.bmc.identity.requests.*;
import com.oracle.bmc.model.BmcException;
import jenkins.model.Jenkins;

public class SDKCloudClient implements CloudClient {
    private static final Logger LOGGER = Logger.getLogger(SDKCloudClient.class.getName());

    private SimpleAuthenticationDetailsProvider provider;
    private String regionId;

    public SDKCloudClient(SimpleAuthenticationDetailsProvider provider, String regionId) {
        this.provider = provider;
        this.regionId = regionId;
        ClientRuntime.setClientUserAgent("Oracle-Jenkins/" + Jenkins.VERSION);
    }


    IdentityClient getIdentityClient() {
        IdentityClient identityClient = new IdentityClient(provider, null, new DefaultConfigurator());
        identityClient.setRegion(regionId);
        return identityClient;
    }

    @Override
    public void authenticate() throws BmcException {
        Identity identityClient = getIdentityClient();

        try {
            identityClient.getUser(GetUserRequest.builder().userId(provider.getUserId()).build());
        } catch(BmcException e) {
            LOGGER.log(Level.FINE, CloudCredentialMessages.INCORRECT_CREDENTIAL_ERROR_MESSAGE, e);
            throw e;
        } finally {
            try {
                identityClient.close();
            } catch (Exception e) {
                // TODO Auto-generated catch block
                LOGGER.log(Level.FINE, "Error closing identity client:", e);
            }
        }
    }

    @Override
    public void close() throws Exception {
        // TODO Auto-generated method stub

    }
}
