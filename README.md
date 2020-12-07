**Oracle Cloud Infrastructure DevOps Plugin** can be used to upload artifacts, and run deployments on the Oracle Cloud Infrastructure (OCI) from Jenkins.
A Jenkins master instance with Oracle Cloud Infrastructure DevOps plugin can upload the artifacts to the Artifact Registry repository, and can trigger the deployment pipeline for those artifacts.  

## Table of Contents

- [Features](#features)
- [Prerequisites](#prerequisites)
- [Compatibility](#compatibility)
- [Installation](#installation)
- [Build](#build)
- [Upgrade](#upgrade)
- [Configuration](#configuration)
- [Licensing](#licensing)
- [Changelog](#changelog)
- [Contribution](#contribution)

## Features

**Oracle Cloud Infrastructure DevOps Plugin** can be used to upload generic artifacts and create deployment on the Oracle Cloud Infrastructure (OCI) from Jenkins Jobs.

For more information, see Oracle Cloud Infrastructure DevOps plugin page on the [plugins.jenkins.io](https://plugins.jenkins.io/oracle-cloud-infrastructure-deployment) website.

## Prerequisites

1. Oracle Cloud Account. To sign up, visit [Oracle Cloud](https://cloud.oracle.com/en_US/tryit).

2. Jenkins installed with JDK 8 or higher.

3. Required plugins: [bouncycastle API](https://plugins.jenkins.io/bouncycastle-api), [SSH Credentials](https://plugins.jenkins.io/ssh-credentials/) and [Credentials](https://plugins.jenkins.io/credentials).

## Compatibility

Minimum Jenkins requirement: *2.204*

## Installation

There are a number of ways to install the Oracle Cloud Infrastructure DevOps plugin:

- Using the Plugin Manager in the web UI.
- Using the Jenkins CLI install-plugin command.
- Copying the .hpi file to the JENKINS_HOME/plugins directory.

### Using the Plugin Manager

The simplest and most common way of installing plugins is through the Manage Plugins view, available to administrators of a Jenkins environment.

To install the plugin in Jenkins:

1. Click **Manage Jenkins** on the home page.
2. Click **Manage Plugins**.
3. Click **Available** tab.
4. Search for **Oracle Cloud Infrastructure DevOps Plugin** or **oracle-cloud-infrastructure-devops**.
5. Click **Install**.
6. Restart Jenkins.

### Using the Jenkins CLI

Administrators may also use the [Jenkins CLI](https://jenkins.io/doc/book/managing/cli/), which provides a command to install plugins.

 java -jar jenkins-cli.jar -s <http://localhost:8080/> install-plugin SOURCE ... [-deploy] [-name VAL] [-restart]

 Installs a plugin either from a file, an URL, or from an update center.

  SOURCE    : If this points to a local file that file will be installed. If
            this is an URL, Jenkins downloads the URL and installs that as a
            plugin. Otherwise the name is assumed to be the short name of the
            plugin i.e. "oracle-cloud-infrastructure-devops", and the
            plugin will be installed from the update center.
  -deploy   : Deploys plugins right away without postponing them until the reboot.
  -name VAL : If specified, the plugin will be installed as the short name
            (usually the name is inferred from the source name
            automatically).
  -restart  : Restart Jenkins upon successful installation.

Link to latest .hpi version can be found in the [Jenkins Update Site](https://updates.jenkins.io/latest/oracle-cloud-infrastructure-devops.hpi).

### Copying the .hpi file to the plugin directory

Using the .hpi file that has been explicitly downloaded by a systems administrator, the administrator can manually copy the downloaded .hpi file into the JENKINS_HOME/plugins directory on the Jenkins master.
Link to the latest .hpi version can be found in the [Jenkins Update Site](https://updates.jenkins.io/latest/oracle-cloud-infrastructure-devops.hpi).

The Jenkins master will need to be restarted before the plugin is loaded and made available in the Jenkins environment.

## Build

Jenkins plugins are packaged as self-contained .hpi files, which have all the necessary code, and other resources that the plugin needs to operate successfully. 

If required you can build the Oracle Cloud Infrastructure DevOps Plugin .hpi from the source code, and then install the .hpi file in Jenkins.

To build the .hpi file, OCI Java SDK is required and is available on [Maven Central](https://search.maven.org/search?q=g:com.oracle.oci.sdk) and [JCenter](https://bintray.com/oracle/jars/oci-java-sdk).

Refer to OCI Java SDK licensing in the [OCI Java SDK GitHub repository](https://github.com/oracle/oci-java-sdk/blob/master/LICENSE.txt).

### Compile the Plugin

1. Clone the git repo.

2. Update pom.xml if you want to use the latest version of the OCI Java SDK, 

   > <oci-java-sdk.version>1.29.0</oci-java-sdk.version>

3. Compile and install the package,

   > $ mvn package

### Install the Plugin

A logged-in Jenkins administrator may upload the file from within the web UI.

1. Navigate to the Manage Jenkins > Manage Plugins page in the web UI.
1. Click the Advanced tab.
1. Choose the .hpi file under the Upload Plugin section.
1. Click Upload.

**or**

The System Administrator can copy the .hpi file into the JENKINS_HOME/plugins directory on the Jenkins master.
The master will need to be restarted before the plugin is loaded, and made available in the Jenkins environment.

## Upgrade

Updates are listed in the Updates tab of the **Manage Plugins** page and can be installed by checking the checkbox of the Oracle Cloud Infrastructure DevOps plugin updates, and clicking the **Download now and install after restart** button.

**Note**:  Upgrading the Plugin may require you to update your already created OCI Cloud and Templates configuration. After upgrade, please verify if all the OCI Cloud values are OK in the location, Manage Jenkins > Manage Nodes, and Clouds > Configure Clouds. Then click **Save**.

For example, a new method of adding OCI Credentials was added in v106 of the OCI Compute plugin. Previously, these OCI Credentials were added in the OCI Cloud configuration. If upgrading from a version earlier than v106, then you may have to update the values in your existing Cloud configuration.

**Note**: A plugin version with new functionality may only take effect on Slaves built with that particular new version. You have to remove older Slaves.

## Configuration

### Add OCI Credentials

Oracle Cloud Infrastructure Credentials are required to connect to your Oracle Cloud Infrastructure. For more information on OCI Credentials and other required keys, see [Security Credentials](https://docs.cloud.oracle.com/iaas/Content/General/Concepts/credentials.htm).

You can add these OCI Credentials by navigating to the Jenkins Server console, Credentials, System,  and **Add Credentials**,

Once in the New Credentials screen, select **Oracle Cloud Infrastructure Credentials** from the **Kind** drop-down.

- **Fingerprint** - The Fingerprint for the key pair being used.
- **API Key** - The OCI API Signing Private Key.
- **PassPhrase** - The PassPhrase for the key pair being used.
- **Tenant Id** - The Tenant OCID.
- **User Id** - The OCID of the User whose API signing key you are using.
- **Region** - The OCI region to use for all OCI API requests for example, us-phoenix-1.
- **ID** - An internal unique ID by which these credentials are identified from jobs, and other configuration.
- **Description** - An optional description to differentiate between similar credentials.

Click **Verify Credentials** to successfully connect to your Oracle Cloud Infrastructure.

### Using Post Build Actions

After setting up the OCI credentials, some simple configuration is needed for your project.

#### OCI Artifact Upload

1. Open up your project configuration
2. In the Post-build Actions section, select OCI Artifact Upload.
User has to specify the following details for each of the artifacts:
    1. Source Path : Source path of the artifact relative to the workspace.
    2. Repository OCID : OCID of the artifact Service repository. Make sure that the repo type is appropriate for the type of artifact being uploaded. For example, do not try to upload a container image to a maven repository.
    3. Artifact Version: Make sure that version is unique in the repo if the repo is immutable. If the repo is immutable, generate unique version for each of the generated artifacts for each build of the Jenkins pipeline.
    4. Artifact Path : Specify a path for the artifact which will be used while placing the artifact in the repository.
    5. Click Add button, to add multiple artifacts to be uploaded.

##### Usage Example for Artifact Upload

Add this stage to Jenkins pipeline to upload artifacts to generic artifact service

    stage("OCI Upload Artifact") {  
             steps {  
                OCIUploadArtifact(credentialsId: 'dlctest', uploadArtifactDetailsList: [[artifactPath: 'artifacts.zip', repositoryId: 'ocid1.artifactrepository.oc1.iad.0.amaaaaaansx72maa7qtvx6szocqxrpcwvbv2etzzqlid7qrlmmcxqehjwwnq', repositoryType: 'GENERIC', sourcePath: 'artifacts.zip', version: "1.$BUILD_NUMBER"]])  
            }  
        }
  
#### OCI Deployment

1. Open up your project configuration
2. In the Post-build Actions section, to upload an artifact to OCI Generic repo, select OCI Deployment.
3. Credentials, pipeline OCID, display name, wait for deployment (Sync/Async) and endpoint are all required options. You can choose to pass arguments (as json string if using pipeline job or as json filepath if using freestyle job)
which will be used to create the deployment.
You can choose polling configuration to poll for the status of the deployment.

##### Usage Example for Triggering Deployment

Add this stage to Jenkins pipeline to trigger a deployment

    stage('Trigger Deployment') {
                    steps {
                        OCIDeployment(credentialsId: 'dlctest', displayName: 'testDeployment', endpoint: 'https://cloud-deploy.us-ashburn-1.oci.oc-test.com', pipelineId: 'ocid1.clouddeploypipeline.oc1.iad.amaaaaaansx72maayxy22tfkefsme2umuzjs73lvo7yplmnwmdesjfdn6j5a', pollingConfig: [timeoutSeconds: 10, "pollingIntervalSeconds" : 5], executionMode: 'ASYNC')
                }
            }

## Licensing

Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
This code is offered under the Universal Permissive License (UPL) 1.0. For more information, refer to the [LICENSE.txt](https://github.com/oracle/oci-devops-jenkins-plugin/blob/master/LICENSE.txt) file.
This plugin also contains other third-party code, under separate licenses identified in the THIRD_PARTY_LICENSES.txt file.

## Changelog

For CHANGELOG please refer to [CHANGELOG.md](https://github.com/oracle/oci-devops-jenkins-plugin/blob/master/CHANGELOG.md).

## Contribution

Oracle Cloud Infrastructure DevOps Plugin is an open source project. For more details, see [CONTRIBUTING.md](https://github.com/oracle/oci-devops-jenkins-plugin/blob/master/CONTRIBUTING.md).

Oracle gratefully acknowledges the contributions to the Oracle Cloud Infrastructure DevOps Plugin that have been made by the community.
