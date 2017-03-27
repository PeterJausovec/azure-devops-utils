#!/bin/bash

# Defaults (read these from arguments)
jenkins_url="http://127.0.0.1:8080"
cd_job_name="servicea-CD"
artifacts_location="https://raw.githubusercontent.com/PeterJausovec/azure-devops-utils/master"
ci_job_name="servicea-CI"
git_url="https://github.com/peterjausovec/servicea"

cd_job_xml=$(curl -s ${artifacts_location}/jenkins/cd-job.xml)

cd_job_xml=${cd_job_xml//'{insert-ci-job-here}'/${ci_job_name}}
cd_job_xml=${cd_job_xml//'{insert-git-url-here}'/${git_url}}

echo "${cd_job_xml}" > cdjob.xml
cat cdjob.xml | java -jar jenkins-cli.jar -s ${jenkins_url} create-job ${cd_job_name} 