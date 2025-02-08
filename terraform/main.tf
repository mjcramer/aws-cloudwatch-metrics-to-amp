
provider "aws" {
  region = "us-west-2"  # Change to your desired AWS region
  profile = "default"
}

variable "region" {
  default = "us-west-2"
}

variable "account_id" {
  default = "240259995564"
}



# # 🔹 S3 Bucket for Firehose
# resource "aws_s3_bucket" "firehose_bucket" {
#   bucket = "my-kinesis-firehose-bucket"
# }
#
# # 🔹 IAM Role for Firehose
# resource "aws_iam_role" "firehose_role" {
#   name = "firehose_delivery_role"
#
#   assume_role_policy = jsonencode({
#     Version = "2012-10-17"
#     Statement = [
#       {
#         Effect = "Allow"
#         Principal = {
#           Service = "firehose.amazonaws.com"
#         }
#         Action = "sts:AssumeRole"
#       }
#     ]
#   })
# }
#
# # 🔹 IAM Policy for Firehose
# resource "aws_iam_policy" "firehose_policy" {
#   name        = "firehose_s3_policy"
#   description = "Allows Firehose to write to S3"
#
#   policy = jsonencode({
#     Version = "2012-10-17"
#     Statement = [
#       {
#         Effect = "Allow"
#         Action = [
#           "s3:PutObject",
#           "s3:GetBucketLocation",
#           "s3:ListBucket"
#         ]
#         Resource = [
#           aws_s3_bucket.firehose_bucket.arn,
#           "${aws_s3_bucket.firehose_bucket.arn}/*"
#         ]
#       }
#     ]
#   })
# }

# # 🔹 Attach IAM Policy to Role
# resource "aws_iam_role_policy_attachment" "firehose_policy_attach" {
#   role       = aws_iam_role.firehose_role.name
#   policy_arn = aws_iam_policy.firehose_policy.arn
# }
#
# # 🔹 Kinesis Firehose Delivery Stream
# resource "aws_kinesis_firehose_delivery_stream" "firehose" {
#   name        = "my-firehose-stream"
#   destination = "s3"
#
#   s3_configuration {
#     role_arn   = aws_iam_role.firehose_role.arn
#     bucket_arn = aws_s3_bucket.firehose_bucket.arn
#     buffer_size = 5
#     buffer_interval = 300
#     compression_format = "GZIP"
#   }
# }
#
