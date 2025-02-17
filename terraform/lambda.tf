
# https://registry.terraform.io/providers/hashicorp/aws/latest/docs/data-sources/iam_policy_document
data "aws_iam_policy_document" "lambda_assume" {
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
resource "aws_iam_role" "amp_writer" {
  name               = "${title(var.prefix)}CloudMetricsAmpWriterRole"
  assume_role_policy = data.aws_iam_policy_document.lambda_assume.json
}

# https://registry.terraform.io/providers/hashicorp/aws/latest/docs/data-sources/iam_policy_document
data "aws_iam_policy_document" "amp_write" {
  statement {
    effect = "Allow"
    actions = [
      "aps:RemoteWrite"
    ]
    resources = [
      aws_prometheus_workspace.amp_workspace.arn
    ]
  }
  statement {
    effect = "Allow"
    actions = [
      "kms:Decrypt"
    ]
    resources = [
      "*"
    ]
  }
}

# https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/iam_policy
resource "aws_iam_policy" "amp_write" {
  name        = "${title(var.prefix)}AmpWritePolicy"
  description = "Policy to allow writing to an AMP endpoint"
  policy      = data.aws_iam_policy_document.amp_write.json
}

# https://registry.terraform.io/providers/hashicorp/aws/latest/docs/data-sources/iam_policy
data "aws_iam_policy" "lambda_execution" {
  name = "AWSLambdaBasicExecutionRole"
}

# https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/iam_role_policy_attachment
resource "aws_iam_role_policy_attachment" "amp_writer_execute" {
  role       = aws_iam_role.amp_writer.id
  policy_arn = data.aws_iam_policy.lambda_execution.arn
}

# https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/iam_role_policy_attachment
resource "aws_iam_role_policy_attachment" "amp_write" {
  role       = aws_iam_role.amp_writer.id
  policy_arn = aws_iam_policy.amp_write.arn
}

# https://registry.terraform.io/providers/hashicorp/aws/latest/docs/data-sources/lambda_function
resource "aws_lambda_function" "amp_publisher" {
  function_name    = "${var.prefix}-amp-publisher"
  runtime          = "java21"
  handler          = "com.adobe.aep.metrics.CloudWatchMetricsAmpWriter::handleRequest"
  role             = aws_iam_role.amp_writer.arn
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

# https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/lambda_permission
resource "aws_lambda_permission" "allow_firehose" {
  statement_id  = "AllowFirehoseInvoke"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.amp_publisher.function_name
  principal     = "firehose.amazonaws.com"
  source_arn    = aws_kinesis_firehose_delivery_stream.metrics.arn
}

# https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/iam_role
resource "aws_iam_role" "amp_query" {
  name               = "${title(var.prefix)}CloudMetricsAmpQueryRole"
  assume_role_policy = data.aws_iam_policy_document.lambda_assume.json
}

# https://registry.terraform.io/providers/hashicorp/aws/latest/docs/data-sources/iam_policy_document
data "aws_iam_policy_document" "amp_query" {
  statement {
    effect = "Allow"
    actions = [
      "aps:QueryMetrics"
    ]
    resources = [
      aws_prometheus_workspace.amp_workspace.arn
    ]
  }
}

# https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/iam_policy
resource "aws_iam_policy" "amp_query" {
  name        = "${title(var.prefix)}AmpQueryPolicy"
  description = "Policy to allow this lambda to query AMP for metrics"
  policy      = data.aws_iam_policy_document.amp_query.json
}

# https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/iam_role_policy_attachment
resource "aws_iam_role_policy_attachment" "amp_query_execute" {
  role       = aws_iam_role.amp_query.id
  policy_arn = data.aws_iam_policy.lambda_execution.arn
}

# https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/iam_role_policy_attachment
resource "aws_iam_role_policy_attachment" "lambda_logging" {
  role       = aws_iam_role.amp_query.name
  policy_arn = aws_iam_policy.amp_query.arn
}

locals {
  python_version   = "python3.12"
  requirements_dir = "${path.module}/requirements"
}

# https://registry.terraform.io/providers/hashicorp/archive/latest/docs/data-sources/file
data "archive_file" "amp_query_zip" {
  type        = "zip"
  source_file = "${path.module}/amp_query.py"
  output_path = "${path.module}/amp_query.zip"
}

# https://registry.terraform.io/providers/hashicorp/null/latest/docs/resources/resource
resource "null_resource" "pip_install_requirements" {
  # Run pip install if the target directory doesn't exist or changes.
  provisioner "local-exec" {
    command = "mkdir -p ${local.requirements_dir}/python && pip install -r requirements.txt -t ${local.requirements_dir}/python"
  }

  # You can use a trigger to force re-run if needed (for example, if you update the requirements)
  triggers = {
    hash = filebase64sha256("${path.module}/requirements.txt")
  }
}

# Package the Lambda layer from the 'layer' directory
data "archive_file" "requirements_layer_zip" {
  type        = "zip"
  source_dir  = local.requirements_dir
  output_path = "${path.module}/requirements.zip"

  # Ensure that the pip install step is done before creating the archive.
  depends_on = [null_resource.pip_install_requirements]
}

# https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/lambda_layer_version
resource "aws_lambda_layer_version" "requirements" {
  layer_name          = "requirements"
  compatible_runtimes = [local.python_version]
  filename            = data.archive_file.requirements_layer_zip.output_path
  depends_on          = [data.archive_file.requirements_layer_zip]
}

# https://registry.terraform.io/providers/hashicorp/aws/latest/docs/data-sources/lambda_function
resource "aws_lambda_function" "amp_query" {
  function_name = "${var.prefix}-amp-query"
  filename      = data.archive_file.amp_query_zip.output_path
  layers        = [aws_lambda_layer_version.requirements.arn]
  handler       = "amp_query.execute"
  runtime       = local.python_version
  role          = aws_iam_role.amp_query.arn

  environment {
    variables = {
      REGION       = var.aws_region
      AMP_ENDPOINT = aws_prometheus_workspace.amp_workspace.prometheus_endpoint
    }
  }

  logging_config {
    log_format = "Text"
    log_group  = aws_cloudwatch_log_group.project_logs.name
  }
  source_code_hash = data.archive_file.amp_query_zip.output_base64sha256
}

