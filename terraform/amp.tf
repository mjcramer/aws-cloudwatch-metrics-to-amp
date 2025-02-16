
# https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/prometheus_workspace
resource "aws_prometheus_workspace" "amp_workspace" {
  alias = "cloudwatch-metrics"

  logging_configuration {
    log_group_arn = "${aws_cloudwatch_log_group.project_logs.arn}:*"
  }
}

# https://registry.terraform.io/providers/hashicorp/local/latest/docs/resources/file
resource "local_file" "amp_query_url" {
  filename = "${path.root}/../amp.txt"
  content  = aws_prometheus_workspace.amp_workspace.prometheus_endpoint
}