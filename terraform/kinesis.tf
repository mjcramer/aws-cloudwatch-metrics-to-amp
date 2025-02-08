
# CloudWatch Log Group
resource "aws_cloudwatch_log_group" "kinesis_log_group" {
  name              = "kinesis-logs"
  retention_in_days = 14  # Retain logs for 14 days
  tags = {
    Environment = "Dev"
  }
}

# CloudWatch Log Stream
resource "aws_cloudwatch_log_stream" "kinesis_log_stream" {
  name           = "kinesis-log-stream"
  log_group_name = aws_cloudwatch_log_group.kinesis_log_group.name
}

# resource "aws_iam_policy" "kinesis_access_policy" {
#   name        = "KinesisAccessPolicy"
#   description = "Policy to allow Kinesis stream operations and CloudWatch logging"
#   policy      = jsonencode({
#     Version = "2012-10-17",
#     Statement = [
#       # Allow Kinesis actions on the specific stream
#       {
#         Action = [
#           "kinesis:CreateStream",
#           "kinesis:DescribeStream",
#           "kinesis:DescribeStreamSummary",
#           "kinesis:ListStreams",
#           "kinesis:ListShards",
#           "kinesis:PutRecord",
#           "kinesis:PutRecords",
#           "kinesis:GetRecords",
#           "kinesis:GetShardIterator"
#         ],
#         Effect   = "Allow",
#         Resource = "arn:aws:kinesis:${var.region}:${var.account_id}:stream/${aws_kinesis_stream.test_stream.name}"
#       },
#       # Allow CloudWatch Logs actions on the specific log group
#       {
#         Action = [
#           "logs:PutLogEvents",
#           "logs:CreateLogStream"
#         ],
#         Effect   = "Allow",
#         Resource = [
#           aws_cloudwatch_log_group.kinesis_log_group.arn,
#           "${aws_cloudwatch_log_group.kinesis_log_group.arn}:*"
#         ]
#       },
#       # Allow Lambda execution permissions
#       {
#         Action = [
#           "lambda:InvokeFunction",
#           "lambda:GetFunctionConfiguration"
#         ],
#         Effect   = "Allow",
#         Resource = aws_lambda_function.kinesis_lambda_function.arn
#       }
#     ]
#   })
# }

# Kinesis Data Stream
resource "aws_kinesis_stream" "test_stream" {
  name             = "test-kinesis-stream"
  #shard_count      = 1
  retention_period = 48  # Retain data for 48 hours
  shard_level_metrics = [
    "IncomingBytes",
    "OutgoingBytes"
  ]
  stream_mode_details {
    stream_mode = "ON_DEMAND"
  }
  tags = {
    Environment = "Dev"
  }
}


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



# resource "aws_lambda_function" "kinesis_to_amp" {
#   function_name = "kinesis-to-amp"
#   runtime       = "java11" # Specify the runtime as Java 11
#   handler       = "com.example.LambdaHandler::handleRequest" # Update with your Java handler class
#   role          = aws_iam_role.kinesis_to_amp_role.arn
#   filename      = "kinesis-to-amp-1.0-SNAPSHOT.jar"
#   source_code_hash = filebase64sha256("kinesis-to-amp-1.0-SNAPSHOT.jar") # Hash for the jar file
#
#   environment {
#     variables = {
#       ENV = "Dev"
#     }
#   }
#   memory_size      = 512   # Adjust memory size as per function requirements
#   timeout          = 30    # Set the timeout in seconds
#   tags = {
#     Environment = "Dev"
#   }
# }

# Kinesis Stream and Lambda Event Source Mapping
# resource "aws_lambda_event_source_mapping" "kinesis_to_amp" {
#   event_source_arn = aws_kinesis_stream.test_stream.arn
#   function_name    = aws_lambda_function.kinesis_to_amp.arn
#   starting_position = "LATEST"
# }



resource "aws_kinesis_firehose_delivery_stream" "metric_firehose" {
    name        = var.firehose_name
    destination = "extended_s3"

    extended_s3_configuration {
        role_arn           = var.firehose_role_arn
        bucket_arn         = var.s3_bucket_arn
        buffering_size     = var.buffering_size     # In MiB, between 1-128
        buffering_interval = var.buffering_interval # In seconds, between 60-900
        compression_format = var.compression_format # GZIP, Snappy, ZIP, or UNCOMPRESSED
        
        cloudwatch_logging_options {
            enabled         = var.cloudwatch_logging_enabled
            log_group_name  = var.log_group_name
            log_stream_name = var.log_stream_name
        }

        processing_configuration {
            enabled = var.processing_enabled

            processors {
                type = "Lambda"
                
                parameters {
                    parameter_name  = "LambdaArn"
                    parameter_value = var.lambda_arn
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

    server_side_encryption {
        enabled  = var.sse_enabled
        key_type = var.sse_key_type # can be "AWS_OWNED_CMK" or "CUSTOMER_MANAGED_CMK"
    }

    tags = var.tags
}

# Required variables
variable "firehose_name" {
    type        = string
    description = "Name of the Kinesis Firehose delivery stream"
}

variable "s3_bucket_arn" {
    type        = string
    description = "ARN of the destination S3 bucket"
}

variable "firehose_role_arn" {
    type        = string
    description = "ARN of the IAM role that Kinesis Firehose can assume"
}

# Optional variables with defaults
variable "buffering_interval" {
    type        = number
    default     = 300
    description = "Buffer incoming data for the specified period of time, in seconds"

    validation {
        condition     = var.buffering_interval >= 60 && var.buffering_interval <= 900
        error_message = "Buffering interval must be between 60 and 900 seconds."
    }
}

variable "buffering_size" {
    type        = number
    default     = 5
    description = "Buffer incoming data to the specified size, in MiBs"

    validation {
        condition     = var.buffering_size >= 1 && var.buffering_size <= 128
        error_message = "Buffering size must be between 1 and 128 MiBs."
    }
}
