pipeline {
    agent any
    parameters {
        // choice(name: 'Generation', choices: ['3rd-Gen','4th-Gen'], description: 'Intel processor generation') 
        choice(name: 'Optimization', choices: ['Optimized','Non-Optimized'], description: 'Use Intel optimized instance type or not') 
        // choice(name: 'InstanceType', choices: ['t2.micro','t2.medium','t2.large'], description: 'EC2 instance type to provision') 
        choice(name: 'OS', choices: ['Ubuntu'], description: 'Operating system for the EC2 instance') 
        choice(name: 'VolumeType', choices: ['gp2','gp3','io1','io2','sc1','st1','standard'], description: 'EBS volume type') 
        choice(name: 'VolumeSize',  choices: ['50','100','150','200'], description: 'Size of EBS volume in GB')
    }
    environment {
        instance_id = ''
    }
    stages {
        stage('Clone') {
            steps {
                script{
                    ws("workspace/${JOB_NAME}") {
                        cleanWs()
                        path=sh(script:'pwd', returnStdout: true).trim()
                        sh " echo instance_type=${params.InstanceType} -var volume_type=${params.VolumeType} -var volume_size=${params.VolumeSize}"
                        def fileCount = sh(script: 'ls -la | wc -l', returnStdout: true).trim()
                        echo "File count: $fileCount"
                        if (fileCount.toInteger() == 3) {
                            git branch: 'temp', url: 'https://github.com/AbhishekRaoV/Intel_IceLake.git'
                        }else{
                            git branch: 'temp', url: 'https://github.com/AbhishekRaoV/Intel_IceLake.git'
                        }
                    }
                }
            }
        }
        stage('Build Infra') {
            steps {
                script {
                     ws("${path}"){
                        sh "terraform init"
                        sh "terraform validate"
                        sh "terraform apply -no-color -var instance_type=${params.InstanceType} -var volume_type=${params.VolumeType} -var volume_size=${params.VolumeSize} --auto-approve"
                        sh "terraform output -json private_ips | jq -r '.[]'"
                        waitStatus()
                        instance_id=sh(script: "terraform output -json instance_IDs | jq -r '.[]' | head -1",returnStdout: true).trim()
                        sh "echo ${instance_id}"
                        postgres_ip = sh(script: "terraform output -json private_ips | jq -r '.[]' | head -1", returnStdout: true).trim()
                        hammer_ip = sh(script: "terraform output -json private_ips | jq -r '.[]' | tail -1", returnStdout: true).trim()
                        sh '''
                        echo "Postgres IP: ${postgres_ip}"
                        echo "Hammer IP: ${hammer_ip}"
                        '''
                    }
                }
                }
        }

        stage('Generate Inventory File') {
            steps {
                script {
                     ws("${path}"){
                    sh 'chmod +x inventoryfile.sh'
                    sh 'bash ./inventoryfile.sh'
                    // sh "ssh -o StrictHostKeyChecking=no ubuntu@${postgres_ip} -- 'sudo apt update && sudo apt install ansible -y'"
                    // sh "ssh -o StrictHostKeyChecking=no ubuntu@${hammer_ip} -- 'sudo apt update && sudo apt install ansible -y'"
                }
                }
            }
        }

        stage('Install & Configure') {
            steps {
                script {
                     ws("${path}"){
                        def timeoutSeconds = 300  // Set a reasonable timeout
                         def buildNumber = currentBuild.number

                        timeout(time: timeoutSeconds, unit: 'SECONDS') {
                            boolean ansiblePingSuccess = false
    
                            while (!ansiblePingSuccess) {
                                // Run Ansible ping command
                                def ansiblePingCommand = "ansible all -m ping"
                                def ansiblePingResult = sh(script: ansiblePingCommand, returnStatus: true)
                                if (ansiblePingResult == 0) {
                                    ansiblePingSuccess = true
                                    echo "Ansible ping successful!"
                                } else {
                                    echo "Ansible ping failed. Retrying..."
                                    sleep 10  // Adjust sleep duration as needed
                                }
                            }
                        }

                    if("${params.Optimization}" == "Optimized"){
                    sh """
                        ansible-playbook -i myinventory postgres_install.yaml
                        ansible-playbook -i myinventory hammerdb_install.yaml
                        ansible-playbook -i myinventory node_exporter_install.yaml
                        ansible-playbook -i myinventory postgres_exporter_install.yaml -e postgres_ip=${postgres_ip}
                        ansible-playbook -i myini prometheus_config.yaml -e postgres_ip=${postgres_ip} -e buildNumber=${buildNumber}
                         
                    """
                        // ansible-playbook -i myinventory postgres_config_with_optimisation.yaml -e postgres_ip=${postgres_ip} -e hammer_ip=${hammer_ip}
                        // ansible-playbook -i myinventory hammer_config.yaml -e postgres_ip=${postgres_ip}
                        // ansible-playbook -i myinventory postgres_backup.yaml
                    }

                    if("${params.Optimization}" == "Non-Optimized"){
                    sh """
                        ansible-playbook -i myinventory postgres_install.yaml
                        ansible-playbook -i myinventory hammerdb_install.yaml
                        ansible-playbook -i myinventory node_exporter_install.yaml
                        ansible-playbook -i myinventory postgres_exporter_install.yaml -e postgres_ip=${postgres_ip}
                        ansible-playbook -i myini prometheus_config.yaml -e postgres_ip=${postgres_ip} -e buildNumber=${buildNumber}
                        
                    """
                        // ansible-playbook -i myinventory postgres_config.yaml -e postgres_ip=${postgres_ip} -e hammer_ip=${hammer_ip} 
                        // ansible-playbook -i myinventory hammer_config.yaml -e postgres_ip=${postgres_ip}
                        // ansible-playbook -i myinventory postgres_backup.yaml 
                    }
                        // ansible-playbook -i myinventory prometheus_install.yaml
                        // ansible-playbook -i myinventory grafana_install.yaml
                }
                }
            }
        }

        // stage('Test') {
        //     steps {
        //         script {
        //              ws("${path}"){
        //             sh """
        //                 ansible-playbook -i myinventory test_hammer.yaml -e postgres_ip=${postgres_ip}
        //                 ansible-playbook -i myinventory restore_db.yaml
        //                 ansible-playbook -i myinventory test_hammer.yaml -e postgres_ip=${postgres_ip}
        //                 ansible-playbook -i myinventory restore_db.yaml 
        //                 ansible-playbook -i myinventory test_hammer.yaml -e postgres_ip=${postgres_ip}
        //                 ansible-playbook -i myinventory restore_db.yaml  
                    
                        
        //             """
                    
        //         }
        //         }
        //     }
        //     post('Artifact'){
        //     success{
        //         script{
        //              ws("${path}"){
        //             archiveArtifacts artifacts: '**/results.txt'
        //         }
        //         }
        //     }
        //     }
        // }
        // stage('Push to Mysql') {
        //     steps {
        //         script {
        //              ws("${path}"){
        //             withEnv(["INSTANCE_ID=${instance_id}"]) {
        //                 echo "Instance ID inside Hello stage: ${INSTANCE_ID}"
        //                 def avg_cpu = sh(
        //                 script: '''
        //                     aws cloudwatch get-metric-statistics \
        //                         --namespace AWS/EC2 \
        //                         --metric-name CPUUtilization \
        //                         --dimensions Name=InstanceId,Value=${INSTANCE_ID} \
        //                         --start-time "$(date -u -d '1 hour ago' '+%Y-%m-%dT%H:%M:%S')" \
        //                         --end-time "$(date -u '+%Y-%m-%dT%H:%M:%S')" \
        //                         --period 3600 \
        //                         --statistics Average | jq -r '.Datapoints[].Average'
        //                      ''',returnStdout: true).trim()
        //                 def avg_cpu_value = avg_cpu.toDouble()
        //                 echo "Avg CPU: ${avg_cpu_value}"
        //                 def max_cpu = sh(
        //                     script: '''
        //                         aws cloudwatch get-metric-statistics \
        //                             --namespace AWS/EC2 \
        //                             --metric-name CPUUtilization \
        //                             --dimensions Name=InstanceId,Value=${INSTANCE_ID} \
        //                             --start-time "$(date -u -d '1 hour ago' '+%Y-%m-%dT%H:%M:%S')" \
        //                             --end-time "$(date -u '+%Y-%m-%dT%H:%M:%S')" \
        //                             --period 3600 \
        //                             --statistics Maximum | jq -r '.Datapoints[].Maximum'
        //                         ''',
        //                         returnStdout: true
        //                     ).trim()
        //                 def max_cpu_value = max_cpu.toDouble()
        //                 echo "Max CPU: ${max_cpu_value}"        
        //             sh "ls"
        //             def NOPM = sh(script: 'cat results.txt | cut -d " " -f3 | tr "\\n" "+" | sed "s/+$/\\n/" | bc -l', returnStdout: true).trim().toDouble() / 3
        //             def TPM = sh(script: 'cat results.txt | cut -d " " -f6 | tr "\\n" "+" | sed "s/+$/\\n/" | bc -l', returnStdout: true).trim().toDouble() / 3

        //             echo "Average NOPM: ${NOPM}"
        //             echo "Average TPM: ${TPM}"

        //             def generation = params.Generation
        //             def optimization = params.Optimization
        //             def instanceType = params.InstanceType
        //             def os = params.OS
        //             def volumeType = params.VolumeType
        //             def volumeSize = params.VolumeSize
        //             def buildNumber = currentBuild.number

        //             sh """
        //              mysql -h10.63.34.188 -uroot -proot intel -e 'CREATE TABLE IF NOT EXISTS sysconfig (
        //                 build_number INT,
        //                 generation VARCHAR(255),
        //                 optimization VARCHAR(255),
        //                 instance_type VARCHAR(255),
        //                 os VARCHAR(255),
        //                 volume_type VARCHAR(255),
        //                 volume_size INT,
        //                 NOPM INT,
        //                 TPM INT,
        //                 max_cpu FLOAT,
        //                 avg_cpu FLOAT,
        //                 BuildTime FLOAT
        //             );'
        //             """
        //             def totalBuildTimeMinutes = currentBuild.duration / 60000
        //             sh """
        //                 sudo mysql -h10.63.34.188 -uroot -proot intel -e 'INSERT INTO sysconfig (build_number, generation, optimization, instance_type, os, volume_type, volume_size, NOPM, TPM, max_cpu, avg_cpu,BuildTime) VALUES (${buildNumber},\"${generation}\",\"${optimization}\",\"${instanceType}\",\"${os}\",\"${volumeType}\",${volumeSize}, ${NOPM}, ${TPM}, ${max_cpu_value}, ${avg_cpu_value},${totalBuildTimeMinutes});'
        //             """
        //             }
        //         }
        //         }
        //     }
        // }
    }

    // post('Destroy Infra'){
    //     always{
    //         script{
    //              ws("${path}"){
    //         sh "terraform destroy --auto-approve "
    //             }
    //         }
    //     }
    // }
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
