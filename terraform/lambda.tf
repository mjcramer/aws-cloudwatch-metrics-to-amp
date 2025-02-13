
# https://registry.terraform.io/providers/hashicorp/aws/latest/docs/data-sources/iam_policy_document
data "aws_iam_policy_document" "lambda_assume_role" {
  statement {
    effect = "Allow"

    principals {
      type        = "Service"
      identifiers = ["lambda.amazonaws.com"]
    }

    actions = ["sts:AssumeRole"]
  }
}

# https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/iam_role
resource "aws_iam_role" "lambda_role" {
  name               = "${upper(var.prefix)}LambdaRole"
  assume_role_policy = data.aws_iam_policy_document.lambda_assume_role.json
}

# resource "aws_iam_policy" "kinesis_policy" {
#   name        = "KtaKinesisPolicy"
#   description = "Policy to allow Kinesis stream operations and CloudWatch logging"
#   policy = jsonencode({
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
#         Resource = "arn:aws:kinesis:${var.region}:${var.account_id}:stream/${aws_kinesis_stream.metrics_stream.name}"
#       },
#       # Allow CloudWatch Logs actions on the specific log group
#       {
#         Action = [
#           "logs:PutLogEvents",
#           "logs:CreateLogStream"
#         ],
#         Effect = "Allow",
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
#         Resource = aws_lambda_function.kinesis_to_amp.arn
#       }
#     ]
#   })
# }


# resource "aws_iam_policy" "amp_policy" {
#   name        = "KtaAmpPolicy"
#   description = "Allows operations on Amazon Managed Prometheus"
#
#   policy = jsonencode({
#     Version = "2012-10-17"
#     Statement = [{
#       Effect = "Allow"
#       Action = [
#         "aps:*"
#       ]
#       Resource = "*"
#     }]
#   })
# }
#
# resource "aws_iam_role_policy_attachment" "amp_attachment" {
#   role       = aws_iam_role.lambda_role.id
#   policy_arn = aws_iam_policy.amp_policy.arn
# }
#
# resource "aws_iam_role_policy_attachment" "kinesis_attachment" {
#   role       = aws_iam_role.lambda_role.id
#   policy_arn = aws_iam_policy.kinesis_policy.arn
# }

# # https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/cloudwatch_log_stream
# resource "aws_cloudwatch_log_stream" "kinesis_log_stream" {
#  name           = "test-kinesis-log-stream"
#  log_group_name = aws_cloudwatch_log_group.project_logs.name
# }


resource "aws_lambda_function" "kinesis_to_amp" {
  function_name    = "${var.prefix}-amp-publisher"
  runtime          = "java11"
  handler          = "com.adobe.aep.metrics.CloudWatchMetricsAmpWriter::handleRequest"
  role             = aws_iam_role.lambda_role.arn
  filename         = var.lambda_writer_config.jar_file
  source_code_hash = filebase64sha256(var.lambda_writer_config.jar_file)

  environment {
    variables = {
      REGION       = var.aws_region
      WORKSPACE_ID = aws_prometheus_workspace.amp_workspace.id
    }
  }

  logging_config {
    log_format = "Text"
    log_group  = aws_cloudwatch_log_group.project_logs.name
  }

  memory_size = var.lambda_writer_config.memory_size
  timeout     = var.lambda_writer_config.timeout
}


# Kinesis Stream and Lambda Event Source Mapping
# resource "aws_lambda_event_source_mapping" "kinesis_to_amp" {
#   event_source_arn = aws_kinesis_stream.test_stream.arn
#   function_name    = aws_lambda_function.kinesis_to_amp.arn
#   starting_position = "LATEST"
# }



# resource "aws_lambda_function" "kinesis_stream_processor" {
#  function_name = "kinesis-stream-processor"
#  role          = aws_iam_role.lambda_execution_role.arn
#
#  # Runtime and handler configuration
#  runtime = "java11"
#  handler = "com.example.LambdaHandler::handleRequest"
#
#  # Resource configurations
#  memory_size = 512
#  timeout     = 300
#
#  # Storage and package configuration
#  package_type = "Zip"
#  ephemeral_storage {
#    size = 512
#  }
#
#  # Logging configuration
#  logging_config {
#    log_format = "Text"
#    log_group  = "/aws/lambda/kinesis-stream-processor"
#  }
#
#  # Tracing configuration
#  tracing_config {
#    mode = "PassThrough"
#  }
#
#  # Environment variables
#  environment {
#    variables = {
#      AWS_AMP_ROLE_ARN            = aws_iam_role.lambda_execution_role.arn
#      DEFAULT_AWS_REGION          = "us-east-1"
#      PROMETHEUS_REMOTE_WRITE_URL = "https://aps-workspaces.us-east-1.amazonaws.com/workspaces/ws-14e20584-7c40-48f5-a8f4-fd3e772d82d4/api/v1/remote_write" // Change to your actually endpoint.
#    }
#  }
#
#  # Architecture specification
#  architectures = ["x86_64"]
#
#  # Tags
#  tags = {
#    Environment = "Dev"
#  }
#}
#
## IAM Role Resource (inferred from the role ARN)
#resource "aws_iam_role" "lambda_execution_role" {
#  name = "lambda_execution_role"
#
#  assume_role_policy = jsonencode({
#    Version = "2012-10-17"
#    Statement = [
#      {
#        Action = "sts:AssumeRole"
#        Effect = "Allow"
#        Principal = {
#          Service = "lambda.amazonaws.com"
#        }
#      }
#    ]
#  })
#}


## IAM Role for Lambda Execution
## data "aws_iam_role" "lambda_execution_role" {
##   name               = "lambda_execution_role"
##   assume_role_policy = jsonencode({
##     Version = "2012-10-17",
##     Statement = [
##       {
##         Action = "sts:AssumeRole",
##         Effect = "Allow",
##         Principal = {
##           Service = "lambda.amazonaws.com"
##         }
##       }
##     ]
##   })
## }
## IAM Policy Attachment for Lambda Role
#resource "aws_iam_role_policy_attachment" "lambda_basic_execution" {
#  role       = "arn:aws:iam::471112885190:role/lambda_execution_roleS"
#  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
#}
## Kinesis Stream and Lambda Event Source Mapping
#resource "aws_lambda_event_source_mapping" "kinesis_to_lambda" {
#  event_source_arn = aws_kinesis_stream.test_stream.arn
#  function_name    = aws_lambda_function.kinesis_lambda_function.arn
#  starting_position = "LATEST"
#}