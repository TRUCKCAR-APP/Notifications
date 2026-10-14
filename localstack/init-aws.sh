#!/bin/bash
set -e

echo "Initializing LocalStack AWS resources for TruckCar Notifications..."

# Create S3 Bucket for voice audio
awslocal s3 mb s3://truckcar-voice-notifications || true

# Create SNS Topic for push notifications
awslocal sns create-topic --name truckcar-notifications || true

echo "LocalStack resources initialized successfully."
