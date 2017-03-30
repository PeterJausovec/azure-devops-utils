#!/bin/bash
# Check if /var/lib/jenkins/config.xml contains <useSecurity>false</useSecurity>
str_to_check="<useSecurity>false</useSecurity>"

if grep -q ${str_to_check} "/var/lib/jenkins/config.xml"; then
  echo "Jenkins is unsecured, nothing to do"
else
  sudo echo "1" > /var/lib/jenkins/jenkins.install.InstallUtil.lastExecVersion

  unsecure_config_xml=$(sed -zr \
      -e "s|<useSecurity>.*</useSecurity>|<useSecurity>false</useSecurity>|"\
      -e "s|<authorizationStrategy.*</authorizationStrategy>||"\
      -e "s|<securityRealm.*</securityRealm>||"\
    /var/lib/jenkins/config.xml)

  echo "${unsecure_config_xml}" > /var/lib/jenkins/config.xml

  sudo service jenkins restart
fi
