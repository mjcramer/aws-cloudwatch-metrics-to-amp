## Create prometheus workspace, log group and alert manager. Change to name to what you want to name it.

resource "aws_prometheus_workspace" "amp_workspace" {
   alias = "aep1-stage-va6-amp-poc"
   
   tags = {
       "Adobe.Environment" = "stage"
       "Adobe.Location"    = "va6"
       "Adobe.Region"      = "us-east-1"
       "Adobe.ArchPath"    = ""
       "Adobe.Component"   = "" 
   }

   logging_configuration {
       log_group_arn = "${aws_cloudwatch_log_group.prometheus_logs.arn}:*"
   }
}

resource "aws_cloudwatch_log_group" "prometheus_logs" {
   name = "aws-managed-service-prometheus-complete"
}

resource "aws_prometheus_alert_manager_definition" "alerts" {
   workspace_id = aws_prometheus_workspace.amp_workspace.id
   
   definition = <<-EOT
       alertmanager_config: |
         route:
           receiver: 'default'
         receivers:
           - name: 'default'
   EOT
   }