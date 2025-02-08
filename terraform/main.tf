
provider "aws" {
  region  = "us-west-2" # Change to your desired AWS region
  profile = "default"
}

variable "region" {
  default = "us-west-2"
}

variable "account_id" {
  default = "240259995564"
}

variable "lambda_jar" {
  default = "../target/kinesis-to-amp-1.0.jar"
}

# https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/s3_bucket
resource "aws_s3_bucket" "test_bucket" {
  bucket = "kta-test-bucket"
}

# https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/s3_bucket_acl
# resource "aws_s3_bucket_acl" "bucket_acl" {
#   bucket = aws_s3_bucket.test_bucket.id
#   acl    = "private"
# }
# resource "aws_s3_bucket_policy" "firehose_policy" {
#   bucket = aws_s3_bucket.firehose_bucket.id
#   policy = jsonencode({
#     Version = "2012-10-17"
#     Statement = [
#       {
#         Effect    = "Allow"
#         Principal = { Service = "firehose.amazonaws.com" }
#         Action    = "s3:PutObject"
#         Resource  = "${aws_s3_bucket.firehose_bucket.arn}/*"
#       }
#     ]
#   })
# }

# https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/cloudwatch_log_group
resource "aws_cloudwatch_log_group" "kinesis_log_group" {
  name              = "kinesis-logs"
  retention_in_days = 14 # Retain logs for 14 days
  tags = {
    Environment = "Dev"
  }
}

# https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/cloudwatch_log_stream
resource "aws_cloudwatch_log_stream" "kinesis_log_stream" {
  name           = "kinesis-log-stream"
  log_group_name = aws_cloudwatch_log_group.kinesis_log_group.name
}
