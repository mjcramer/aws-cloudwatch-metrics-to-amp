
locals {
  project_name = "cloudwatch-metrics-in-amp"
}

variable "prefix" {
  description = "The prefix which should be used for all resources in this project"
  default     = "c2a" // cloudwatch to amp
}

variable "environment" {
  description = "The environment name where this resource is deployed"
  default     = "Dev"
}

variable "owner" {
  description = "The team that will assume ownership of any created resources"
  default     = "Magda Miu team / Adobe"
}

variable "aws_account_id" {
  default = "240259995564"
}

variable "aws_region" {
  default = "us-west-2"
}

variable "project_logs_retention_in_days" {
  description = "The number of days to retain logs for this project"
  default = 5
}

variable "metrics_stream_shard_count" {
  description = "Number of shards for the metrics stream."
  default     = 1
}

variable "metrics_bucket_buffering_size" {
  description = "Buffer incoming data to the specified size, in MBs between 1 and 128, before delivering it to the destination."
  default     = 1
}

variable "metrics_bucket_buffering_interval" {
  description = "Buffer incoming data for the specified period of time, in seconds between 0 and 900, before delivering it to the destination."
  default     = 0
}

variable "lambda_writer_config" {
  description = "The lambda function resource configuration for the AMP writer"
  default = {
    jar_file = "../target/cloudwatch-metrics-amp-writer-1.0.jar"
    memory_size = 512
    timeout     = 60
  }
  validation {
    condition     = fileexists(var.lambda_writer_config.jar_file)
    error_message = "The specified file does not exist. Please provide a valid file path."
  }
}
