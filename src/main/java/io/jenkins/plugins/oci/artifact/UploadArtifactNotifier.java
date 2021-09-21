/*
 * Copyright (c) 2021, Oracle and/or its affiliates.  All rights reserved.
 * This software is licensed to you under the Universal Permissive License (UPL) 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */

package io.jenkins.plugins.oci.artifact;

import com.google.common.base.Strings;
import com.oracle.bmc.ClientConfiguration;
import com.oracle.bmc.Region;
import com.oracle.bmc.genericartifactscontent.requests.PutGenericArtifactContentByPathRequest;
import com.oracle.bmc.genericartifactscontent.GenericArtifactsContentClient;
import com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider;
import com.oracle.bmc.retrier.RetryConfiguration;
import com.oracle.bmc.waiter.MaxAttemptsTerminationStrategy;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.Result;
import hudson.model.Item;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.util.ListBoxModel;
import io.jenkins.plugins.oci.messages.Messages.DisplayNames;
import jenkins.tasks.SimpleBuildStep;
import lombok.Data;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.io.File;
import java.io.FileInputStream;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.URLDecoder;
import java.util.List;

import io.jenkins.plugins.oci.utils.CommonUtil;

@Data
public class UploadArtifactNotifier extends Notifier implements SimpleBuildStep {
    private static final String UTF_8_ENCODING = "UTF-8";
    private static final int MAX_ATTEMPTS = 3;
    // TODO: remove this once endpoints are moved to oci.oraclecloud.com
    private static  final String ENDPOINT = "https://generic.{regionId}.ocir.io";

    private String credentialsId;
    private List<UploadArtifactDetails> uploadArtifactDetailsList;

    @DataBoundConstructor
    public UploadArtifactNotifier(String credentialsId, List<UploadArtifactDetails> uploadArtifactDetailsList) {
        this.uploadArtifactDetailsList = uploadArtifactDetailsList;
        this.credentialsId = credentialsId;
    }

    @Override
    public void perform(Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener) throws InterruptedException, IOException {
        try {
            listener.getLogger().println("Starting Artifact Upload");

            listener.getLogger().println("Validating Parameters");
            validateParams();

            SimpleAuthenticationDetailsProvider authProvider = CommonUtil.getAuthProvider(credentialsId);
            GenericArtifactsContentClient client = createArtifactClient(authProvider);

            listener.getLogger().println("Uploading Artifacts To Generic Repo");
            uploadArtifacts(client, uploadArtifactDetailsList, workspace, listener);

            listener.getLogger().println("Artifacts Upload complete");
            run.setResult(Result.SUCCESS);
        } catch (Exception e) {
            listener.getLogger().println(e.getMessage());
            listener.getLogger().println(ExceptionUtils.getStackTrace(e));
            run.setResult(Result.FAILURE);
        }
    }

    GenericArtifactsContentClient createArtifactClient(SimpleAuthenticationDetailsProvider provider) {
        // Set the default retry strategy for Put Generic Artifact operation to MAX_ATTEMPTS
        GenericArtifactsContentClient client = new GenericArtifactsContentClient(provider, ClientConfiguration.builder()
                .retryConfiguration(
                        RetryConfiguration.builder()
                                .terminationStrategy(new MaxAttemptsTerminationStrategy(MAX_ATTEMPTS))
                                .build())
                .build());
        return client;
    }

    void uploadArtifacts(GenericArtifactsContentClient client, List<UploadArtifactDetails> details,
                         FilePath workspace, TaskListener listener ) throws Exception {
        for (UploadArtifactDetails detail : details) {
            FilePath artifactFile = new FilePath(workspace, detail.getSourcePath());
            try (BufferedInputStream artifactContent = new BufferedInputStream(artifactFile.read())) {
                listener.getLogger().println(String.format("Uploading Artifact located at %s", artifactFile.absolutize()));
                listener.getLogger().println(String.format("Artifact Name: %s", artifactFile.getName()));
                listener.getLogger().println(String.format("Artifact Size: %s", artifactFile.length()));

                PutGenericArtifactContentByPathRequest request = PutGenericArtifactContentByPathRequest.builder()
                        .artifactPath(detail.getArtifactPath())
                        .repositoryId(detail.getRepositoryId())
                        .version(detail.getVersion())
                        .genericArtifactContentBody(artifactContent)
                        .build();

                // A user can upload artifacts to different regions. First fetch the region ID from the repository OCID.
                // Set end point for the client before each artifact upload.
                client.setEndpoint(getEndpointFromOCID(detail.getRepositoryId()));
                client.putGenericArtifactContentByPath(request);
            }
        }
    }

    String getEndpointFromOCID(String repositoryOCID) throws Exception {
        return ENDPOINT.replace
                ("{regionId}", Region.fromRegionCode(CommonUtil.parseRegionFromOCID(repositoryOCID))
                        .getRegionId());
    }

    void validateParams() {
        if(Strings.isNullOrEmpty(credentialsId)) {
            throw new IllegalArgumentException("Credentials ID must be specified.");
        }
        if(CollectionUtils.isEmpty(uploadArtifactDetailsList)) {
            throw new IllegalArgumentException("UploadArtifactDetailsList must be specified.");
        }
        for (UploadArtifactDetails details: uploadArtifactDetailsList) {
            if (Strings.isNullOrEmpty(details.getArtifactPath())) {
                throw new IllegalArgumentException("Artifact Path must be specified.");
            }
            if (Strings.isNullOrEmpty(details.getRepositoryId())) {
                throw new IllegalArgumentException("Repository Id must be specified.");
            }
            if (Strings.isNullOrEmpty(details.getSourcePath())) {
                throw new IllegalArgumentException("Source Path must be specified.");
            }
            if (Strings.isNullOrEmpty(details.getVersion())) {
                throw new IllegalArgumentException("Version must be specified.");
            }
        }
    }

    @Symbol("OCIUploadArtifact")
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Item context, @QueryParameter String credentialsId) {
            return CommonUtil.getCredentialsListBoxModel(context, credentialsId);
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return DisplayNames.ARTIFACT_UPLOAD_DISPLAY_NAME;
        }
    }
}
