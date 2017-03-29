
pipeline {
    agent any
    parameters {
        string(name:'REGISTRY_URL', description: 'docker image repository')
        string(name:'IMAGE_NAME', description: 'image name')
        string(name:'IMAGE_TAG', description: 'image tag')
        // TODO: create a bikesharing namespace
        string(name:'SERVICE_NAME', description: 'Service name that is being deployed')
    }
    environment {
        KUBECONFIG = '/var/lib/jenkins/.kube/config'
    }
    stages {
        stage ('Deploy') {
            steps {
                sh 'kubectl apply -f https://raw.githubusercontent.com/stepro/k8s-l5d/master/l5d.yaml'
                sleep 5
                script {
                    try {
                        sh "kubectl expose deployment l5d --name=${params.SERVICE_NAME} --port=80"
                    } catch (exc) {
                    }
                    finally {
                        // TODO: wait until IP becomes available
                        env.LOGICAL_SERVICE_IP = sh(returnStdout: true, script: "kubectl get service ${params.SERVICE_NAME} -o go-template={{.spec.clusterIP}}")
                    }

                    echo "Logical service IP: ${env.LOGICAL_SERVICE_IP}"
                    env.STABLE_SERVICE_EXISTS = true;
                    try {
                        env.EXISTING_SERVICE_NAME = sh(returnStdout: true, script: "kubectl get service --selector=via=${params.SERVICE_NAME},track=stable -o jsonpath='{.items[0].metadata.name}'")
                    } catch (exc) {
                        env.STABLE_SERVICE_EXISTS = false;
                        env.EXISTING_SERVICE_NAME = '';
                    }

                    if (env.STABLE_SERVICE_EXISTS == "true") {
                        // Do the canary
                        // Stable service exists, deploy to canary
                        echo 'Stable service exists - deploy the canary version'
                        echo "Deploying the canary service image: ${params.REGISTRY_URL}/${params.IMAGE_NAME}:${params.IMAGE_TAG}"
                        sh "kubectl run ${params.SERVICE_NAME}-${params.IMAGE_TAG} --image=${params.REGISTRY_URL}/${params.IMAGE_NAME}:${params.IMAGE_TAG} --port=80"
                        sh "kubectl expose deployment ${params.SERVICE_NAME}-${params.IMAGE_TAG} -l via=${params.SERVICE_NAME},track=canary,run=${params.SERVICE_NAME}-${params.IMAGE_TAG} --port=80"
                        script {
                            // TODO: Wait for the service IP to become available
                            env.SERVICE_CANARY_IP=sh(returnStdout: true, script: "kubectl get service ${params.SERVICE_NAME}-${params.IMAGE_TAG} -o go-template={{.spec.clusterIP}}")
                        }
                        echo "CANARY SERVICE IP: ${env.SERVICE_STABLE_IP}"
                        env.CANARY_ROLLOUT=true;
                    } else {
                        // Deploy the stable version of the service
                        // Stable service doesn't exist yet (first deployment)
                        echo "Deploying the stable service image: ${params.REGISTRY_URL}/${params.IMAGE_NAME}:${params.IMAGE_TAG}"
                        sh "kubectl run ${params.SERVICE_NAME}-${params.IMAGE_TAG} --image=${params.REGISTRY_URL}/${params.IMAGE_NAME}:${params.IMAGE_TAG} --port=80"
                        sh "kubectl expose deployment ${params.SERVICE_NAME}-${params.IMAGE_TAG} -l via=${params.SERVICE_NAME},track=stable,run=${params.SERVICE_NAME}-${params.IMAGE_TAG} --port=80"
                        sh "kubectl annotate service ${params.SERVICE_NAME} l5d=/svc/${params.SERVICE_NAME}-${params.IMAGE_TAG}"
                        script {
                            // TODO: Wait for the service IP to become available
                            env.SERVICE_STABLE_IP=sh(returnStdout: true, script: "kubectl get service ${params.SERVICE_NAME}-${params.IMAGE_TAG} -o go-template={{.spec.clusterIP}}")
                        }
                        echo "STABLE SERVICE IP: ${env.SERVICE_STABLE_IP}"
                    }
                }
            }
        }
        stage ('Dark - 0%') {
            steps {
                script {
                    if (env.CANARY_ROLLOUT == "true") {
                        sh "kubectl annotate --overwrite service ${params.SERVICE_NAME} l5d=\"100*/label/track/stable/${params.SERVICE_NAME} &amp; 0*/label/track/canary/${params.SERVICE_NAME}\""
                        sleep 15
                    } else {
                        sh "kubectl annotate --overwrite service ${params.SERVICE_NAME} l5d=\"0*/label/track/stable/${params.SERVICE_NAME}\""
                    }
                }
            }
        }
        stage ('Canary - 5%') {
            steps {
                script {
                    if (env.CANARY_ROLLOUT == "true") {
                        sh "kubectl annotate --overwrite service ${params.SERVICE_NAME} l5d=\"95*/label/track/stable/${params.SERVICE_NAME} &amp; 5*/label/track/canary/${params.SERVICE_NAME}\""
                        sleep 15
                    }
                }
            }
        }
        stage ('Canary - 10%') {
            steps {
                script {
                    if (env.CANARY_ROLLOUT == "true") {
                        sh "kubectl annotate --overwrite service ${params.SERVICE_NAME} l5d=\"90*/label/track/stable/${params.SERVICE_NAME} &amp; 10*/label/track/canary/${params.SERVICE_NAME}\""
                        sleep 15
                    }
                }
            }
        }
        stage ('Canary - 25%') {
            steps {
                script {
                    if (env.CANARY_ROLLOUT == "true") {
                        sh "kubectl annotate --overwrite service ${params.SERVICE_NAME} l5d=\"75*/label/track/stable/${params.SERVICE_NAME} &amp; 25*/label/track/canary/${params.SERVICE_NAME}\""
                        sleep 15
                    }
                }
            }
        }
        stage ('Canary - 50%') {
            steps {
                script {
                    if (env.CANARY_ROLLOUT == "true") {
                        sh "kubectl annotate --overwrite service ${params.SERVICE_NAME} l5d=\"50*/label/track/stable/${params.SERVICE_NAME} &amp; 50*/label/track/canary/${params.SERVICE_NAME}\""
                        sleep 15
                    }
                }
            }
        }
        stage ('Canary - 75%') {
            steps {
                script {
                    if (env.CANARY_ROLLOUT == "true") {
                        sh "kubectl annotate --overwrite service ${params.SERVICE_NAME} l5d=\"25*/label/track/stable/${params.SERVICE_NAME} &amp; 75*/label/track/canary/${params.SERVICE_NAME}\""
                        sleep 15
                    }
                }
            }
        }
        stage ('Canary - 100%') {
            steps {
                script {
                    sh "kubectl annotate --overwrite service ${params.SERVICE_NAME} l5d=/svc/${params.SERVICE_NAME}-${params.IMAGE_TAG}"
                }
            }
        }
        stage ('Cleanup') {
            steps {
                script {
                    if (env.CANARY_ROLLOUT == "true") {
                        env.EXISTING_SERVICE_NAME = sh(returnStdout: true, script:"kubectl get service --selector=via=${params.SERVICE_NAME},track=stable -o jsonpath='{.items[0].metadata.name}'")
                        if (env.EXISTING_SERVICE_NAME?.trim()) {
                            echo "Delete the original deployment and service: ${env.EXISTING_SERVICE_NAME}"
                            sh "kubectl delete deployment -l run=${env.EXISTING_SERVICE_NAME}"
                            sh "kubectl delete service -l run=${env.EXISTING_SERVICE_NAME}"

                            echo "Re-label canary version as stable version"
                            sh "kubectl label --overwrite service ${params.SERVICE_NAME}-${params.IMAGE_TAG} track=stable"
                        }
                    }
                }
            }
        }
    }
    post {
        always {
            echo "Deployment completed."
            echo "Deleting Canary service"
        }
        success {
            echo "Deployment succeeded."
        }
        failure {
            echo "Failure"
        }
    }
}