/*
 * Copyright (c) 2021, Oracle and/or its affiliates.  All rights reserved.
 * This software is licensed to you under the Universal Permissive License (UPL) 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */

package io.jenkins.plugins.oci.deployment;

public enum ExecutionMode {
    // Sync: Wait for deployment completion; Async: Completes the deployment as soon as IN_PROGRESS.
    SYNC,
    ASYNC
}