terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "5.54.1"
    }
  }
}
# S3 bucket for storing processed data
resource "aws_s3_bucket" "metrics_bucket" {
  bucket = "adobe-secure-metrics-data"

  tags = {
    Name        = "metric_bucket"
    Environment = "Dev"
    Owner       = "Magda Miu team / Adobe"
    Purpose     = "Store CloudWatch Metric"
  }
}

# S3 bucket for storing Terraform
resource "aws_s3_bucket" "terraform_bucket" {
  bucket = "adobe-terraform-state-magdamteam-bucket"
}

  # tags = {
  #   Name        = "terraform_bucket"
  #   Environment = "Dev"
  #   Owner       = "Magda Miu team"
  #   Purpose     = "Store Terraform"
  # }
