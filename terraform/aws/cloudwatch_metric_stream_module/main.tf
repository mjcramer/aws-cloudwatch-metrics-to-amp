provider "aws" {
  region = "us-east-1" # Replace with your desired AWS region
}
# IAM Role for CloudWatch Metric Stream
resource "aws_iam_role" "metric_stream_role" {
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
# IAM Policy for Metric Stream Role
resource "aws_iam_role_policy" "metric_stream_policy" {
  name   = "metric_stream_policy"
  role   = aws_iam_role.metric_stream_role.name
  policy = jsonencode({
    Version = "2012-10-17",
    Statement = [
      {
        Effect   = "Allow",
        Action   = ["firehose:PutRecord", "firehose:PutRecordBatch",],
        Resource = "*"
      }
    ]
  })
}

# S3 Bucket for Firehose Destination
data "aws_s3_bucket" "firehose_bucket" {
bucket = "adobe-secure-metrics-data"
}

# Kinesis Firehose Delivery Stream
# resource "aws_kinesis_firehose_delivery_stream" "firehose_stream" {
#   name        = "cloudwatch-metric-stream"
#   destination = "extended_s3"
#   extended_s3_configuration {
#     role_arn   = "arn:aws:iam::471112885190:role/cloudwatch_metric_stream_role"
#     bucket_arn = "arn:aws:s3:::adobe-secure-metrics-data"
#   }
# }
# CloudWatch Metric Stream for DynamoDB Metrics
resource "aws_cloudwatch_metric_stream" "dynamodb_metric_stream" {
  name          = "dynamodb-metric-stream"
  role_arn      = "arn:aws:iam::471112885190:role/cloudwatch_metric_stream_role"
  firehose_arn  = "arn:aws:firehose:us-east-1:471112885190:deliverystream/KDS-S3-oBr67"
  output_format = "json"
  # Include specific DynamoDB metrics
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
# Alarm for ThrottledRequests
resource "aws_cloudwatch_metric_alarm" "throttled_requests" {
alarm_name          = "dynamodb-throttled-requests"
comparison_operator = "GreaterThanThreshold"
evaluation_periods  = 1
metric_name         = "ThrottledRequests"
namespace           = "AWS/DynamoDB"
period              = 60
statistic           = "Sum"
threshold           = 5
alarm_description   = "Triggers when throttled requests exceed 5."
}
# Alarm for SystemErrors
resource "aws_cloudwatch_metric_alarm" "system_errors" {
alarm_name          = "dynamodb-system-errors"
comparison_operator = "GreaterThanThreshold"
evaluation_periods  = 1
metric_name         = "SystemErrors"
namespace           = "AWS/DynamoDB"
period              = 60
statistic           = "Sum"
threshold           = 1
alarm_description   = "Triggers when system errors occur."
}