# Metrics Pipeline with Terraform
This repository contains Terraform modules to deploy a modular and reusable metrics pipeline in AWS. The architecture supports processing and monitoring CloudWatch metrics with components including DynamoDB, S3, Kinesis Firehose, Lambda, and Amazon Managed Prometheus.
## Features
**DynamoDB**: Stores metric-related data for downstream processing.
**CloudWatch Metric Streams**: Streams metrics in near real-time to Kinesis Firehose.
**Kinesis Firehose**: Streams metrics data into an S3 bucket for storage.
**S3 Bucket**: Stores the streamed metrics data from Kinesis Firehose.
**Lambda Function**: Processes metric streams and forwards them to Amazon Managed Prometheus.
**Amazon Managed Prometheus**: Stores and visualizes metrics for observability and analysis.

## Modules
The project is broken into the following modular components for reusability:
[DynamoDB Module](./dynamodb_module): Deploys a DynamoDB table for metric storage.
[CloudWatch Metric Stream Module](./cloudwatch_metric_stream_module): Configures a CloudWatch Metric Stream to send metrics to Firehose.
[Kinesis Firehose Module](./kinesis_firehose_module): Sets up a Firehose stream for metrics data.
[S3 Module](./s3_module): Creates an S3 bucket to store the metrics.
[Lambda Module](./lambda_module): Deploys a Lambda function to process metrics and send them to Prometheus.
[Prometheus Module](./prometheus_module): Provisions an Amazon Managed Prometheus workspace.
