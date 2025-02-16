#!/usr/bin/env bash

script_dir=$(cd "$(dirname $0)" || exit; pwd -P)

amp="amp.txt"
region="us-east-1"

usage() {
    echo "${script_dir}: This script does wonderful things!"
    echo "Usage: ${0##*/} [flags] <parameter1> <parameter2> ..."
    echo "  -h          Print usage instructions"
    echo "  -a          Option A switch"
    echo "  -r OPTARG   AWS region (default: $region)"
}

param() {
  if [ -z "$1" ]; then
    echo "Missing parameter: $2"
    exit 1
  else
    return $1
  fi
}

while getopts ":har:" opt; do
    case $opt in
    h)
        usage
        exit 0
        ;;
    a)
        A=true
        ;;
    r)
        region=$OPTARG
        ;;
    \?)
        echo "Invalid option: -$opt" >&2
        exit 1
        ;;
    :)
        echo "Option -$opt requires an argument." >&2
        exit 1
        ;;
    esac
done
shift $(($OPTIND - 1))

amp_url=$(cat amp.txt)
# Remove a trailing newline if present
amp_url="${amp_url%$'\n'}"

if [ -z "$AWS_ACCESS_KEY_ID" ]; then
  echo "Need to set access key id"
  exit 1
fi

if [ -z "$AWS_SECRET_ACCESS_KEY" ]; then
  echo "Need to set secret access key"
  exit 1
fi

export query="${amp_url}?query=up"
awscurl -X POST --region ${region} \
                --access_key $AWS_ACCESS_KEY_ID \
                --secret_key $AWS_SECRET_ACCESS_KEY \
                --service aps ${query}
