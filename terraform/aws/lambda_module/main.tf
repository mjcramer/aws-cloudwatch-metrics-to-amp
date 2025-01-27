resource "aws_lambda_function" "kinesis_stream_processor" {
  function_name = "kinesis-stream-processor"
  role          = aws_iam_role.lambda_execution_role.arn
  
  # Runtime and handler configuration
  runtime = "java11"
  handler = "com.example.LambdaHandler::handleRequest"
  
  # Resource configurations
  memory_size = 512
  timeout     = 300
  
  # Storage and package configuration
  package_type = "Zip"
  ephemeral_storage {
    size = 512
  }
  
  # Logging configuration
  logging_config {
    log_format = "Text"
    log_group  = "/aws/lambda/kinesis-stream-processor"
  }
  
  # Tracing configuration
  tracing_config {
    mode = "PassThrough"
  }
  
  # Environment variables
  environment {
    variables = {
      AWS_AMP_ROLE_ARN            = aws_iam_role.lambda_execution_role.arn
      DEFAULT_AWS_REGION          = "us-east-1"
      PROMETHEUS_REMOTE_WRITE_URL = "https://aps-workspaces.us-east-1.amazonaws.com/workspaces/ws-14e20584-7c40-48f5-a8f4-fd3e772d82d4/api/v1/remote_write" // Change to your actually endpoint.
    }
  }
  
  # Architecture specification
  architectures = ["x86_64"]
  
  # Tags
  tags = {
    Environment = "Dev"
  }
}

# IAM Role Resource (inferred from the role ARN)
resource "aws_iam_role" "lambda_execution_role" {
  name = "lambda_execution_role"
  
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "lambda.amazonaws.com"
        }
      }
    ]
  })
}