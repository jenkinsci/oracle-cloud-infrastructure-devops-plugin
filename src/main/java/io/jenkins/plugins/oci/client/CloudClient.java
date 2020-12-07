/*
 * Copyright (c) 2021, Oracle and/or its affiliates.  All rights reserved.
 * This software is licensed to you under the Universal Permissive License (UPL) 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */

package io.jenkins.plugins.oci.client;

import com.oracle.bmc.model.BmcException;

public interface CloudClient extends AutoCloseable {
    /**
     * @throws BmcException if an error occurs
     */
    void authenticate() throws BmcException;
}
