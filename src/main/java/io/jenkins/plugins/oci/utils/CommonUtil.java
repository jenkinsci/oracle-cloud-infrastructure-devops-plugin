/*
 * Copyright (c) 2021, Oracle and/or its affiliates.  All rights reserved.
 * This software is licensed to you under the Universal Permissive License (UPL) 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */

package io.jenkins.plugins.oci.utils;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.oracle.bmc.Region;
import com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider;
import com.oracle.bmc.model.BmcException;

import com.oracle.bmc.devops.model.Deployment;
import hudson.EnvVars;
import hudson.model.Item;
import hudson.security.ACL;
import hudson.util.ListBoxModel;
import io.jenkins.plugins.oci.credentials.CloudCredentials;
import io.jenkins.plugins.oci.deployment.ExecutionMode;
import java.util.Locale;
import jenkins.model.Jenkins;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.QueryParameter;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.cloudbees.plugins.credentials.CredentialsMatchers.anyOf;
import static com.cloudbees.plugins.credentials.CredentialsMatchers.instanceOf;

@UtilityClass
public final class CommonUtil {
    private static final String VARIABLE_PATTERN_REGEX = "(?<!\\\\)\\$\\{(\\w*?)\\}\\s?";
    private static final Pattern VARIABLE_PATTERN = Pattern.compile(VARIABLE_PATTERN_REGEX);
    private static final int ENTITY_TYPE_MAX_LENGTH = 32;
    private static final int REALM_MAX_LENGTH = 15;
    private static final int REGION_MAX_LENGTH = 24;
    private static final int MAX_PARTS = 5;
    private static final int MAX_OCID_LENGTH = 255;
    private static final String OCID_COMMON_PARTS_PATTERN = "^[a-z][a-z0-9-_]*[a-z0-9]+$";
    private static final String OCID_OTHER_PARTS_PATTERN = "^[a-zA-Z0-9-_]*$";

    public static ListBoxModel getCredentialsListBoxModel(@AncestorInPath Item context, @QueryParameter String credentialsId) {
        StandardListBoxModel result = new StandardListBoxModel();
        Jenkins instance = Jenkins.get();
        if (context != null && instance != null ) {
            if (!context.hasPermission(Item.EXTENDED_READ) && !context.hasPermission(CredentialsProvider.USE_ITEM)) {
                return result.includeCurrentValue(credentialsId);
            }
        } else {
            if (instance != null && !instance.hasPermission(Jenkins.ADMINISTER)) {
                return result.includeCurrentValue(credentialsId);
            }
        }
        List<DomainRequirement> domainRequirements = new ArrayList<>();
        return result.includeMatchingAs(ACL.SYSTEM, context, CloudCredentials.class, domainRequirements, anyOf(instanceOf(CloudCredentials.class)));
    }

    public static Map<String, String> parseJsonToMap(String argValue) throws Exception {
        Map<String, String> argMap;
        ObjectMapper mapper = new ObjectMapper();
        try {
            argMap = mapper.readValue(argValue, new TypeReference<Map<String, String>>() {});
        } catch (MismatchedInputException ex) {
            throw new Exception("Deserialization error- Json keys and values must be string");
        } catch (JsonProcessingException e) {
            throw new Exception("Error parsing json");
        }
        return argMap;
    }

    public static SimpleAuthenticationDetailsProvider getAuthProvider(String credentialsId) throws Exception {
        List<CloudCredentials> credList = CredentialsProvider.lookupCredentials(
                CloudCredentials.class,
                Jenkins.get(),
                ACL.SYSTEM,
                Collections.<DomainRequirement>emptyList());

        CloudCredentials cloudCredentials =
                credList.stream().filter(c -> c.getId().equals(credentialsId)).findFirst().orElseThrow(() -> new Exception("This credentialId couldn't be found."));
        return SimpleAuthenticationDetailsProvider.builder()
                .fingerprint(cloudCredentials.getFingerprint())
                .passPhrase(cloudCredentials.getPassphrase())
                .privateKeySupplier(() -> new ByteArrayInputStream(cloudCredentials.getApikey().getBytes(StandardCharsets.UTF_8)))
                .tenantId(cloudCredentials.getTenantId())
                .userId(cloudCredentials.getUserId())
                .region(Region.fromRegionId(cloudCredentials.getRegionId()))
                .build();
    }

    public static String substituteValues(String argumentVal, EnvVars vars) throws Exception {
        Matcher matcher = VARIABLE_PATTERN.matcher(argumentVal);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            if (vars.containsKey(key)) {
                String value = vars.get(key);
                matcher.appendReplacement(sb, value);
            }
        }
        matcher.appendTail(sb);
        String substitutedStr = sb.toString();
        substitutedStr = substitutedStr.replace("\\\\$", "$");
        return substitutedStr;
    }

    public static  boolean retryableException(Throwable th) {
        if (th instanceof BmcException) {
            return ((BmcException) th).getStatusCode() != 404;
        }
        return true;
    }

    public static boolean retryableLifeCycleState(Deployment deployment, ExecutionMode executionMode) {
        if (executionMode.equals(ExecutionMode.SYNC)) {
            return !deployment.getLifecycleState().equals(Deployment.LifecycleState.Succeeded) &&
                    !deployment.getLifecycleState().equals(Deployment.LifecycleState.Failed);
        }
        return !deployment.getLifecycleState().equals(Deployment.LifecycleState.Succeeded) &&
                !deployment.getLifecycleState().equals(Deployment.LifecycleState.Failed) &&
                !deployment.getLifecycleState().equals(Deployment.LifecycleState.InProgress);
    }

    public static String parseRegionFromOCID(String possibleOcid) throws IllegalArgumentException {
        if (possibleOcid == null) {
            throw new NullPointerException("possibleOcid");
        }
        if (possibleOcid.length() > MAX_OCID_LENGTH) {
            throw new IllegalArgumentException("OCID is too long");
        }
        String[] parts = possibleOcid.split("[.:]");
        if (parts.length < MAX_PARTS) {
            throw new IllegalArgumentException("OCID has too few parts");
        }
        if (StringUtils.isBlank(parts[0])) {
            throw new IllegalArgumentException("OCID has missing version");
        }
        if (!"ocidv1".equals(parts[0].toLowerCase(Locale.ENGLISH)) && !"ocid1".equals(parts[0].toLowerCase(Locale.ENGLISH))) {
            throw new IllegalArgumentException("OCID has invalid version");
        }
        if (StringUtils.isBlank(parts[1])) {
            throw new IllegalArgumentException("OCID has missing entity type");
        }
        if (!Pattern.matches(OCID_COMMON_PARTS_PATTERN, parts[1].toLowerCase(Locale.ENGLISH)) || parts[1].length() > ENTITY_TYPE_MAX_LENGTH) {
            throw new IllegalArgumentException("OCID has invalid entity type");
        }
        if (StringUtils.isBlank(parts[2])) {
            throw new IllegalArgumentException("OCID has missing realm");
        }
        if (!Pattern.matches(OCID_COMMON_PARTS_PATTERN, parts[2].toLowerCase(Locale.ENGLISH)) || parts[2].length() > REALM_MAX_LENGTH) {
            throw new IllegalArgumentException("OCID has invalid realm");
        }
        if (StringUtils.isNotBlank(parts[3]) && (!Pattern.matches(OCID_COMMON_PARTS_PATTERN, parts[3].toLowerCase(Locale.ENGLISH))
                || parts[3].length() > REGION_MAX_LENGTH)) {
            throw new IllegalArgumentException("OCID has invalid region");
        }
        for(int i = 4; i < parts.length; ++i) {
            if (!Pattern.matches(OCID_OTHER_PARTS_PATTERN, parts[i])) {
                throw new IllegalArgumentException("OCID has invalid part " + i);
            }
        }
        return parts[3];
    }
}