
# https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/dynamodb_table
resource "aws_dynamodb_table" "metrics_table" {
  name         = "${var.prefix}-test-metrics"
  billing_mode = "PAY_PER_REQUEST"
  # read_capacity  = 20
  # write_capacity = 20
  hash_key = "metric_id"

  attribute {
    name = "metric_id"
    type = "S" # S for String
  }

  # Enable server-side encryption (default: AWS-managed key)
  server_side_encryption {
    enabled = true
  }

  # Add point-in-time recovery (backup best practice)
  point_in_time_recovery {
    enabled = true
  }
}
