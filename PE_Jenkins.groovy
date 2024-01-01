pipeline {
    agent any
    parameters{
        choice(name: 'action',choices: 'apply\ndestroy',description: 'Choose the action you want')
    }
    stages {
        stage('Clone') {
            steps {
                git branch: 'main', url: 'https://github.com/AbhishekRaoV/Intel_IceLake.git'
            }
        }
        stage('Build infra'){
            steps{
                script{
                  if(params.action == 'destroy'){
                    sh "terraform destroy --auto-approve "
                  }
                  if(params.action == 'apply'){
                    sh '''
                    
                       terraform init
                       terraform validate
                       terraform plan -out=tfplan
                       terraform apply tfplan -no-color
                       terraform output -json private_ips | jq -r '.[]'
                    '''
                    }
                }
            }
        }

        stage('Generate Inventory file'){
            steps{
                script{
                    sh ./inventoryfile.sh

        stage('Install ansible'){
            steps{
                script{
                    sh" ssh ubuntu@${postgres_ip} -- 'sudo apt install ansible'"
                    sh" ssh ubuntu@${hammer_ip} -- 'sudo apt install ansible'"  
                }
            }
        }
        stage('Install Tools'){
            steps{
                script{
                    sh """
                        ansible-playbook -i myinventory postgres_install.yaml
                        ansible-playbook -i myinventory hammerdb_install.yaml
                        ansible-playbook -i myinventory prometheus_install.yaml
                        ansible-playbook -i myinventory postgres_exporter_install.yaml -e postgres_ip=${postgres_ip}
                        ansible-playbook -i myinventory grafana_install.yaml
                    """
                }
            }
        }
        stage('Configure'){
            steps{
                script{
                    sh """
                        ansible-playbook -i myinventory postgres_config.yaml -e postgres_ip=${postgres_ip}, hammer_ip=${hammer_ip}
                        ansible-playbook -i myinventory hammer_config.yaml -e postgres_ip=${postgres_ip}
                        ansible-playbook -i myinventory postgres_backup.yaml 
                        ansible-playbook -i myinventory test_hammer.yaml -e postgres_ip=${postgres_ip}
                        ansible-playbook -i myinventory restore_db.yaml 
                    """
                }
            }
        }
    }
}

def instance() {
    // Create an Ansible inventory file
    sh "rm -rf myinventory 2> /dev/null && touch myinventory"
    
    // Master node
    // sh '''
    //        echo "[postgres]" >> myinventory
    //        echo -n "ansible_host=" >> myinventory
    //         terraform output --no-color -json instance_private_ip > output.json &&
    //         cat output.json |
    //         tr -d '[]"' |
    //         tr ',' '\\n' |
    //         head -1 |
    //         sed """s/\\$/ ansible_user=ubuntu/""" >> myinventory

    //     '''

    // def postgres_ip = sh(script: "cat output.json | tr -d \"[]\"' | tr ',' '\\n' | head -1 | sed 's/\\$/ ansible_user=ubuntu/")

    // Worker nodes with ansible_user=ubuntu
    // sh '''
    //         echo "[hammer]" >> myinventory
        
    //         terraform output -no-color -json instance_private_ip > output.json &&
    //         cat output.json |
    //         tr -d '[]"' |
    //         tr ',' '\\n' |
    //         tail -n +2 |
    //         sed """s/\\$/ ansible_user=ubuntu/""" >> myinventory
    //     '''

    // def hammer_ip = sh(script: "   cat output.json | tr -d \"[]\"' | tr ',' '\\n' | tail -n +2 | sed 's/\\$/ ansible_user=ubuntu/")
   
}
