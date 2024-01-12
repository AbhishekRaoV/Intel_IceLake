#!/bin/bash

# Check if two arguments are provided
if [ "$#" -ne 2 ]; then
    echo "Usage: $0 <timestamp1> <timestamp2>"
    exit 1
fi

timestamp1="$1"
timestamp2="$2"

# Convert timestamps to seconds since midnight
time1=$(date -d "$timestamp1" +"%s")
time2=$(date -d "$timestamp2" +"%s")

# Calculate the time difference
time_difference=$((time2 - time1))

# Format the time difference
#formatted_time=$(date -u -d @$time_difference +"%T")

echo "$time_difference"
