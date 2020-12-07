/*
 * Copyright (c) 2021, Oracle and/or its affiliates.  All rights reserved.
 * This software is licensed to you under the Universal Permissive License (UPL) 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */

package io.jenkins.plugins.oci.messages;

public class Messages {
    public static class CloudCredentialMessages {
        public static final String INCORRECT_CREDENTIAL_ERROR_MESSAGE = "Failed to connect to Oracle Cloud Infrastructure, Please verify all the correct credential information were provided.";
        public static final String CREDENTIAL_SUCCESS_MESSAGE = "Connection successful.";
    }

    public static class DisplayNames {
        public static final String CREDENTIAL_DISPLAY_NAME = "Oracle Cloud Infrastructure Credentials - Devops";
        public static final String DEPLOYMENT_DISPLAY_NAME = "OCI Deployment";
        public static final String ARTIFACT_UPLOAD_DISPLAY_NAME = "OCI Artifact Upload";
    }
}
