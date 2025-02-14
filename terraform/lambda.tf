
# https://registry.terraform.io/providers/hashicorp/aws/latest/docs/data-sources/iam_policy_document
data "aws_iam_policy_document" "lambda_assume_role" {
  statement {
    effect = "Allow"
    actions = [
      "sts:AssumeRole"
    ]

    principals {
      type        = "Service"
      identifiers = ["lambda.amazonaws.com"]
    }
  }
}

# https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/iam_role
resource "aws_iam_role" "lambda_role" {
  name               = "${title(var.prefix)}CloudMetricsAmpWriterRole"
  assume_role_policy = data.aws_iam_policy_document.lambda_assume_role.json
}

# https://registry.terraform.io/providers/hashicorp/aws/latest/docs/data-sources/iam_policy_document
data "aws_iam_policy_document" "lambda_policy" {
  statement {
    effect = "Allow"
    actions = [
      "aps:RemoteWrite"
    ]
    resources = [
      aws_prometheus_workspace.amp_workspace.arn
    ]
  }
}

# https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/iam_policy
resource "aws_iam_policy" "lambda_write" {
  name        = "${upper(var.prefix)}LambdaPolicy"
  description = "Policy to allow Kinesis stream operations and CloudWatch logging"
  policy      = data.aws_iam_policy_document.lambda_policy.json
}

# https://registry.terraform.io/providers/hashicorp/aws/latest/docs/data-sources/iam_policy
data "aws_iam_policy" "lambda_execution" {
  name = "AWSLambdaBasicExecutionRole"
}

# https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/iam_role_policy_attachment
resource "aws_iam_role_policy_attachment" "lambda_attachment" {
  role       = aws_iam_role.lambda_role.id
  policy_arn = data.aws_iam_policy.lambda_execution.arn
}

# https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/iam_role_policy_attachment
resource "aws_iam_role_policy_attachment" "lambda_attachment_2" {
  role       = aws_iam_role.lambda_role.id
  policy_arn = aws_iam_policy.lambda_write.arn
}

# https://registry.terraform.io/providers/hashicorp/aws/latest/docs/data-sources/lambda_function
resource "aws_lambda_function" "amp_publisher" {
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

resource "aws_lambda_permission" "allow_firehose" {
  statement_id  = "AllowFirehoseInvoke"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.amp_publisher.function_name
  principal     = "firehose.amazonaws.com"

  # Optionally, you can specify the source ARN if you want to restrict it to a specific Firehose stream
  source_arn = aws_kinesis_firehose_delivery_stream.metrics.arn
}

