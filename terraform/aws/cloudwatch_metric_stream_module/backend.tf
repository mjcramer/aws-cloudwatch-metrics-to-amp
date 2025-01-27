terraform {
  backend "s3" {
    bucket         = "adobe-terraform-magdamteam-bucket"
    key            = "global/cloudwatch-metric-stream/terraform.tfstate"
    region         = "us-east-1"
    encrypt        = true
    dynamodb_table = "terraform-lock-table"

  }
}