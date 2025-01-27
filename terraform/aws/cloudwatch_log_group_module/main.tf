terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "5.54.1"
    }
  }
}
resource "aws_cloudwatch_log_group" "lambda_log_group" {
name                = "/aws/lambda/metric-lambda-fuction"
  retention_in_days = 30
}

resource "aws_cloudwatch_log_group" "firehose_log_group" {
  name             =  "/aws/firehose/metric-delivery-stream"
  retention_in_days = 14
}