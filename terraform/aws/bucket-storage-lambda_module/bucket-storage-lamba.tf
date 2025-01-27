terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "5.54.1"
    }
  }
}
# S3 bucket for storing processed data
resource "aws_s3_bucket" "lambda_bucket" {
  bucket = "adobe-lamba-bucket"

  tags = {
    Name        = "lambda_bucket"
    Environment = "Dev"
    Owner       = "Magda Miu team / Adobe"
    Purpose     = "Store lambda file"
  }
}
