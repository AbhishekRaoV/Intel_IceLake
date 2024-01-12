output "private_ips" {
  value=module.ec2-vm.*.private_ip
}

output "instance_IDs"{
  value=module.ec2-vm.*.id
}
