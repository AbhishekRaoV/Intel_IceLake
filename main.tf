module "ec2-vm" {
    source = "intel/aws-vm/intel"
    count = var.instance_count
    ami = var.ami
    instance_type= var.instance_type
    subnet_id= var.subnet_id
    vpc_security_group_ids= var.security_group_id
    key_name =  var.key_name
    root_block_device = [
        {
            volume_size = var.volume_size
            volume_type= var.volume_type //example:- gp2,gp3,io1,io2,sc1,st1,standard
        }
    ]
    tags = {
        Name     = "my-test-vm-${count.index + 1}"
        Owner    = "OwnerName-${random_id.rid.dec}",
    }
}
resource "random_id" "rid" {
  byte_length = 4
}

