
terraform {
  # backend "s3" {
  #   bucket         = "adobe-terraform-magdamteam-bucket"
  #   key            = "global/cloudwatch-metrics-in-amp"
  #   region         = "us-east-1"
  #   encrypt        = true
  #   dynamodb_table = "terraform-lock-table"
  # }
  backend "s3" {
    bucket         = "cramer-terraform-state"
    key            = "terraform.tfstate"
    region         = "us-west-2"
    dynamodb_table = "cramer-terraform-state"
    encrypt        = true
  }

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.86"
    }
  }
}

provider "aws" {
  region  = var.aws_region
  profile = "default"

  default_tags {
    tags = {
      Environment = var.environment
      Project     = local.project_name
      Owner       = var.owner
    }
  }
}

# https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/s3_bucket
resource "aws_s3_bucket" "metrics_bucket" {
  bucket = "${var.prefix}-secure-metrics-data"
  tags = {
    Name    = "metric_bucket"
    Purpose = "CloudWatch metrics storage"
  }
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
resource "aws_cloudwatch_log_group" "project_logs" {
  name              = "${var.prefix}-project-logs"
  retention_in_days = var.project_logs_retention_in_days
}


# # CloudWatch Metric Stream Module
# module "cloudwatch_metric_stream" {
#   source                  = "./terraform/aws/cloudwatch_metric_stream_module"
#   metric_stream_name      = var.metric_stream_name
#   kinesis_firehose_arn    = module.kinesis_firehose.firehose_arn
#   tags                    = var.common_tags
# }
#
# # Lambda Module
# module "lambda_function" {
#   source           = "./terraform/aws/lambda_module"
#   function_name    = var.lambda_function_name
#   handler          = "com.example.LambdaHandler::handleRequest"
#   runtime          = "java11"
#   s3_bucket        = module.s3_bucket.bucket_name
#   s3_key           = var.lambda_jar_file_key
#   environment_vars = {
#     PROMETHEUS_REMOTE_WRITE_URL = var.prometheus_remote_write_url
#     AWS_AMP_ROLE_ARN            = var.aws_amp_role_arn
#     AWS_REGION                  = var.aws_region
#   }
#   tags = var.common_tags
# }
#
# # Prometheus Module
# module "prometheus" {
#   source                  = "./terraform/aws/prometheus_module"
#   prometheus_workspace_id = var.prometheus_workspace_id
#   tags                    = var.common_tags
# }
#
# # IAM Module
# module "iam" {
#   source             = "./terraform/aws/IAM_module"
#   lambda_role_name   = var.lambda_role_name
#   firehose_role_name = var.firehose_role_name
#   tags               = var.common_tags
# }


# provider "aws" {
#  region = "us-east-1" # Replace with your desired AWS region
#}
## IAM Role for CloudWatch Metric Stream
#resource "aws_iam_role" "metric_stream_role" {
#  name = "cloudwatch_metric_stream_role"
#  assume_role_policy = jsonencode({
#    Version = "2012-10-17",
#    Statement = [{
#      Effect    = "Allow",
#      Principal = { Service = "streams.metrics.cloudwatch.amazonaws.com" },
#      Action    = "sts:AssumeRole"
#    }]
#  })
#}
## IAM Policy for Metric Stream Role
#resource "aws_iam_role_policy" "metric_stream_policy" {
#  name   = "metric_stream_policy"
#  role   = aws_iam_role.metric_stream_role.name
#  policy = jsonencode({
#    Version = "2012-10-17",
#    Statement = [
#      {
#        Effect   = "Allow",
#        Action   = ["firehose:PutRecord", "firehose:PutRecordBatch",],
#        Resource = "*"
#      }
#    ]
#  })
#}
#
## S3 Bucket for Firehose Destination
#data "aws_s3_bucket" "firehose_bucket" {
#bucket = "adobe-secure-metrics-data"
#}
#
## Kinesis Firehose Delivery Stream
## resource "aws_kinesis_firehose_delivery_stream" "firehose_stream" {
##   name        = "cloudwatch-metric-stream"
##   destination = "extended_s3"
##   extended_s3_configuration {
##     role_arn   = "arn:aws:iam::471112885190:role/cloudwatch_metric_stream_role"
##     bucket_arn = "arn:aws:s3:::adobe-secure-metrics-data"
##   }
## }
## CloudWatch Metric Stream for DynamoDB Metrics
#resource "aws_cloudwatch_metric_stream" "dynamodb_metric_stream" {
#  name          = "dynamodb-metric-stream"
#  role_arn      = "arn:aws:iam::471112885190:role/cloudwatch_metric_stream_role"
#  firehose_arn  = "arn:aws:firehose:us-east-1:471112885190:deliverystream/KDS-S3-oBr67"
#  output_format = "json"
#  # Include specific DynamoDB metrics
#  include_filter {
#    namespace = "AWS/DynamoDB"
#    metric_names = [
#      "ConditionalCheckFailedRequests",
#      "ConsumedReadCapacityUnits",
#      "ConsumedWriteCapacityUnits",
#      "ReadThrottleEvents",
#      "ReturnedBytes",
#      "ReturnedItemCount",
#      "ReturnedRecordsCount",
#      "SuccessfulRequestLatency",
#      "SystemErrors",
#      "TimeToLiveDeletedItemCount",
#      "ThrottledRequests",
#      "UserErrors",
#      "WriteThrottleEvents",
#      "OnDemandMaxReadRequestUnits",
#      "OnDemandMaxWriteRequestUnits",
#      "AccountMaxReads",
#      "AccountMaxTableLevelReads",
#      "AccountMaxTableLevelWrites",
#      "AccountMaxWrites",
#      "ThrottledPutRecordCount"
#    ]
#  }
#}
## Alarm for ThrottledRequests
#resource "aws_cloudwatch_metric_alarm" "throttled_requests" {
#alarm_name          = "dynamodb-throttled-requests"
#comparison_operator = "GreaterThanThreshold"
#evaluation_periods  = 1
#metric_name         = "ThrottledRequests"
#namespace           = "AWS/DynamoDB"
#period              = 60
#statistic           = "Sum"
#threshold           = 5
#alarm_description   = "Triggers when throttled requests exceed 5."
#}
## Alarm for SystemErrors
#resource "aws_cloudwatch_metric_alarm" "system_errors" {
#alarm_name          = "dynamodb-system-errors"
#comparison_operator = "GreaterThanThreshold"
#evaluation_periods  = 1
#metric_name         = "SystemErrors"
#namespace           = "AWS/DynamoDB"
#period              = 60
#statistic           = "Sum"
#threshold           = 1
#alarm_description   = "Triggers when system errors occur."
#}

# resource "aws_iam_role" "lambda_execution_role" {
#  name = "lambda_execution_role"
#  assume_role_policy = jsonencode({
#    Version = "2012-10-17",
#    Statement = [
#      {
#        Effect    = "Allow",
#        Principal = { Service = "lambda.amazonaws.com" },
#        Action    = "sts:AssumeRole"
#      }
#    ]
#  })
#}
#resource "aws_iam_policy" "lambda_policy" {
#  name        = "lambda_basic_execution_policy"
#  description = "Policy for Lambda to access CloudWatch and S3"
#  policy = jsonencode({
#    Version = "2012-10-17",
#    Statement = [
#      {
#        Effect   = "Allow",
#        Action   = [
#          "logs:CreateLogGroup",
#          "logs:CreateLogStream",
#          "logs:PutLogEvents"
#        ],
#        Resource = "arn:aws:logs:*:*:*"
#      },
#      {
#        Effect   = "Allow",
#        Action   = [
#          "s3:GetObject",
#          "s3:PutObject"
#        ],
#        Resource = "arn:aws:s3:::adobe-lamba-bucket/*"
#      }
#    ]
#  })
#}
#resource "aws_iam_role_policy_attachment" "lambda_role_policy_attachment" {
#  role       = aws_iam_role.lambda_execution_role.name
#  policy_arn = aws_iam_policy.lambda_policy.arn
#}
#
#
#resource "aws_iam_role" "firehose_delivery_role" {
#  name = "firehose_delivery_role"
#  assume_role_policy = jsonencode({
#    Version = "2012-10-17",
#    Statement = [
#      {
#        Effect    = "Allow",
#        Principal = { Service = "firehose.amazonaws.com" },
#        Action    = "sts:AssumeRole"
#      }
#    ]
#  })
#}
#resource "aws_iam_policy" "firehose_policy" {
#  name        = "firehose_delivery_policy"
#  description = "Policy for Kinesis Firehose to access S3 and CloudWatch"
#  policy = jsonencode({
#    Version = "2012-10-17",
#    Statement = [
#      {
#        Effect   = "Allow",
#        Action   = [
#          "s3:PutObject",
#          "s3:GetBucketLocation",
#          "s3:ListBucket"
#        ],
#        Resource = [
#          "arn:aws:s3:::adobe-secure-metrics-data",
#          "arn:aws:s3:::adobe-secure-metrics-data/*"
#        ]
#      },
#      {
#        Effect   = "Allow",
#        Action   = [
#          "logs:PutLogEvents"
#        ],
#        Resource = "arn:aws:logs:*:*:*"
#      }
#    ]
#  })
#}
#resource "aws_iam_role_policy_attachment" "firehose_policy_attachment" {
#  role       = aws_iam_role.firehose_delivery_role.name
#  policy_arn = aws_iam_policy.firehose_policy.arn
#}
#
#resource "aws_iam_role" "prometheus_role" {
#  name = "prometheus_execution_role"
#  assume_role_policy = jsonencode({
#    Version = "2012-10-17",
#    Statement: [{
#      Effect = "Allow",
#      Principal = {
#        Service = "aps.amazonaws.com"
#      },
#      Action = "sts:AssumeRole"
#    }]
#  })
#}
#resource "aws_iam_policy" "prometheus_policy" {
#  name = "prometheus_policy"
#  policy = jsonencode({
#    Version = "2012-10-17",
#    Statement: [
#      {
#        Effect = "Allow",
#        Action = [
#          "aps:RemoteWrite",
#          "aps:QueryMetrics"
#        ],
#        Resource = "*"
#      }
#    ]
#  })
#}
#resource "aws_iam_role_policy_attachment" "prometheus_policy_attachment" {
#  role       = aws_iam_role.prometheus_role.name
#  policy_arn = aws_iam_policy.prometheus_policy.arn
#}