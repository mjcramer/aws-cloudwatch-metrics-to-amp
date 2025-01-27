
# DynamoDB Tables for Metric Storage and Terraform State Lock
provider "aws" {
  region = "us-east-1" # Set your preferred AWS region
}
# DynamoDB Table for Metrics
resource "aws_dynamodb_table" "metrics_table" {
  name         = "metrics-dynamodb-table"
  billing_mode = "PAY_PER_REQUEST" # On-demand billing for flexibility
  hash_key     = "metric_id"
  attribute {
    name = "metric_id"
    type = "S" # S for String
  }
  # Enable server-side encryption (default: AWS-managed key)
  server_side_encryption {
    enabled = true
  }
  # Add point-in-time recovery (backup best practice)
  point_in_time_recovery {
    enabled = true
  }
  # Tags for resource organization
  tags = {
    Environment = "Development"
    Project     = "Metrics Storage"
    Owner       = " Magda Miu Team"
  }
}
# DynamoDB Table for Terraform State Lock
resource "aws_dynamodb_table" "terraform_table" {
  name         = "terraform-lock-table"
  billing_mode = "PAY_PER_REQUEST" # On-demand billing for flexibility
  hash_key     = "lock_id"
  attribute {
    name = "lock_id"
    type = "S" # S for String
  }
  # Enable server-side encryption (default: AWS-managed key)
  server_side_encryption {
    enabled = true
  }
  # Add point-in-time recovery (backup best practice)
  point_in_time_recovery {
    enabled = true
  }
  # Tags for resource organization
  tags = {
    Environment = "Development"
    Project     = "Terraform State Management"
    Owner       = "Magda Miu Team"
  }
}









