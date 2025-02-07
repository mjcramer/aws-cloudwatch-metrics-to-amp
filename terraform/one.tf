# CloudWatch Log Group
resource "aws_cloudwatch_log_group" "kinesis_log_group" {
  name              = "/aws/kinesis/test-stream-log-group"
  retention_in_days = 14  # Retain logs for 14 days
  tags = {
    Environment = "Dev"
  }
}
# CloudWatch Log Stream
resource "aws_cloudwatch_log_stream" "kinesis_log_stream" {
  name           = "test-kinesis-log-stream"
  log_group_name = aws_cloudwatch_log_group.kinesis_log_group.name
}
# IAM Policy for Kinesis and CloudWatch Logs
variable "region" {
  default = "us-east-1"
}
variable "account_id" {
  default = "471112885190"
}
resource "aws_iam_policy" "kinesis_access_policy" {
  name        = "KinesisAccessPolicy"
  description = "Policy to allow Kinesis stream operations and CloudWatch logging"
  policy      = jsonencode({
    Version = "2012-10-17",
    Statement = [
      # Allow Kinesis actions on the specific stream
      {
        Action = [
          "kinesis:CreateStream",
          "kinesis:DescribeStream",
          "kinesis:DescribeStreamSummary",
          "kinesis:ListStreams",
          "kinesis:ListShards",
          "kinesis:PutRecord",
          "kinesis:PutRecords",
          "kinesis:GetRecords",
          "kinesis:GetShardIterator"
        ],
        Effect   = "Allow",
        Resource = "arn:aws:kinesis:${var.region}:${var.account_id}:stream/${aws_kinesis_stream.test_stream.name}"
      },
      # Allow CloudWatch Logs actions on the specific log group
      {
        Action = [
          "logs:PutLogEvents",
          "logs:CreateLogStream"
        ],
        Effect   = "Allow",
        Resource = [
          aws_cloudwatch_log_group.kinesis_log_group.arn,
          "${aws_cloudwatch_log_group.kinesis_log_group.arn}:*"
        ]
      },
      # Allow Lambda execution permissions
      {
        Action = [
          "lambda:InvokeFunction",
          "lambda:GetFunctionConfiguration"
        ],
        Effect   = "Allow",
        Resource = aws_lambda_function.kinesis_lambda_function.arn
      }
    ]
  })
}
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
# Java Lambda Function to Process Kinesis Stream
resource "aws_lambda_function" "kinesis_lambda_function" {
  function_name = "kinesis-stream-processor"
  runtime       = "java11" # Specify the runtime as Java 11
  handler       = "com.example.LambdaHandler::handleRequest" # Update with your Java handler class
  role          = "arn:aws:iam::471112885190:role/lambda_execution_role"
  filename         = "lambda-handler-1.0-SNAPSHOT.jar"  # Path to the local .jar file
  source_code_hash = filebase64sha256("lambda-handler-1.0-SNAPSHOT.jar") # Hash for the jar file
  environment {
    variables = {
      ENV = "Dev"
    }
  }
  memory_size      = 512   # Adjust memory size as per function requirements
  timeout          = 30    # Set the timeout in seconds
  tags = {
    Environment = "Dev"
  }
}
# IAM Role for Lambda Execution
# data "aws_iam_role" "lambda_execution_role" {
#   name               = "lambda_execution_role"
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
# IAM Policy Attachment for Lambda Role
resource "aws_iam_role_policy_attachment" "lambda_basic_execution" {
  role       = "arn:aws:iam::471112885190:role/lambda_execution_roleS"
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}
# Kinesis Stream and Lambda Event Source Mapping
resource "aws_lambda_event_source_mapping" "kinesis_to_lambda" {
  event_source_arn = aws_kinesis_stream.test_stream.arn
  function_name    = aws_lambda_function.kinesis_lambda_function.arn
  starting_position = "LATEST"
}








