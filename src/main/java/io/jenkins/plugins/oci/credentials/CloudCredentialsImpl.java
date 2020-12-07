/*
 * Copyright (c) 2021, Oracle and/or its affiliates.  All rights reserved.
 * This software is licensed to you under the Universal Permissive License (UPL) 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */

package io.jenkins.plugins.oci.credentials;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import hudson.model.Item;
import io.jenkins.plugins.oci.client.CloudClient;
import io.jenkins.plugins.oci.client.SDKCloudClient;
import io.jenkins.plugins.oci.messages.Messages.CloudCredentialMessages;
import io.jenkins.plugins.oci.messages.Messages.DisplayNames;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.impl.BaseStandardCredentials;
import com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider;
import com.oracle.bmc.model.BmcException;

import hudson.Extension;
import hudson.util.FormValidation;
import hudson.util.Secret;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import org.kohsuke.stapler.verb.POST;

public final class CloudCredentialsImpl extends BaseStandardCredentials implements CloudCredentials {
    private static final Logger LOGGER = Logger.getLogger(CloudCredentials.class.getName());

    private final String fingerprint;
    private final Secret apikey;
    private final Secret passphrase;
    private final String tenantId;
    private final String userId;
    private final String regionId;

    @DataBoundConstructor
    public CloudCredentialsImpl(CredentialsScope scope,
            String id,
            String description,
            String fingerprint,
            Secret apikey,
            Secret passphrase,
            String tenantId,
            String userId,
            String regionId) {
        super(scope, id, description);
        this.fingerprint = fingerprint;
        this.apikey = apikey;
        this.passphrase = passphrase;
        this.tenantId = tenantId;
        this.userId = userId;
        this.regionId = regionId;
    }

    @Override
    public String getFingerprint() {
        return fingerprint;
    }

    @Override
    public String getApikey() {
        return getPlainText(apikey);
    }

    @Override
    public String getPassphrase() {
        return getPlainText(passphrase);
    }

    @Override
    public String getTenantId() {
        return tenantId;
    }

    @Override
    public String getUserId() {
        return userId;
    }

    @Override
    public String getRegionId() {
        return regionId;
    }

    protected String getEncryptedValue(String str) {
        return Secret.fromString(str).getEncryptedValue();
    }

    protected String getPlainText(Secret secret) {
        if (secret != null) {
            return secret.getPlainText();
        }
        return null;
    }

    @Extension
    public static class DescriptorImpl extends BaseStandardCredentials.BaseStandardCredentialsDescriptor {

        @Override
        public String getDisplayName() {
            return DisplayNames.CREDENTIAL_DISPLAY_NAME;
        }

        @POST
        public FormValidation doTestConnection(
                @QueryParameter String fingerprint,
                @QueryParameter String apikey,
                @QueryParameter String passphrase,
                @QueryParameter String tenantId,
                @QueryParameter String userId,
                @QueryParameter String regionId,
                @AncestorInPath Item item) {
            if (item == null) {
                if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
                    return FormValidation.ok();
                }
            } else {
                if (!item.hasPermission(Item.EXTENDED_READ)
                        && !item.hasPermission(CredentialsProvider.USE_ITEM)) {
                    return FormValidation.ok();
                }
            }
            SimpleAuthenticationDetailsProvider provider = SimpleAuthenticationDetailsProvider.builder()
                    .fingerprint(fingerprint)
                    .passPhrase(passphrase)
                    .privateKeySupplier(() ->  new ByteArrayInputStream(apikey.getBytes(StandardCharsets.UTF_8)))
                    .tenantId(tenantId)
                    .userId(userId)
                    .build();
            CloudClient client = new SDKCloudClient(provider, regionId);
            try{
                client.authenticate();
                return FormValidation.ok(CloudCredentialMessages.CREDENTIAL_SUCCESS_MESSAGE);
            } catch(BmcException e){
                LOGGER.log(Level.INFO, CloudCredentialMessages.INCORRECT_CREDENTIAL_ERROR_MESSAGE, e);
                return FormValidation.error(CloudCredentialMessages.INCORRECT_CREDENTIAL_ERROR_MESSAGE);
            }
        }
    }
}
