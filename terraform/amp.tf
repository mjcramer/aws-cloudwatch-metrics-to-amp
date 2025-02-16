
# https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/prometheus_workspace
resource "aws_prometheus_workspace" "amp_workspace" {
  alias = "cloudwatch-metrics"

  logging_configuration {
    log_group_arn = "${aws_cloudwatch_log_group.project_logs.arn}:*"
  }
}

#
# data "aws_iam_policy_document" "grafana_assume_role" {
#   statement {
#     effect = "Allow"
#     actions = ["sts:AssumeRole"]
#
#     principals {
#       type        = "Service"
#       identifiers = [
#         "grafana.amazonaws.com"
#       ]
#     }
#   }
# }
#
# resource "aws_iam_role" "grafana_role" {
#   name = "${title(var.prefix)}GrafanaRole"
#   assume_role_policy = data.aws_iam_policy_document.grafana_assume_role.json
# }
#
# data "aws_iam_policy_document" "grafana_amp_access" {
#   statement {
#     effect = "Allow"
#     actions = [
#       "aps:QueryMetrics",
#       "aps:GetSeries",
#       "aps:RemoteWrite"
#     ]
#     resources = [
#       aws_prometheus_workspace.amp_workspace.arn
#     ]
#   }
# }
#
# resource "aws_iam_policy" "grafana_amp_policy" {
#   name        = "${title(var.prefix)}GrafanaAmpPolicy"
#   description = "Policy for AWS Managed Grafana to access AMP workspace"
#   policy      = data.aws_iam_policy_document.grafana_amp_access.json
# }
#
# # Attach the policy to the IAM role.
# resource "aws_iam_role_policy_attachment" "grafana_amp_attach" {
#   role       = aws_iam_role.grafana_role.name
#   policy_arn = aws_iam_policy.grafana_amp_policy.arn
# }
#
# # The "data_sources" argument with "PROMETHEUS" preconfigures a Prometheus data source.
# resource "aws_grafana_workspace" "cloudwatch_metrics" {
#   name                     = "${var.prefix}-cloudwatch-metrics"
#   permission_type = "CUSTOMER_MANAGED"
#   authentication_providers = [
#     "AWS_SSO"
#   ]
#   account_access_type      = "CURRENT_ACCOUNT"
#   role_arn                 = aws_iam_role.grafana_role.arn
#   data_sources             = ["PROMETHEUS"]
#   notification_destinations = ["SNS"]
#
#   tags = {
#     Environment = "dev"
#   }
# }
