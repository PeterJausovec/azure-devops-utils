node {
    def built_img = ''
    stage('Checkout git repo') {
      git branch: 'master', url: params.git_repo
    }
    stage('Build Docker image') {
      built_img = docker.build(params.docker_repository + ":${env.BUILD_NUMBER}", '.')
    }
    stage('Push Docker image to Azure Container Registry') {
      docker.withRegistry(params.registry_url, params.registry_credentials_id ) {
        built_img.push("${env.BUILD_NUMBER}");
      }
    }
    stage('Kick off CD') {
      registry = params.registry_url.replace("https://", "");
      build job: params.cd_job_name, parameters: [string(name: 'REGISTRY_URL', value:  registry), string(name: 'IMAGE_NAME', value: params.docker_repository), string(name: 'IMAGE_TAG', value: "${env.BUILD_NUMBER}"), string(name: 'SERVICE_NAME', value: params.service_name)]
    }
}