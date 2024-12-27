# cloudwatch-metrics-in-amp
Integrate Cloudwatch metrics in alerting in AMP
This repository contains Terraform modules to deploy a modular and reusable metrics pipeline in AWS. The architecture supports processing and monitoring CloudWatch metrics with components including DynamoDB, S3, Kinesis Firehose, Lambda, and Amazon Managed Prometheus.

## Architecture Overview
This architecture consists of the following key components:
![image](https://git.corp.adobe.com/storage/user/67828/files/984ba1f0-e680-4cfb-ba50-8effd866761d)


**Amazon DynamoDB** 
   Acts as the data source emitting metrics into the pipeline.
   
**Amazon CloudWatch Metric Stream** 
   Collects metric data from DynamoDB in the form of metric streams.
   
**Amazon Kinesis Data Firehose** 
   Receives metric stream data from CloudWatch and delivers data to Amazon S3.
   
**Amazon S3** 
   Stores processed metric data for archival or further analysis.
   
**Amazon Managed Service for Prometheus** 
   Provides monitoring and observability.
   
# AWS Lambda Handler for Kinesis Firehose Events

This repository contains an AWS Lambda function written in Java that processes Kinesis Firehose events. The function extracts metrics from the event records and pushes them to a Prometheus remote write URL.

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Setup and Deployment](#setup-and-deployment)
- [Configuration](#configuration)
- [Usage](#usage)
- [Testing](#testing)
- [Troubleshooting](#troubleshooting)
- [Contributing](#contributing)
- [License](#license)

## Overview

This Lambda function processes Kinesis Firehose events containing metric data. For each record, it extracts metrics, creates Prometheus gauge metrics, and pushes these metrics to a Prometheus remote write URL.

## Architecture

The main components of the Lambda function are:

- **LambdaHandler**: The main handler class implementing `RequestHandler<KinesisFirehoseEvent, KinesisFirehoseResponse>`.
- **MetricStreamData**: A class representing the metric data extracted from the Kinesis Firehose event records.
- **Value**: A class representing the metric values.
- **KinesisFirehoseResponse**: A class representing the response to be sent back to Kinesis Firehose.



## Setup and Deployment

### Prerequisites

- Java 8 or higher
- AWS CLI configured with appropriate permissions
- AWS Lambda execution role with access to Kinesis Firehose and CloudWatch Logs
- Prometheus remote write URL

### Building the Project

1. Clone the repository:

   ```sh
   git clone https://github.com/your-repo/aws-lambda-handler.git
   cd aws-lambda-handler
   ```
2. Build the project using Maven:

   ```sh
   mvn clean package
   ```
3. The output JAR file will be located in the `target` directory.

### Deploying the Lambda Function

1. Create a new Lambda function in the AWS Management Console.
2. Upload the JAR file from the `target` directory.
3. Set the handler to `com.example.LambdaHandler::handleRequest`.
4. Configure the Lambda function with the necessary environment variables (e.g., Prometheus remote write URL).
5. Set up a Kinesis Firehose delivery stream to trigger the Lambda function.

## Configuration

### Environment Variables

- `PROMETHEUS_REMOTEWRITE_URL`: The URL of the Prometheus PushGateway.

### IAM Permissions

Ensure the Lambda execution role has the necessary permissions to access Kinesis Firehose and CloudWatch Logs.

## Usage

The Lambda function will automatically process incoming Kinesis Firehose events, extract metrics, and push them to the Prometheus remote write URL. The metrics include:

- Count
- Sum
- Max
- Min

### Metric Naming Convention

Metrics are named using the format `<metric_name>_<metric_type>`, where `metric_type` can be `count`, `sum`, `max`, or `min`.

## Testing

### Unit Tests

Unit tests can be run using Maven:

```sh
mvn test
```
   
**Terraform Modules** 
   All resources are provisioned using reusable Terraform modules.
---

