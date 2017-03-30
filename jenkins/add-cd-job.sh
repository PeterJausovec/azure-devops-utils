#!/bin/bash

function print_usage() {
  cat <<EOF
Command
  $0
Arguments
  --jenkins_url|-j          [Required]: Jenkins URL
  --jenkins_user_name|-ju   [Required]: Jenkins user name
  --jenkins_password|-jp              : Jenkins password. If not specified and the user name is "admin", the initialAdminPassword will be used
  --cd_job_name|-cdn                  : Desired Jenkins job name for CD
  --cd_job_display_name|-cddn         : Desired Jenkins job display name for CD
  --artifacts_location|-al            : Url used to reference other scripts/artifacts.
EOF
}

function throw_if_empty() {
  local name="$1"
  local value="$2"
  if [ -z "$value" ]; then
    echo "Parameter '$name' cannot be empty." 1>&2
    print_usage
    exit -1
  fi
}

# Defaults
artifacts_location="https://raw.githubusercontent.com/PeterJausovec/azure-devops-utils/master"

while [[ $# > 0 ]]
do
  key="$1"
  shift
  case $key in
    --jenkins_url|-j)
      jenkins_url="$1"
      shift
      ;;
    --jenkins_user_name|-ju)
      jenkins_user_name="$1"
      shift
      ;;
    --jenkins_password|-jp)
      jenkins_password="$1"
      shift
      ;;
    --cd_job_name|-cdn)
      cd_job_name="$1"
      shift
      ;;
    --cd_job_display_name|-cddn)
      cd_job_display_name="$1"
      shift
      ;;
    --artifacts_location|-al)
      artifacts_location="$1"
      shift
      ;;
    --help|-help|-h)
      print_usage
      exit 13
      ;;
    *)
      echo "ERROR: Unknown argument '$key' to script '$0'" 1>&2
      exit -1
  esac
done

function retry_until_successful {
    counter=0
    "${@}"
    while [ $? -ne 0 ]; do
        if [[ "$counter" -gt 20 ]]; then
            exit 1
        else
            let counter++
        fi
        sleep 5
        "${@}"
    done;
}

throw_if_empty --jenkins_url $jenkins_url
throw_if_empty --cd_job_name $cd_job_name
throw_if_empty --jenkins_user_name $jenkins_user_name
if [ "$jenkins_user_name" != "admin" ]; then
  throw_if_empty --jenkins_password $jenkins_password
fi

# Download jenkins cli (wait for Jenkins to be online)
retry_until_successful wget ${jenkins_url}/jnlpJars/jenkins-cli.jar -O jenkins-cli.jar

# Download the CD job definition
cd_job_xml=$(curl -s ${artifacts_location}/jenkins/cd-job.xml)
cd_job_xml=${cd_job_xml//'{insert-cd-job-display-name}'/${cd_job_display_name}}
cd_job_xml=${cd_job_xml//'{insert-groovy-script}'/"$(curl -s ${artifacts_location}/jenkins/cd-pipeline.groovy)"}

if [ -z "$jenkins_password" ]; then
  # NOTE: Intentionally setting this after the first retry_until_successful to ensure the initialAdminPassword file exists
  jenkins_password=`sudo cat /var/lib/jenkins/secrets/initialAdminPassword`
fi


# Check if /var/lib/jenkins/config.xml contains <useSecurity>false</useSecurity>
str_to_check="<useSecurity>false</useSecurity>"

username_password_string="--username ${jenkins_user_name} --password ${jenkins_password}"
if grep -q ${str_to_check} "/var/lib/jenkins/config.xml"; then
  echo "Jenkins is unsecured, not using username/password"
  # Jenkins is unsecured - no need to pass username and password 
  username_password_string=""
fi

# Create the job
echo "${cd_job_xml}" > cdjob.xml
retry_until_successful cat cdjob.xml | java -jar jenkins-cli.jar -s ${jenkins_url} create-job ${cd_job_name} ${username_password_string}

# Cleanup
rm cdjob.xml
rm jenkins-cli.jar