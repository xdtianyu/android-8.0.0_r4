# VTS Dashboard

## Introduction

The VTS Dashboard displays the summarized results of the Multi Device Tests along with graphs.

## Installation

### Steps to run locally:

1. Google App Engine uses Java 8. Install Java 8 before running running locally:
   'sudo apt install openjdk-8-jdk'

   To use java 8:
   Copy the following lines in ~/.bashrc :

```
    function setup_jdk() {
      # Remove the current JDK from PATH
      if [ -n "$JAVA_HOME" ] ; then
        PATH=${PATH/$JAVA_HOME\/bin:/}
      fi
      export JAVA_HOME=$1
      export PATH=$JAVA_HOME/bin:$PATH
    }

    function use_java8() {
    #  setup_jdk /usr/java/jre1.8.0_73
      setup_jdk /usr/lib/jvm/java-8-openjdk-amd64
    }

    Then from cmd:
    $ use_java8
```

2. Maven is used for build. Install Maven 3.3.9:
   Download maven from:
   https://maven.apache.org/download.cgi

   Steps to Install Maven:
   1) Unzip the Binary tar:
      tar -zxf apache-maven-3.3.3-bin.tar.gz

   2) Move the application directory to /usr/local
      sudo cp -R apache-maven-3.3.3 /usr/local

   3) Make a soft link in /usr/bin for universal access of mvn
      sudo ln -s /usr/local/apache-maven-3.3.3/bin/mvn /usr/bin/mvn

   4) Verify maven installation:
      $ mvn -v

      The output should resemble this:

      Apache Maven 3.3.9 (bb52d8502b132ec0a5a3f4c09453c07478323dc5; 2015-11-10T08:41:47-08:00)
      Maven home: /opt/apache-maven-3.3.9
      Java version: 1.8.0_45-internal, vendor: Oracle Corporation
      Java home: /usr/lib/jvm/java-8-openjdk-amd64/jre
      Default locale: en_US, platform encoding: UTF-8
      OS name: "linux", version: "3.13.0-88-generic", arch: "amd64", family: "unix"

3. Install Google Cloud SDK. Follow the instructions listed on official source:
   https://cloud.google.com/sdk/docs/quickstart-linux

   The default location where the application searches for a google-cloud-sdk is:
   /usr/local/share/google/google-cloud-sdk

   Therefore move the extracted folder to this location: /usr/local/share/google/

   Otherwise, to have a custom location, specify the location of
   google-cloud-sdk in /vts/web/dashboard/appengine/servlet/pom.xml by putting the configuration:

```
   <configuration>
     <gcloud_directory>PATH/TO/GCLOUD_DIRECTORY</gcloud_directory>
   </configuration>
```
   within the 'com.google.appengine' plugin tag :

## To run GAE on local machine:

$ cd web/dashboard/appengine/servlet
$ mvn clean gcloud:run

## To deploy to Google App Engine

$ cd web/dashboard/appengine/servlet
$ mvn clean gcloud:deploy

visit https://<YOUR-PROJECT-NAME>.appspot.com

## Monitoring

The following steps list how to create a monitoring service for the VTS Dashboard.

### Create a Stackdriver account

1. Go to Google Cloud Platform Console:
   http://console.developers.google.com

2. In the Google Cloud Platform Console, select Stackdriver > Monitoring.
   If your project is not in a Stackdriver account you'll see a message to
   create a new project.

3. Click Create new Stackdriver account and then Continue.

4. With your project shown, click Create account.

5. In the page, "Add Google Cloud Platform projects to monitor", click Continue to skip ahead.

6. In the page, "Monitor AWS accounts", click Done to skip ahead.

7. In a few seconds you see the following message:
   "Finished Initial collection"
   Click Launch Monitoring.

8. In the page, "Get reports by email", click No reports and Continue.

9. You will see your Stackdriver account dashboard.
   Close the "Welcome to Stackdriver" banner if you don't need it.

### Steps to create an uptime check and an alerting policy

1. Go to Stack Monitoring console:
   https://app.google.stackdriver.com/

2. Go to Alerting > Uptime Checks in the top menu and then click Add Uptime Check.
   You see the New Uptime Check panel.

3. Fill in the following fields for the uptime check:

    Check type: HTTP
    Resource Type: Instance
    Applies To: Single, lamp-1-vm
    Leave the other fields with their default values.

4. Click Test to verify your uptime check is working.

5. Click Save. After you click on save you'll see a panel to
   'Create Alerting Policy'

6. Fill out the configuration for notifications and click save policy.

### Test the check and alert

This procedure can take up to fifteen minutes.

To test the check and alert, go to the VM Instances page, select your instance, and click Stop from the top menu.
You'll have to wait up to five minutes for the next uptime check to fail. The alert and notification don't happen until the next failure occurs.

To correct the "problem," return to the VM Instances page, select your instance, and click Start from the top menu.
