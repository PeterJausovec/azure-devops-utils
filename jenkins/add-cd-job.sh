#!/bin/bash

function print_usage() {
  cat <<EOF
Command
  $0
Arguments
  --jenkins_url|-j          [Required]: Jenkins URL
  --git_url|-g              [Required]: Git URL with a Dockerfile in it's root
  --ci_job_name|-cin                  : Existing CI job name
  --cd_job_name|-cdn                  : Desired Jenkins job name for CD
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
    --git_url|-g)
      git_url="$1"
      shift
      ;;
    --ci_job_name|-cin)
      registry="$1"
      shift
      ;;
    --cd_job_name|-cdn)
      registry_user_name="$1"
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

throw_if_empty --jenkins_url $jenkins_url
throw_if_empty --ci_job_name $ci_job_name
throw_if_empty --cd_job_name $cd_job_name
throw_if_empty --git_url $git_url

# Download the CD job definition
cd_job_xml=$(curl -s ${artifacts_location}/jenkins/cd-job.xml)
cd_job_xml=${cd_job_xml//'{insert-ci-job-here}'/${ci_job_name}}
cd_job_xml=${cd_job_xml//'{insert-git-url-here}'/${git_url}}

# Create the job
echo "${cd_job_xml}" > cdjob.xml
cat cdjob.xml | java -jar jenkins-cli.jar -s ${jenkins_url} create-job ${cd_job_name}

# Cleanup
rm cdjob.xml