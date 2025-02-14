
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

# 🔹 Attach IAM Policy to Role
resource "aws_iam_role_policy_attachment" "firehose_policy_attach" {
  role       = aws_iam_role.firehose_role.name
  policy_arn = aws_iam_policy.firehose_policy.arn
}

data "aws_iam_policy_document" "firehose_policy" {
  statement {
    effect = "Allow"
    actions = [
      "lambda:InvokeFunction",
      "lambda:GetFunctionConfiguration"
    ]
    resources = [
      aws_lambda_function.amp_publisher.arn
    ]
  }

  statement {
    effect = "Allow"
    actions = [
      "logs:PutLogEvents",
      "logs:CreateLogStream"
    ]
    resources = [
      aws_cloudwatch_log_group.project_logs.arn,
      "${aws_cloudwatch_log_group.project_logs.arn}:*"
    ]
  }

  statement {
    effect = "Allow"
    actions = [
      "s3:PutObject",
      "s3:GetBucketLocation",
      "s3:ListBucket"
    ]
    resources = [
      aws_s3_bucket.metrics_bucket.arn,
      "${aws_s3_bucket.metrics_bucket.arn}/*"
    ]
  }
}

# https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/iam_policy
resource "aws_iam_policy" "firehose_policy" {
  name        = "${title(var.prefix)}FirehosePolicy"
  description = "Policy to allow Firehose operations and CloudWatch logging"
  policy = data.aws_iam_policy_document.firehose_policy.json
}

# # https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/kinesis_stream
# resource "aws_kinesis_stream" "metrics" {
#   name             = "${var.prefix}-metrics"
#   # shard_count      = var.metrics_stream_shard_count
#   retention_period = 48 # Retain data for 48 hours
#   shard_level_metrics = [
#     "IncomingBytes",
#     "OutgoingBytes"
#   ]
#
#   stream_mode_details {
#     stream_mode = "ON_DEMAND"
#   }
# }

# https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/cloudwatch_log_stream
resource "aws_cloudwatch_log_stream" "metric_delivery_stream_logs" {
  name           = "${var.prefix}-metric-delivery-stream-logs"
  log_group_name = aws_cloudwatch_log_group.project_logs.name
}

# https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/kinesis_firehose_delivery_stream
resource "aws_kinesis_firehose_delivery_stream" "metrics" {
  name        = "${var.prefix}-metrics"
  destination = "extended_s3"

  # kinesis_source_configuration {
  #   kinesis_stream_arn = aws_kinesis_stream.metrics.arn
  #   role_arn           = aws_iam_role.firehose_role.arn
  # }

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
          parameter_value = aws_lambda_function.amp_publisher.arn
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

