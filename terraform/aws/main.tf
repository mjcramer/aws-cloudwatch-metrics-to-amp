provider "aws" {
  region = var.aws_region
}

# S3 Bucket Module
module "s3_bucket" {
  source = "./terraform/aws/s3_module"
  bucket_name = var.bucket_name
  tags        = var.common_tags
}

# DynamoDB Module
module "dynamodb" {
  source       = "./terraform/aws/dynamodb_module"
  table_name   = var.dynamodb_table_name
  billing_mode = "PAY_PER_REQUEST"
  tags         = var.common_tags
}

# Kinesis Firehose Module
module "kinesis_firehose" {
  source                 = "./terraform/aws/kinesis_firehose_module"
  firehose_name          = var.firehose_name
  s3_bucket_arn          = module.s3_bucket.bucket_arn
  cloudwatch_log_group   = var.firehose_log_group
  cloudwatch_log_stream  = var.firehose_log_stream
  tags                   = var.common_tags
}

# CloudWatch Metric Stream Module
module "cloudwatch_metric_stream" {
  source                  = "./terraform/aws/cloudwatch_metric_stream_module"
  metric_stream_name      = var.metric_stream_name
  kinesis_firehose_arn    = module.kinesis_firehose.firehose_arn
  tags                    = var.common_tags
}

# Lambda Module
module "lambda_function" {
  source           = "./terraform/aws/lambda_module"
  function_name    = var.lambda_function_name
  handler          = "com.example.LambdaHandler::handleRequest"
  runtime          = "java11"
  s3_bucket        = module.s3_bucket.bucket_name
  s3_key           = var.lambda_jar_file_key
  environment_vars = {
    PROMETHEUS_REMOTE_WRITE_URL = var.prometheus_remote_write_url
    AWS_AMP_ROLE_ARN            = var.aws_amp_role_arn
    AWS_REGION                  = var.aws_region
  }
  tags = var.common_tags
}

# Prometheus Module
module "prometheus" {
  source                  = "./terraform/aws/prometheus_module"
  prometheus_workspace_id = var.prometheus_workspace_id
  tags                    = var.common_tags
}

# IAM Module
module "iam" {
  source             = "./terraform/aws/IAM_module"
  lambda_role_name   = var.lambda_role_name
  firehose_role_name = var.firehose_role_name
  tags               = var.common_tags
}
