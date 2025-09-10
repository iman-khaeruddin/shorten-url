#!/bin/bash

# Test Rate Limiting Script for URL Shortener API
# Make sure your application is running on http://localhost:8080

echo "🚀 Testing Rate Limiting for URL Shortener API"
echo "Rate Limit: 30 requests per 60 seconds"
echo "============================================"

API_URL="http://localhost:8080/api/shorten"
COUNTER=1

# Test data
TEST_DATA='{"longUrl":"https://www.example.com/test","customAlias":"test'$RANDOM'"}'

echo "📡 Making API calls to test rate limiting..."
echo ""

while [ $COUNTER -le 35 ]; do
    # Generate unique test data for each request
    UNIQUE_DATA='{"longUrl":"https://www.example.com/test'$COUNTER'","customAlias":"test'$COUNTER$RANDOM'"}'
    
    echo -n "Request #$COUNTER: "
    
    RESPONSE=$(curl -s -w "HTTP_STATUS:%{http_code}\nRESPONSE_TIME:%{time_total}s" \
                    -H "Content-Type: application/json" \
                    -d "$UNIQUE_DATA" \
                    -X POST "$API_URL")
    
    HTTP_STATUS=$(echo "$RESPONSE" | grep "HTTP_STATUS:" | cut -d: -f2)
    RESPONSE_TIME=$(echo "$RESPONSE" | grep "RESPONSE_TIME:" | cut -d: -f2)
    
    if [ "$HTTP_STATUS" -eq 200 ]; then
        echo "✅ SUCCESS (${RESPONSE_TIME})"
    elif [ "$HTTP_STATUS" -eq 429 ]; then
        echo "🚫 RATE LIMITED (HTTP 429) - ${RESPONSE_TIME}"
        echo "   Rate limit reached! Waiting for next window..."
        break
    else
        echo "❌ ERROR (HTTP $HTTP_STATUS) - ${RESPONSE_TIME}"
    fi
    
    COUNTER=$((COUNTER + 1))
    
    # Small delay between requests
    sleep 0.1
done

echo ""
echo "🧪 Test completed!"
echo ""
echo "📊 Expected behavior:"
echo "   - First 30 requests should succeed (HTTP 200)"
echo "   - Request #31+ should be rate limited (HTTP 429)"
echo ""
echo "🔍 You can also check rate limit headers in the response:"
echo "   - X-RateLimit-Limit: 30"
echo "   - X-RateLimit-Remaining: (decreases with each request)"
echo "   - X-RateLimit-Window: 60"
echo ""
echo "⏱️  Wait 60 seconds and run the test again to reset the rate limit window."
