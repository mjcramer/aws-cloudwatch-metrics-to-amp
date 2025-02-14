
# https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/iam_role
resource "aws_iam_role" "metric_stream" {
  name = "cloudwatch_metric_stream_role"
  assume_role_policy = jsonencode({
    Version = "2012-10-17",
    Statement = [{
      Effect    = "Allow",
      Principal = { Service = "streams.metrics.cloudwatch.amazonaws.com" },
      Action    = "sts:AssumeRole"
    }]
  })
}

# https://registry.terraform.io/providers/hashicorp/aws/latest/docs/data-sources/iam_policy_document
data "aws_iam_policy_document" "metric_stream_role_assume_role_policy" {
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
  name   = "CloudWatchMetricStream"
  role   = aws_iam_role.metric_stream.name
  policy = data.aws_iam_policy_document.metric_stream_role_assume_role_policy.json
}

# # https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/cloudwatch_log_stream
# resource "aws_cloudwatch_log_stream" "metric_stream" {
#   name           = "${var.prefix}-metric-stream-logs"
#   log_group_name = aws_cloudwatch_log_group.project_logs.name
# }

# https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/cloudwatch_metric_stream
resource "aws_cloudwatch_metric_stream" "metric_stream" {
  name          = "${var.prefix}-metric-stream"
  role_arn      = aws_iam_role.metric_stream.arn
  firehose_arn  = aws_kinesis_firehose_delivery_stream.metrics.arn
  output_format = "json"

  include_filter {
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