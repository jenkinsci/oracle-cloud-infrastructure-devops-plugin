/*
 * Copyright (c) 2021, Oracle and/or its affiliates.  All rights reserved.
 * This software is licensed to you under the Universal Permissive License (UPL) 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */

package io.jenkins.plugins.oci.deployment.polling;
import org.kohsuke.stapler.DataBoundConstructor;

public class PollingConfig {
    private long timeoutSeconds;
    private long pollingIntervalSeconds;
    private boolean isValid;
    private final static long TIMEOUT_DEFAULT_VAL = 2 * 60;
    private final static long POLLING_INTERVAL_DAFAULT_VAL = 10;

    @DataBoundConstructor
    public PollingConfig(long timeoutSeconds, long pollingIntervalSeconds) throws Exception {
        try {
            this.timeoutSeconds = timeoutSeconds;
            this.pollingIntervalSeconds = pollingIntervalSeconds;
            this.isValid = true;
        } catch (NumberFormatException ex) {
            this.isValid = false;
        }
    }

    public PollingConfig() {
        this.timeoutSeconds = TIMEOUT_DEFAULT_VAL;
        this.pollingIntervalSeconds = POLLING_INTERVAL_DAFAULT_VAL;
        this.isValid = true;
    }

    public long getTimeoutSeconds() {
        return this.timeoutSeconds;
    }
    public long getPollingIntervalSeconds() {
        return this.pollingIntervalSeconds;
    }
    public boolean getIsValid() {
        return this.isValid;
    }
    public void setTimeoutSeconds(long timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }
    public void setPollingIntervalSeconds(long pollingIntervalSeconds) {
        this.pollingIntervalSeconds = pollingIntervalSeconds;
    }
    public void setIsValid(boolean isValid) {
        this.isValid = isValid;
    }
}
