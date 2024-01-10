def start = ""
def stop = ""
def res = ""
def hourlyRate = ""
def cost = ""
pipeline {
    agent any
    environment {
        AWS_REGION = 'us-east-1'
        INSTANCE_TYPE = 'your_instance_type'
        START_TIME = ''
        END_TIME = ''
        C4_2XLARGE = '0.398'
        M6I_XLARGE = '0.192'
        M6I_2XLARGE = '0.384'
        M6I_4XLARGE = '0.768'
        M6I_8XLARGE = '1.536'
        M6I_16XLARGE = '3.072'
        M7I_XLARGE = '0.2016'
        M7I_2XLARGE = '0.4032'
        M7I_4XLARGE = '0.8064'
        M7I_8XLARGE = '1.6128'
        M7I_16XLARGE = '3.2256'
    }
    parameters {
        // choice(name: 'Generation', choices: ['3rd-Gen','4th-Gen'], description: 'Intel processor generation') 
        choice(name: 'Optimization', choices: ['Optimized','Non-Optimized'], description: 'Use Intel optimized instance type or not') 
        // choice(name: 'InstanceType', choices: ['t2.micro','t2.medium','t2.large'], description: 'EC2 instance type to provision') 
        choice(name: 'OS', choices: ['Ubuntu'], description: 'Operating system for the EC2 instance') 
        choice(name: 'VolumeType', choices: ['gp2','gp3','io1','io2','sc1','st1','standard'], description: 'EBS volume type') 
        choice(name: 'VolumeSize',  choices: ['50','100','150','200'], description: 'Size of EBS volume in GB')
    }
    stages {
        stage('Clone') {
            steps {
                sh " echo instance_type=${params.InstanceType} -var volume_type=${params.VolumeType} -var volume_size=${params.VolumeSize}"
                cleanWs()
                git branch: 'main', url: 'https://github.com/AbhishekRaoV/Intel_IceLake.git'
            }
        }
        stage('Build Infra') {
            steps {
                script {
                        sh "terraform init"
                        sh "terraform validate"
                        //calculate time
                        def command = "date +%T"
                        start = sh(returnStdout: true, script: command).trim()
                        echo start
                        // Echo the environment variable
                        echo "env.START_TIME: ${env.START_TIME}"
                        sh "terraform apply -no-color -var instance_type=${params.InstanceType} -var volume_type=${params.VolumeType} -var volume_size=${params.VolumeSize} --auto-approve"
                        sh "terraform output -json private_ips | jq -r '.[]'"
                        waitStatus()
                        postgres_ip = sh(script: "terraform output -json private_ips | jq -r '.[]' | head -1", returnStdout: true).trim()
                        hammer_ip = sh(script: "terraform output -json private_ips | jq -r '.[]' | tail -1", returnStdout: true).trim()
                        sh '''
                        echo "Postgres IP: ${postgres_ip}"
                        echo "Hammer IP: ${hammer_ip}"
                        '''
                    }
                }
        }

        // stage('Generate Inventory File') {
        //     steps {
        //         script {
        //             sh 'chmod +x inventoryfile.sh'
        //             sh 'bash ./inventoryfile.sh'
        //             // sh "ssh -o StrictHostKeyChecking=no ubuntu@${postgres_ip} -- 'sudo apt update && sudo apt install ansible -y'"
        //             // sh "ssh -o StrictHostKeyChecking=no ubuntu@${hammer_ip} -- 'sudo apt update && sudo apt install ansible -y'"
        //         }
        //     }
        // }

        // stage('Install & Configure') {
        //     steps {
        //         script {

        //             if("${params.Optimization}" == "Optimized"){
        //             sh """
        //                 ansible-playbook -i myinventory postgres_install.yaml
        //                 ansible-playbook -i myinventory hammerdb_install.yaml
        //                 ansible-playbook -i myinventory node_exporter_install.yaml
        //                 ansible-playbook -i myini prometheus_config.yaml -e postgres_ip=${postgres_ip}
        //                 ansible-playbook -i myinventory postgres_config_with_optimisation.yaml -e postgres_ip=${postgres_ip} -e hammer_ip=${hammer_ip}
                         
        //             """
        //                 // ansible-playbook -i myinventory hammer_config.yaml -e postgres_ip=${postgres_ip}
        //                 // ansible-playbook -i myinventory postgres_backup.yaml
        //             }

        //             if("${params.Optimization}" == "Non-Optimized"){
        //             sh """
        //                 ansible-playbook -i myinventory postgres_install.yaml
        //                 ansible-playbook -i myinventory hammerdb_install.yaml
        //                 ansible-playbook -i myinventory node_exporter_install.yaml
        //                 ansible-playbook -i myini prometheus_config.yaml -e postgres_ip=${postgres_ip}
        //                 ansible-playbook -i myinventory postgres_config.yaml -e postgres_ip=${postgres_ip} -e hammer_ip=${hammer_ip}
                        
        //             """
        //                 // ansible-playbook -i myinventory hammer_config.yaml -e postgres_ip=${postgres_ip}
        //                 // ansible-playbook -i myinventory postgres_backup.yaml 
        //             }
        //                 // ansible-playbook -i myinventory prometheus_install.yaml
        //                 // ansible-playbook -i myinventory postgres_exporter_install.yaml -e postgres_ip=${postgres_ip}
        //                 // ansible-playbook -i myinventory grafana_install.yaml
        //         }
        //     }
        // }

        // stage('Test') {
        //     steps {
        //         script {
        //             sh """
        //                 ansible-playbook -i myinventory test_hammer.yaml -e postgres_ip=${postgres_ip}
        //                 ansible-playbook -i myinventory restore_db.yaml 
                        
        //             """
        //             // ansible-playbook -i myinventory test_hammer.yaml -e postgres_ip=${postgres_ip}
        //             //     ansible-playbook -i myinventory restore_db.yaml 
        //             //     ansible-playbook -i myinventory test_hammer.yaml -e postgres_ip=${postgres_ip}
        //             //     ansible-playbook -i myinventory restore_db.yaml 
        //         }
        //     }
        //     post('Artifact'){
        //     success{
        //             archiveArtifacts artifacts: '**/results.txt'
        //         }
        //     }
        // }
    }

    post('Destroy Infra'){
        always{
            script{
                sh "terraform destroy --auto-approve "
                //calculate end time
                def command = "date +%T"
                stop = sh(returnStdout: true, script: command).trim()
                echo stop
                //calculate cost
                sh "chmod +x cost.sh"
                def command = "bash cost.sh ${start} ${stop}"
                res = sh(returnStdout: true, script: command).trim()

                echo res
                if("$params.InstanceType" == "C4_2XLARGE"){
                    hourlyRate = "0.398"
                }
                if("$params.InstanceType" == "M6I_XLARGE"){
                    hourlyRate = "0.192"
                }
                if("$params.InstanceType" == "M6I_4XLARGE"){
                    hourlyRate = "0.768"
                }
                def numericRes = res.toInteger()
                def hourlyRateBigDecimal = hourlyRate.toDouble()
                cost = numericRes / 3600 * hourlyRateBigDecimal * 2
                echo "Cost: ${cost.toString()}"
            }
        }
    }
}

def waitStatus(){
  def instanceIds = sh(returnStdout: true, script: "terraform output -json instance_IDs | tr -d '[]\"' | tr ',' ' '").trim().split(' ')
  for (int i = 0; i < instanceIds.size(); i++) {
    def instanceId = instanceIds[i]
    while (true) {
      def status = sh(returnStdout: true, script: "aws ec2 describe-instances --instance-ids ${instanceId} --query 'Reservations[].Instances[].State.Name' --output text").trim()
      if (status != 'running') {
        print '.'
      } else {
        println "Instance ${instanceId} is ${status}"
        sleep 10
        break  
      }
      sleep 5
    }
  }
}
