#!/bin/sh
set -eu

s3() {
  curl -fsS --aws-sigv4 aws:amz:us-east-1:s3 --user "$RUSTFS_ACCESS_KEY:$RUSTFS_SECRET_KEY" "$@"
}

for bucket in $BUCKETS; do
  s3 -o /dev/null -I "http://rustfs:9000/$bucket" 2>/dev/null || s3 -X PUT "http://rustfs:9000/$bucket"
  echo "Bucket $bucket ready"
done
s3 -X PUT --data-binary '' "http://rustfs:9000/$SPARK_EVENTS"
