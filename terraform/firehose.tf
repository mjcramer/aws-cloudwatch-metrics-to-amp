

# # 🔹 IAM Role for Firehose
# resource "aws_iam_role" "firehose_role" {
#   name = "firehose_delivery_role"
#
#   assume_role_policy = jsonencode({
#     Version = "2012-10-17"
#     Statement = [
#       {
#         Effect = "Allow"
#         Principal = {
#           Service = "firehose.amazonaws.com"
#         }
#         Action = "sts:AssumeRole"
#       }
#     ]
#   })
# }
#
# # 🔹 IAM Policy for Firehose
# resource "aws_iam_policy" "firehose_policy" {
#   name        = "firehose_s3_policy"
#   description = "Allows Firehose to write to S3"
#
#   policy = jsonencode({
#     Version = "2012-10-17"
#     Statement = [
#       {
#         Effect = "Allow"
#         Action = [
#           "s3:PutObject",
#           "s3:GetBucketLocation",
#           "s3:ListBucket"
#         ]
#         Resource = [
#           aws_s3_bucket.firehose_bucket.arn,
#           "${aws_s3_bucket.firehose_bucket.arn}/*"
#         ]
#       }
#     ]
#   })
# }

# # 🔹 Attach IAM Policy to Role
# resource "aws_iam_role_policy_attachment" "firehose_policy_attach" {
#   role       = aws_iam_role.firehose_role.name
#   policy_arn = aws_iam_policy.firehose_policy.arn
# }
#resource "aws_iam_policy" "kinesis_access_policy" {
#  name        = "KinesisAccessPolicy"
#  description = "Policy to allow Kinesis stream operations and CloudWatch logging"
#  policy      = jsonencode({
#    Version = "2012-10-17",
#    Statement = [
#      # Allow Kinesis actions on the specific stream
#      {
#        Action = [
#          "kinesis:CreateStream",
#          "kinesis:DescribeStream",
#          "kinesis:DescribeStreamSummary",
#          "kinesis:ListStreams",
#          "kinesis:ListShards",
#          "kinesis:PutRecord",
#          "kinesis:PutRecords",
#          "kinesis:GetRecords",
#          "kinesis:GetShardIterator"
#        ],
#        Effect   = "Allow",
#        Resource = "arn:aws:kinesis:${var.region}:${var.account_id}:stream/${aws_kinesis_stream.test_stream.name}"
#      },
#      # Allow CloudWatch Logs actions on the specific log group
#      {
#        Action = [
#          "logs:PutLogEvents",
#          "logs:CreateLogStream"
#        ],
#        Effect   = "Allow",
#        Resource = [
#          aws_cloudwatch_log_group.kinesis_log_group.arn,
#          "${aws_cloudwatch_log_group.kinesis_log_group.arn}:*"
#        ]
#      },
#      # Allow Lambda execution permissions
#      {
#        Action = [
#          "lambda:InvokeFunction",
#          "lambda:GetFunctionConfiguration"
#        ],
#        Effect   = "Allow",
#        Resource = aws_lambda_function.kinesis_lambda_function.arn
#      }
#    ]
#  })
#}



# https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/iam_role
# resource "aws_iam_role" "kinesis_to_amp_role" {
#   name               = "KinesisToAmpRole"
#   assume_role_policy = jsonencode({
#     Version = "2012-10-17",
#     Statement = [
#       {
#         Action = "sts:AssumeRole",
#         Effect = "Allow",
#         Principal = {
#           Service = "lambda.amazonaws.com"
#         }
#       }
#     ]
#   })
# }
#
# # IAM Policy Attachment for Lambda Role
# resource "aws_iam_role_policy_attachment" "lambda_basic_execution" {
#   role       = "arn:aws:iam::471112885190:role/lambda_execution_roleS"
#   policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
# }

data "aws_iam_policy_document" "firehose_assume_role" {
  statement {
    effect = "Allow"

    principals {
      type        = "Service"
      identifiers = ["firehose.amazonaws.com"]
    }

    actions = ["sts:AssumeRole"]
  }
}

# https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/iam_role
resource "aws_iam_role" "firehose_role" {
  name               = "${upper(var.prefix)}FirehoseRole"
  assume_role_policy = data.aws_iam_policy_document.firehose_assume_role.json
}


# https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/kinesis_stream
resource "aws_kinesis_stream" "metrics" {
  name             = "${var.prefix}-metrics"
  # shard_count      = var.metrics_stream_shard_count
  retention_period = 48 # Retain data for 48 hours
  shard_level_metrics = [
    "IncomingBytes",
    "OutgoingBytes"
  ]

  stream_mode_details {
    stream_mode = "ON_DEMAND"
  }
}

# https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/cloudwatch_log_stream
resource "aws_cloudwatch_log_stream" "metric_delivery_stream_logs" {
  name           = "${var.prefix}-metric-delivery-stream-logs"
  log_group_name = aws_cloudwatch_log_group.project_logs.name
}

# https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/kinesis_firehose_delivery_stream
resource "aws_kinesis_firehose_delivery_stream" "metrics" {
  name        = "${var.prefix}-metrics"
  destination = "extended_s3"

  extended_s3_configuration {
    role_arn           = aws_iam_role.firehose_role.arn
    bucket_arn         = aws_s3_bucket.metrics_bucket.arn
    buffering_size     = var.metrics_bucket_buffering_size
    buffering_interval = var.metrics_bucket_buffering_interval
    compression_format = "Snappy"

    cloudwatch_logging_options {
      enabled = true
      log_group_name  = aws_cloudwatch_log_group.project_logs.name
      log_stream_name = aws_cloudwatch_log_stream.metric_delivery_stream_logs.name
    }

    processing_configuration {
      enabled = true

      processors {
        type = "Lambda"

        parameters {
          parameter_name  = "LambdaArn"
          parameter_value = "${aws_lambda_function.kinesis_to_amp.arn}:$LATEST"
        }

        # Optional: Include retry duration if needed
        # parameters {
        #     parameter_name  = "RetryDuration"
        #     parameter_value = "60" # seconds
        # }
      }
    }

    # S3 backup mode is optional, default is "Disabled"
    s3_backup_mode = "Disabled"
  }

  #   server_side_encryption {
  #     enabled  = var.sse_enabled
  #     key_type = var.sse_key_type # can be "AWS_OWNED_CMK" or "CUSTOMER_MANAGED_CMK"
  #   }
}

