/*
 * Copyright (c) 2021, Oracle and/or its affiliates.  All rights reserved.
 * This software is licensed to you under the Universal Permissive License (UPL) 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */

package io.jenkins.plugins.oci.artifact;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.ListBoxModel;
import lombok.Data;
import org.kohsuke.stapler.DataBoundConstructor;

@Data
public class UploadArtifactDetails extends AbstractDescribableImpl<UploadArtifactDetails> {
    private String sourcePath;
    private String repositoryId;
    private RepositoryType repositoryType;
    private String version;
    private String artifactPath;

    @DataBoundConstructor
    public UploadArtifactDetails(String sourcePath, RepositoryType repositoryType,
                                 String repositoryId, String version,
                                 String artifactPath) {
        this.artifactPath = artifactPath;
        this.repositoryId = repositoryId;
        this.repositoryType = repositoryType;
        this.version = version;
        this.sourcePath = sourcePath;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<UploadArtifactDetails> {
        private static final String GENERIC = "Generic";
        private static final String EMPTY_STRING = "";

        @Override
        public String getDisplayName() {
            return EMPTY_STRING;
        }

        public ListBoxModel doFillRepositoryTypeItems() {
            ListBoxModel items = new ListBoxModel();
            // Add more repository types here later when supported.
            items.add(GENERIC, RepositoryType.GENERIC.name());
            return items;
        }
    }
}
