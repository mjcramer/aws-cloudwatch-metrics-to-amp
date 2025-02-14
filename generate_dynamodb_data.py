#!/usr/bin/env python3

import json
import random
import uuid
from datetime import datetime, timedelta

def generate_metrics_data(num_records=100):
    """Generate test records with DynamoDB metrics"""
    records = []
    
    for i in range(num_records):
        # Generate timestamp within last hour
        current_time = datetime.utcnow() - timedelta(minutes=random.randint(0, 60))
        
        # Create record with all specified metrics
        record = {
            "PutRequest": {
                "Item": {
                    "id": {"S": str(uuid.uuid4())},
                    "timestamp": {"S": current_time.isoformat()},
                    "metrics": {"M": {
                        "ConditionalCheckFailedRequests": {"N": str(random.randint(0, 10))},
                        "ConsumedReadCapacityUnits": {"N": str(random.uniform(1.0, 100.0))},
                        "ConsumedWriteCapacityUnits": {"N": str(random.uniform(1.0, 100.0))},
                        "ReadThrottleEvents": {"N": str(random.randint(0, 5))},
                        "ReturnedBytes": {"N": str(random.randint(1000, 50000))},
                        "ReturnedItemCount": {"N": str(random.randint(1, 100))},
                        "ReturnedRecordsCount": {"N": str(random.randint(1, 100))},
                        "SuccessfulRequestLatency": {"N": str(random.uniform(0.1, 2.0))},
                        "SystemErrors": {"N": str(random.randint(0, 3))},
                        "TimeToLiveDeletedItemCount": {"N": str(random.randint(0, 50))},
                        "ThrottledRequests": {"N": str(random.randint(0, 10))},
                        "UserErrors": {"N": str(random.randint(0, 5))},
                        "WriteThrottleEvents": {"N": str(random.randint(0, 5))},
                        "OnDemandMaxReadRequestUnits": {"N": str(random.randint(100, 1000))},
                        "OnDemandMaxWriteRequestUnits": {"N": str(random.randint(100, 1000))},
                        "AccountMaxReads": {"N": str(random.randint(1000, 5000))},
                        "AccountMaxTableLevelReads": {"N": str(random.randint(1000, 5000))},
                        "AccountMaxTableLevelWrites": {"N": str(random.randint(1000, 5000))},
                        "AccountMaxWrites": {"N": str(random.randint(1000, 5000))},
                        "ThrottledPutRecordCount": {"N": str(random.randint(0, 10))}
                    }},
                    "table_name": {"S": "metrics-dynamodb-table"},
                    "region": {"S": random.choice(['us-east-1', 'us-west-2', 'eu-west-1'])},
                    "operation_type": {"S": random.choice(['read', 'write', 'query', 'scan'])}
                }
            }
        }
        records.append(record)
    
    # Create batches (DynamoDB BatchWriteItem limit is 25 items)
    batch_size = 25
    batches = []
    
    for i in range(0, len(records), batch_size):
        batch = {
            "metrics-dynamodb-table": records[i:i + batch_size]
        }
        batches.append(batch)
    
    # Write to JSON file
    output_file = "dynamodb_metrics_data.json"
    with open(output_file, 'w') as f:
        json.dump(batches, f, indent=2)
    
    # Create shell script to load the data
    cli_command = """#!/bin/bash
# Load data using AWS CLI
for i in $(seq 0 {last_batch}); do
    aws dynamodb batch-write-item --request-items "$(cat dynamodb_metrics_data.json | jq ".[$i]")"
    echo "Processed batch $i"
    sleep 1  # Prevent throttling
done
""".format(last_batch=len(batches)-1)
    
    with open('load_metrics_data.sh', 'w') as f:
        f.write(cli_command)
    
    print(f"Generated {num_records} records in {len(batches)} batches")
    print("Files created:")
    print("- dynamodb_metrics_data.json: Contains the test data")
    print("- load_metrics_data.sh: Script to load the data into DynamoDB")

if __name__ == "__main__":
    generate_metrics_data(100)  # Generate 100 records by default
