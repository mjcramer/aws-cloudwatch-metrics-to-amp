#!/usr/bin/env python3

import sys
import time
import json
import boto3
from botocore.exceptions import ClientError
from datetime import datetime, timedelta


def write_to_dynamodb(table_name, items):
    """
    Write a large volume of data to a DynamoDB table using batch writing.

    Parameters:
    table_name (str): The name of the DynamoDB table.
    items (list): A list of dictionaries containing the items to write to the table.
    """
    # Create a DynamoDB resource
    dynamodb = boto3.resource('dynamodb')
    table = dynamodb.Table(table_name)

    # Batch write items to the table
    with table.batch_writer() as batch:
        for item in items:
            try:
                batch.put_item(Item=item)
            except ClientError as e:
                print(f"Error writing item {item}: {e.response['Error']['Message']}\n")


if __name__ == "__main__":

    if len(sys.argv) > 1:
        # sys.argv[0] is the script name, so the first argument is at index 1
        table_name = sys.argv[1]
    else:
        print("Please provide a table name.\n")
        sys.exit(1)

    # List of Adobe services (repeated to ensure 1000 records)
    adobe_services = [
        'Adobe Photoshop',
        'Adobe Illustrator',
        'Adobe InDesign',
        'Adobe Premiere Pro',
        'Adobe After Effects',
        'Adobe Lightroom',
        'Adobe XD',
        'Adobe Spark',
        'Adobe Acrobat',
        'Adobe Dreamweaver',
        'Adobe Animate',
        'Adobe Audition'
    ]

    # try:
    #     print("Metric generation is running. Hit CTRL-C to stop.\n")
    #     while True:
    #         items = [
    #             {
    #                 'metric_id': str(i),
    #                 'ServiceName': f"{service} {i // len(adobe_services) + 1}",
    #                 'Description': f'Description for {service} {i // len(adobe_services) + 1}'
    #             }
    #             for i, service in enumerate(adobe_services * (1000 // len(adobe_services) + 1), start=1)
    #         ][:1000]
    #         write_to_dynamodb(table_name, items)
    #         print(f"Put {len(items)} items to DynamoDB table successfully.\n")
    #         time.sleep(0.5)
    #
    # except KeyboardInterrupt:
    #     print("Metrics generation terminated.\n")

    # Initialize the Lambda client
    lambda_client = boto3.client('lambda', region_name='us-east-1')  # adjust region as needed

    # Define the payload to send to your Lambda function
    payload = {
        "query": "ConsumedWriteCapacityUnits[15m]"
    }

    try:
        # Invoke the Lambda function
        response = lambda_client.invoke(
            FunctionName='c2a-amp-query',  # Replace with your Lambda function name
            InvocationType='RequestResponse',         # For synchronous invocation
            Payload=json.dumps(payload)                 # Payload must be JSON serialized
        )

        # Read the response from the Lambda function
        response_payload = response['Payload'].read()
        result = json.loads(response_payload)
        print("Lambda response:", json.dumps(result, indent=2))

    except Exception as e:
        print("Error invoking Lambda:", e)

    values = result['result'][0]['values']
    for i in range(len(values)):
        timestamp = values[i][0]
        value = values[i][1]
        print(f"Got data point {value} at time {datetime.fromtimestamp(timestamp).strftime('%Y-%m-%d %H:%M:%S')}")

                                       nkjjjkkkkkkkkkkkkkkkkkk  kkkk  kkk  ,    ,,,,,,,,, ,,,,,,,,,,,,,,,