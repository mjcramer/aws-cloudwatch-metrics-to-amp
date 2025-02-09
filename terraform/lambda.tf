
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
  name               = "KTATestLambdaRole"
  assume_role_policy = data.aws_iam_policy_document.lambda_assume_role.json
}

resource "aws_iam_policy" "lambda_policy" {
  name        = "KTATestLambdaPolicy"
  description = "Allows Lambda to write to Amazon Managed Prometheus"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = [
        "aps:*"
      ]
      Resource = "*"
    }]
  })
}

# IAM Policy Attachment for Lambda Role
resource "aws_iam_role_policy_attachment" "lambda" {
  role       = aws_iam_role.lambda_role.id
  policy_arn = aws_iam_policy.lambda_policy.arn
}

resource "aws_lambda_function" "kinesis_to_amp" {
  function_name    = "kinesis-to-amp"
  runtime          = "java11"
  handler          = "com.example.KinesisToPrometheusLambda::handleRequest"
  role             = aws_iam_role.lambda_role.arn
  filename         = var.lambda_jar
  source_code_hash = filebase64sha256(var.lambda_jar)

  environment {
    variables = {
      ENV = "Dev"
      REGION = var.region
      WORKSPACE_ID = aws_prometheus_workspace.amp_workspace.id
    }
  }

  logging_config {
    log_format = "Text"
    log_group = aws_cloudwatch_log_group.lambda_log_group.id
  }

  memory_size = 512 # Adjust memory size as per function requirements
  timeout     = 30  # Set the timeout in seconds
}


# Kinesis Stream and Lambda Event Source Mapping
# resource "aws_lambda_event_source_mapping" "kinesis_to_amp" {
#   event_source_arn = aws_kinesis_stream.test_stream.arn
#   function_name    = aws_lambda_function.kinesis_to_amp.arn
#   starting_position = "LATEST"
# }
