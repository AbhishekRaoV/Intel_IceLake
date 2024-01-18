#!/bin/bash

# Path to the Terraform state file
TF_STATE_FILE="terraform.tfstate"

# Extract IP addresses from the Terraform output
IP_ADDRESSES=($(terraform output -json private_ips | jq -r '.[]'))

# Create or truncate the Ansible inventory file
> myinventory

# Write IP addresses with specific hostnames and "ubuntu@" prefix to the Ansible inventory file
echo "mysql ansible_ssh_host=ubuntu@${IP_ADDRESSES[0]}" >> myinventory
echo "hammer ansible_ssh_host=ubuntu@${IP_ADDRESSES[1]}" >> myinventory

# Print the Ansible inventory file content
cat myinventory
