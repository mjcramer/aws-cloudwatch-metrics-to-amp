
# https://registry.terraform.io/providers/hashicorp/aws/latest/docs/data-sources/iam_policy_document
data "aws_iam_policy_document" "metric_stream_assume_role" {
  statement {
    effect = "Allow"
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = [
        "streams.metrics.cloudwatch.amazonaws.com"
      ]
    }
  }
}

# https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/iam_role
resource "aws_iam_role" "metric_stream" {
  name = "${title(var.prefix)}CloudWatchMetricsStreamRole"
  assume_role_policy = data.aws_iam_policy_document.metric_stream_assume_role.json
}

# https://registry.terraform.io/providers/hashicorp/aws/latest/docs/data-sources/iam_policy_document
data "aws_iam_policy_document" "metric_stream_policy" {
  statement {
    effect  = "Allow"
    actions = [
      "firehose:PutRecord",
      "firehose:PutRecordBatch"
    ]
    resources = [
      "*"
    ]
  }
}

# https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/iam_policy
resource "aws_iam_role_policy" "metric_stream_policy" {
  name   = "${title(var.prefix)}CloudWatchMetricStreamPolicy"
  role   = aws_iam_role.metric_stream.name
  policy = data.aws_iam_policy_document.metric_stream_policy.json
}

# https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/cloudwatch_metric_stream
resource "aws_cloudwatch_metric_stream" "metric_stream" {
  name          = "${var.prefix}-metric-stream"
  role_arn      = aws_iam_role.metric_stream.arn
  firehose_arn  = aws_kinesis_firehose_delivery_stream.metrics.arn
  output_format = "json"

  include_filter {

    # dimensions {
    #   name  = "TableName"
    #   value = "my-dynamodb-table"
    # }
    namespace = "AWS/DynamoDB"
    metric_names = [
      "ConditionalCheckFailedRequests",
      "ConsumedReadCapacityUnits",
      "ConsumedWriteCapacityUnits",
      "ReadThrottleEvents",
      "ReturnedBytes",
      "ReturnedItemCount",
      "ReturnedRecordsCount",
      "SuccessfulRequestLatency",
      "SystemErrors",
      "TimeToLiveDeletedItemCount",
      "ThrottledRequests",
      "UserErrors",
      "WriteThrottleEvents",
      "OnDemandMaxReadRequestUnits",
      "OnDemandMaxWriteRequestUnits",
      "AccountMaxReads",
      "AccountMaxTableLevelReads",
      "AccountMaxTableLevelWrites",
      "AccountMaxWrites",
      "ThrottledPutRecordCount"
    ]
  }
}

# https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/cloudwatch_metric_alarm
resource "aws_cloudwatch_metric_alarm" "throttled_requests" {
  alarm_name          = "${var.prefix}-throttled-requests"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  metric_name         = "ThrottledRequests"
  namespace           = "AWS/DynamoDB"
  period              = 60
  statistic           = "Sum"
  threshold           = 5
  alarm_description   = "Triggers when throttled requests exceed 5."
}

# https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/cloudwatch_metric_alarm
resource "aws_cloudwatch_metric_alarm" "system_errors" {
  alarm_name          = "${var.prefix}-system-errors"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  metric_name         = "SystemErrors"
  namespace           = "AWS/DynamoDB"
  period              = 60
  statistic           = "Sum"
  threshold           = 1
  alarm_description   = "Triggers when system errors occur."
}