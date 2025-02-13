
# https://registry.terraform.io/providers/hashicorp/aws/latest/docs/data-sources/iam_policy_document
data "aws_iam_policy_document" "firehose_assume_role" {
  statement {
    effect = "Allow"
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = [
        "firehose.amazonaws.com"
      ]
    }
  }
}

# https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/iam_role
resource "aws_iam_role" "firehose_role" {
  name               = "${upper(var.prefix)}FirehoseRole"
  assume_role_policy = data.aws_iam_policy_document.firehose_assume_role.json
}

# https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/iam_policy
resource "aws_iam_policy" "firehose_policy" {
  name        = "firehose_s3_policy"
  description = "Allows Firehose to write to S3"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "s3:PutObject",
          "s3:GetBucketLocation",
          "s3:ListBucket"
        ]
        Resource = [
          aws_s3_bucket.metrics_bucket.arn,
          "${aws_s3_bucket.metrics_bucket.arn}/*"
        ]
      }
    ]
  })
}

# 🔹 Attach IAM Policy to Role
resource "aws_iam_role_policy_attachment" "firehose_policy_attach" {
  role       = aws_iam_role.firehose_role.name
  policy_arn = aws_iam_policy.firehose_policy.arn
}

# resource "aws_iam_policy" "kinesis_access_policy" {
#  name        = "${upper(var.prefix)}Kinesis"
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
# }
#

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

  kinesis_source_configuration {
    kinesis_stream_arn = aws_kinesis_stream.metrics.arn
    role_arn           = aws_iam_role.firehose_role.arn
  }

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

