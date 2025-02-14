
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
  name               = "${title(var.prefix)}FirehoseRole"
  assume_role_policy = data.aws_iam_policy_document.firehose_assume_role.json
}

# https://registry.terraform.io/providers/hashicorp/aws/latest/docs/data-sources/iam_policy_document
data "aws_iam_policy_document" "firehose_policy" {
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
  description = "Allows firehose to write to S3"
  policy = data.aws_iam_policy_document.firehose_policy.json
}

# https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/iam_role_policy_attachment
resource "aws_iam_role_policy_attachment" "firehose_policy" {
  role       = aws_iam_role.firehose_role.name
  policy_arn = aws_iam_policy.firehose_policy.arn
}

# https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/cloudwatch_log_stream
resource "aws_cloudwatch_log_stream" "metric_delivery_stream_logs" {
  name           = "${var.prefix}-metric-delivery-stream-logs"
  log_group_name = aws_cloudwatch_log_group.project_logs.name
}

# https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/kinesis_firehose_delivery_stream
resource "aws_kinesis_firehose_delivery_stream" "metrics" {
  name        = "${var.prefix}-metric-delivery-stream"
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
          parameter_value = aws_lambda_function.amp_publisher.arn
        }
      }
    }

    s3_backup_mode = "Disabled"
  }
}
