import boto3
from botocore.exceptions import ClientError

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
                print(f"Error writing item {item}: {e.response['Error']['Message']}")

if __name__ == "__main__":
    # Example usage
    table_name = 'adobe-aep-test-metrics'  # Replace with your DynamoDB table name

    # List of Adobe services (repeated to ensure 1000 records)
    adobe_services = [
        'Adobe Photoshop', 'Adobe Illustrator', 'Adobe InDesign', 'Adobe Premiere Pro',
        'Adobe After Effects', 'Adobe Lightroom', 'Adobe XD', 'Adobe Spark',
        'Adobe Acrobat', 'Adobe Dreamweaver', 'Adobe Animate', 'Adobe Audition'
    ]

    # Generate 1000 unique items
    items = [
                {'metric_id': str(i), 'ServiceName': f"{service} {i//len(adobe_services)+1}", 'Description': f'Description for {service} {i//len(adobe_services)+1}'}
                for i, service in enumerate(adobe_services * (1000 // len(adobe_services) + 1), start=1)
            ][:1000]

    write_to_dynamodb(table_name, items)
    print("Data written to DynamoDB table successfully.")