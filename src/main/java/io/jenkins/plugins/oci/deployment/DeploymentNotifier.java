/*
 * Copyright (c) 2021, Oracle and/or its affiliates.  All rights reserved.
 * This software is licensed to you under the Universal Permissive License (UPL) 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */

package io.jenkins.plugins.oci.deployment;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.oracle.bmc.auth.AuthenticationDetailsProvider;
import com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider;
import com.oracle.bmc.devops.DevopsClient;
import com.oracle.bmc.model.BmcException;
import com.oracle.bmc.devops.model.CreateDeployPipelineDeploymentDetails;
import com.oracle.bmc.devops.model.Deployment;
import com.oracle.bmc.devops.model.Deployment.LifecycleState;
import com.oracle.bmc.devops.model.DeploymentArgument;
import com.oracle.bmc.devops.model.DeploymentArgumentCollection;
import com.oracle.bmc.devops.requests.CreateDeploymentRequest;
import com.oracle.bmc.devops.requests.GetDeploymentRequest;
import com.oracle.bmc.devops.responses.CreateDeploymentResponse;
import hudson.Launcher;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.Item;
import hudson.model.Result;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.util.ListBoxModel;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.decorators.Decorators;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.jenkins.plugins.oci.deployment.polling.PollingConfig;
import io.jenkins.plugins.oci.messages.Messages.DisplayNames;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import io.jenkins.plugins.oci.utils.CommonUtil;
import lombok.Getter;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import org.kohsuke.stapler.DataBoundSetter;
import java.io.IOException;
import jenkins.tasks.SimpleBuildStep;
import org.jenkinsci.Symbol;

@Getter
public class DeploymentNotifier extends Notifier implements SimpleBuildStep {
    private String credentialsId;
    private String pipelineId;
    private String displayName;
    private String endpoint;
    private String argumentVal;
    private PollingConfig pollingConfig;
    private ExecutionMode executionMode;
    private static final long MIN_POLLING_INTERVAL_SECONDS = 5;
    private static final long MIN_TIMEOUT_SECONDS = 10;

    @DataBoundConstructor
    public DeploymentNotifier(String credentialsId,
            String pipelineId,
            String displayName,
            String endpoint,
            String argumentVal,
            PollingConfig pollingConfig,
            ExecutionMode executionMode) {
        this.credentialsId = credentialsId;
        this.pipelineId = pipelineId;
        this.displayName = displayName;
        this.endpoint = endpoint;
        this.argumentVal = argumentVal;
        if (pollingConfig != null) {
            this.pollingConfig = pollingConfig;
        } else {
            this.pollingConfig = new PollingConfig();
        }
        this.executionMode = executionMode;
    }

    @DataBoundSetter
    public void setCredentialsId(String credentialsId){
        this.credentialsId = credentialsId;
    }
    @DataBoundSetter
    public void setPipelineId(String pipelineId){
        this.pipelineId = pipelineId;
    }
    @DataBoundSetter
    public void setDisplayName(String displayName){
        this.displayName = displayName;
    }
    @DataBoundSetter
    public void setEndpoint(String endpoint){
        this.endpoint = endpoint;
    }
    @DataBoundSetter
    public void setArgumentVal(String argumentVal) {
        this.argumentVal = argumentVal;
    }
    @DataBoundSetter
    public void setExecutionMode(ExecutionMode executionMode) {
        if (executionMode != null) {
            this.executionMode = executionMode;
        }
    }

    @Override
    public void perform(Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener) throws InterruptedException, IOException {
        try {
            listener.getLogger().println("Execution Mode:" + executionMode);
            listener.getLogger().println("ExecutionMode max timeout:" + pollingConfig.getTimeoutSeconds());
            listener.getLogger().println("ExecutionMode polling interval:" + pollingConfig.getPollingIntervalSeconds());
            validateParameters();
            DeploymentArgumentCollection deploymentArgumentCollection = null;
            if (!Strings.isNullOrEmpty(argumentVal)) {
                String substitutedStr = CommonUtil.substituteValues(argumentVal, run.getEnvironment(listener));
                Map<String, String> substitutedMap = CommonUtil.parseJsonToMap(substitutedStr);
                listener.getLogger().println("Argument after substitution: " + substitutedMap);
                deploymentArgumentCollection = createDeploymentArgumentCollection(substitutedMap);
            }
            final SimpleAuthenticationDetailsProvider authProvider = newAuthenticationDetailsProvider(credentialsId);
            listener.getLogger().println("Created Auth provider");
            DevopsClient client = newDeploymentClient(authProvider);
            listener.getLogger().println("Created Deployment Client");
            client.setEndpoint(endpoint);
            CreateDeploymentResponse createDeploymentResponse = createDeployment(deploymentArgumentCollection, client);
            Deployment deployment = createDeploymentResponse.getDeployment();
            listener.getLogger().println("Response received from createDeployment API is: " + deployment);
            if (deployment.getLifecycleState().equals(LifecycleState.Accepted) || deployment.getLifecycleState().equals(LifecycleState.InProgress)) {
                deployment = waitForDeploymentCompletion(deployment.getId(), client, executionMode, pollingConfig);
            }
            listener.getLogger().println("The deployment is completed:" + deployment);
            if (executionMode.equals(ExecutionMode.SYNC)) {
                if (deployment.getLifecycleState().equals(LifecycleState.Succeeded)) {
                    run.setResult(Result.SUCCESS);
                } else {
                    run.setResult(Result.FAILURE);
                }
            } else {
                /* It first waits for sometime (as per polling config) for deployment to go from Accepted to inProgress
                 or Succeeded state. But while testing we have seen cases that even after waiting for some time the Deployment
                 was still in ACCEPTED state and the jenkins job was marked as Failure. So I have added another condition
                 that if Deployment is in Async mode and even after waiting its either in InProgress, Succeeded
                 or Accepted state, mark it as success.*/
                if (deployment.getLifecycleState().equals(LifecycleState.InProgress) ||
                        deployment.getLifecycleState().equals(LifecycleState.Succeeded) ||
                        deployment.getLifecycleState().equals(LifecycleState.Accepted)) {
                    run.setResult(Result.SUCCESS);
                } else {
                    run.setResult(Result.FAILURE);
                }
            }
        } catch (Exception ex) {
            listener.getLogger().println(ex.getMessage());
            run.setResult(Result.FAILURE);
        }
    }

    SimpleAuthenticationDetailsProvider newAuthenticationDetailsProvider(final String credentialsId) throws Exception {
        return CommonUtil.getAuthProvider(credentialsId);
    }

    void validateParameters() {
        if (Strings.isNullOrEmpty(credentialsId)) {
            throw new IllegalArgumentException("CredentialId must be specified.");
        }
        if (Strings.isNullOrEmpty(pipelineId)) {
            throw new IllegalArgumentException("PipelineId must be specified.");
        }
        if (Strings.isNullOrEmpty(displayName)) {
            throw new IllegalArgumentException("Display Name must be specified.");
        }
        if (Strings.isNullOrEmpty(endpoint)) {
            throw new IllegalArgumentException("Endpoint must be specified.");
        }
        if (executionMode == null) {
            throw new IllegalArgumentException("Execution Mode must be specified.");
        }
        if (!pollingConfig.getIsValid()) {
            throw new IllegalArgumentException("Timeout and polling interval must be integers");
        }
        if (pollingConfig.getTimeoutSeconds() < MIN_TIMEOUT_SECONDS) {
            throw new IllegalArgumentException("Timeout in seconds must be greater than " + MIN_TIMEOUT_SECONDS);
        }
        if (pollingConfig.getPollingIntervalSeconds() < MIN_POLLING_INTERVAL_SECONDS) {
            throw new IllegalArgumentException("Polling interval in seconds must be greater than " + MIN_POLLING_INTERVAL_SECONDS);
        }
        if (pollingConfig.getPollingIntervalSeconds() > pollingConfig.getTimeoutSeconds()) {
            throw new IllegalArgumentException("Timeout must be greater than polling interval");
        }
    }

    DevopsClient newDeploymentClient(AuthenticationDetailsProvider authProvider) {
        return new DevopsClient(authProvider);
    }

    Deployment waitForDeploymentCompletion(String deploymentId, DevopsClient client, ExecutionMode executionMode, PollingConfig pollingConfig) {
        GetDeploymentRequest getDeploymentRequest = GetDeploymentRequest.builder()
                .deploymentId(deploymentId)
                .build();
        long interval = pollingConfig.getPollingIntervalSeconds();
        long timeout = pollingConfig.getTimeoutSeconds();
        RetryConfig config = newRetryConfig(executionMode, timeout, interval);
        Retry retryCustom = Retry.of("checkDeploymentState", config);
        Supplier<Deployment> supplier = () -> client.getDeployment(getDeploymentRequest).getDeployment();
        Supplier<Deployment> decoratedSupplier = Decorators.ofSupplier(supplier).withRetry(retryCustom).decorate();
        return decoratedSupplier.get();
    }

    @VisibleForTesting
    RetryConfig newRetryConfig(ExecutionMode executionMode, long timeout, long interval) {
        return RetryConfig.<Deployment>custom().maxAttempts(Math.toIntExact(timeout/interval))
                .retryOnResult((deployment) -> CommonUtil.retryableLifeCycleState(deployment, executionMode))
                .retryOnException(ex -> CommonUtil.retryableException(ex))
                .intervalFunction(IntervalFunction.of(interval * 1000))
                .build();
    }

    CreateDeploymentResponse createDeployment(DeploymentArgumentCollection deploymentArgumentCollection, DevopsClient client) {
        CreateDeploymentResponse createDeploymentResponse;
        try {
            CreateDeployPipelineDeploymentDetails createPipelineDeploymentDetails = CreateDeployPipelineDeploymentDetails.builder()
                    .deploymentArguments(deploymentArgumentCollection)
                    .displayName(displayName)
                    .deployPipelineId(pipelineId)
                    .build();
            CreateDeploymentRequest createDeploymentRequest = CreateDeploymentRequest.builder()
                    .createDeploymentDetails(createPipelineDeploymentDetails)
                    .build();
            createDeploymentResponse = client.createDeployment(createDeploymentRequest);
        } catch (BmcException ex) {
            throw ex;
        } catch (Exception ex) {
            throw ex;
        }
        return createDeploymentResponse;
    }

    DeploymentArgumentCollection createDeploymentArgumentCollection(Map<String, String> map) {
        List<DeploymentArgument> argList = new ArrayList<>();
        for (Map.Entry<String, String> entry: map.entrySet())
            argList.add(DeploymentArgument.builder()
                    .name(entry.getKey())
                    .value(entry.getValue())
                    .build());
        return DeploymentArgumentCollection.builder().items(argList).build();
    }

    @Symbol("OCIDeployment")
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
            return DisplayNames.DEPLOYMENT_DISPLAY_NAME;
        }
    }

}