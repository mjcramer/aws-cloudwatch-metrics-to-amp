# cloudwatch-metrics-in-amp
Integrate Cloudwatch metrics in alerting in AMP
This repository contains Terraform modules to deploy a modular and reusable metrics pipeline in AWS. The architecture supports processing and monitoring CloudWatch metrics with components including DynamoDB, S3, Kinesis Firehose, Lambda, and Amazon Managed Prometheus.

## Architecture Overview
This architecture consists of the following key components:

**Amazon DynamoDB** 
   Acts as the data source emitting metrics into the pipeline.
**Amazon CloudWatch Metric Stream** 
   Collects metric data from DynamoDB in the form of metric streams.
**Amazon Kinesis Data Firehose** 
   Receives metric stream data from CloudWatch and delivers data to Amazon S3.
**Amazon S3** 
   Stores processed metric data for archival or further analysis.
**AWS Lambda (Java)** 
   Processes the logs and transforms them into a format compatible with Prometheus metrics.
**Amazon Managed Service for Prometheus** 
   Provides monitoring and observability.
**Terraform Modules** 
   All resources are provisioned using reusable Terraform modules.
---

