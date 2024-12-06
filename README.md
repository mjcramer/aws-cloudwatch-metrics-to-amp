# cloudwatch-metrics-in-amp
Integrate Cloudwatch metrics in alerting in AMP
This repository contains Terraform modules to deploy a modular and reusable metrics pipeline in AWS. The architecture supports processing and monitoring CloudWatch metrics with components including DynamoDB, S3, Kinesis Firehose, Lambda, and Amazon Managed Prometheus.
## Features
**DynamoDB**: Stores metric-related data for downstream processing.
**CloudWatch Metric Streams**: Streams metrics in near real-time to Kinesis Firehose.
**Kinesis Firehose**: Streams metrics data into an S3 bucket for storage.
**S3 Bucket**: Stores the streamed metrics data from Kinesis Firehose.
**Lambda Function**: Processes metric streams and forwards them to Amazon Managed Prometheus.
**Amazon Managed Prometheus**: Stores and visualizes metrics for observability and analysis.

