import os
import json
import boto3
import requests
from datetime import datetime
from requests_aws4auth import AWS4Auth

query_param = "query"


def execute(event, context):

    # Extract the metric query from the event payload.
    promql_query = event.get(query_param)
    if not promql_query:
        return f"Missing '{query_param}' in event payload"
    print(f"PromQL query = '{promql_query}'")

    # Retrieve the AMP endpoint from environment variables.
    amp_endpoint = os.environ.get("AMP_ENDPOINT").rstrip("/")
    if not amp_endpoint:
        return "AMP_ENDPOINT environment variable not set"
    # Construct the URL for the instant query endpoint.
    query_url = f"{amp_endpoint}/api/v1/query"
    print(f"query url = {query_url}")

    # Get region and service details
    region = os.environ.get("REGION", "us-east-1")
    service = "aps"  # For AMP
    # Create a session and get credentials
    session = boto3.Session()
    credentials = session.get_credentials().get_frozen_credentials()
    # Create the AWS4Auth object
    auth = AWS4Auth(credentials.access_key,
                    credentials.secret_key,
                    region,
                    service,
                    session_token=credentials.token)

    query_time = datetime.utcnow().isoformat("T") + "Z"
    params = {
        "query": promql_query,
        "time": query_time
    }

    try:
        # Make the signed GET request
        response = requests.get(query_url, auth=auth, params=params)
        response.raise_for_status()  # Will raise an error if the response was unsuccessful

    except requests.RequestException as e:
        return f"Error querying AMP: {str(e)}"

    # Return the AMP response.
    result = response.json()
    if not result["status"] == "success":
        return f"AMP query returned: {result}"
    return result["data"]
