terraform {
  backend "s3" {
    bucket         = "adobe-terraform-magdamteam-bucket"
    key            = "global/dynamodb/terraform.tfstate"
    region         = "us-east-1"
    encrypt        = true
    dynamodb_table = "terraform-lock-table"

  }
}