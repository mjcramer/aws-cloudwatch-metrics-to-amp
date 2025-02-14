
# https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/prometheus_workspace
resource "aws_prometheus_workspace" "amp_workspace" {
  alias = "cloudwatch-metrics"

  logging_configuration {
    log_group_arn = "${aws_cloudwatch_log_group.project_logs.arn}:*"
  }
}

# resource "aws_prometheus_alert_manager_definition" "alerts" {
#    workspace_id = aws_prometheus_workspace.amp_workspace.id
#
#    definition = <<-EOT
# alertmanager_config: |
#   route:
#     receiver: 'default'
#     receivers:
#       - name: 'default'
# EOT
# }
