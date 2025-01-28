variable "aws_region" {
  default = "us-east-1"
}

variable "bucket_name" {
  default = "example-metrics-bucket"
}

variable "dynamodb_table_name" {
  default = "example-metrics-table"
}

variable "firehose_name" {
  default = "example-firehose-stream"
}

variable "metric_stream_name" {
  default = "example-metric-stream"
}

variable "lambda_function_name" {
  default = "cloudwatch-metric-processor"
}

variable "lambda_jar_file_key" {
  description = "S3 key for the Lambda JAR file"
}

variable "prometheus_remote_write_url" {
  description = "URL for Prometheus Remote Write"
}

variable "aws_amp_role_arn" {
  description = "IAM Role ARN for Prometheus integration"
}

variable "prometheus_workspace_id" {
  description = "Amazon Managed Prometheus workspace ID"
}

variable "lambda_role_name" {
  default = "lambda-role"
}

variable "firehose_role_name" {
  default = "firehose-role"
}

variable "common_tags" {
  default = {
    Environment = "dev"
    Project     = "CloudWatch-Metric-Stream"
  }
}
