/*
 * Copyright (c) 2021, Oracle and/or its affiliates.  All rights reserved.
 * This software is licensed to you under the Universal Permissive License (UPL) 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */

package io.jenkins.plugins.oci.credentials;

import com.cloudbees.plugins.credentials.common.StandardCredentials;

public interface CloudCredentials extends StandardCredentials {
    String getFingerprint();

    String getApikey();

    String getPassphrase();

    String getTenantId();

    String getUserId();

    String getRegionId();
}
