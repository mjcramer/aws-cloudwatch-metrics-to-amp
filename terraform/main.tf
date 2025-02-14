
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