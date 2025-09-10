# AIT - URL Shortener Service

A high-performance URL shortening service built with Spring Boot that provides short aliases for long URLs with analytics tracking, rate limiting, and Redis caching.

## 📋 Features

- **URL Shortening**: Convert long URLs into short, manageable links
- **Custom Aliases**: Create personalized short URLs with custom aliases
- **Expiration Support**: Set expiration dates for URLs
- **Click Analytics**: Track click counts and basic analytics
- **Rate Limiting**: Built-in rate limiting (30 requests per 60 seconds)
- **Redis Caching**: Fast response times with Redis caching
- **Swagger Documentation**: Interactive API documentation
- **Docker Support**: Easy deployment with Docker and Docker Compose

## 🛠 Tech Stack

- **Backend Framework**: Spring Boot 3.5.5
- **Language**: Java 21
- **Database**: MySQL 8.0
- **Cache**: Redis 7
- **Build Tool**: Maven 3.9.2
- **Documentation**: SpringDoc OpenAPI 3 (Swagger)
- **Containerization**: Docker & Docker Compose
- **Additional Libraries**:
  - Spring Data JPA
  - Spring Data Redis
  - Lombok
  - MySQL Connector

## 🚀 Quick Start

### Prerequisites

- Java 21+
- Maven 3.6+
- MySQL 8.0
- Redis 7+
- Docker (optional)

### Option 1: Run with Docker Compose (Recommended)

1. Clone the repository:
```bash
git clone https://github.com/iman-khaeruddin/shorten-url.git
cd shorten-url
```

2. Start all services:
```bash
docker-compose up -d
```

3. The application will be available at `http://localhost:8080`

### Option 2: Run Locally

1. **Start MySQL and Redis**:
```bash
# MySQL
docker run -d --name mysql \
  -e MYSQL_ROOT_PASSWORD=PUT_YOUR_PASSWORD_HERE \
  -e MYSQL_DATABASE=url_shortener \
  -p 3306:3306 mysql:8.0

# Redis
docker run -d --name redis -p 6379:6379 redis:7
```

2. **Build and run the application**:
```bash
./mvnw clean package -DskipTests
./mvnw spring-boot:run
```

3. **Or run the JAR directly**:
```bash
java -jar target/ait-0.0.1-SNAPSHOT.jar
```

## 📚 API Documentation

Once the application is running, you can access:

- **Swagger UI**: `http://localhost:8080/swagger-ui.html`
- **OpenAPI Spec**: `http://localhost:8080/v3/api-docs`

## 🔗 API Endpoints

### 1. Shorten URL
Create a short URL from a long URL.

**Endpoint**: `POST /api/shorten`

**Request Body**:
```json
{
  "longUrl": "https://www.example.com/very/long/url",
  "customAlias": "my-link",
  "expiresAt": "2024-12-31T23:59:59Z"
}
```

**cURL Example**:
```bash
curl -X POST http://localhost:8080/api/shorten \
  -H "Content-Type: application/json" \
  -d '{
    "longUrl": "https://www.google.com/search?q=spring+boot+url+shortener",
    "customAlias": "google-search"
  }'
```

**Response**:
```json
{
  "shortUrl": "http://localhost:8080/google-search",
  "alias": "google-search",
  "longUrl": "https://www.google.com/search?q=spring+boot+url+shortener"
}
```

### 2. Redirect to Original URL
Redirect using the short URL alias to the original URL.

**Endpoint**: `GET /{alias}`

**cURL Example**:
```bash
curl -L http://localhost:8080/google-search
```

**Response**: `302 Redirect` to the original URL

### 3. Get URL Information
Get detailed information about a shortened URL.

**Endpoint**: `GET /api/info/{alias}`

**cURL Example**:
```bash
curl http://localhost:8080/api/info/google-search
```

**Response**:
```json
{
  "alias": "google-search",
  "longUrl": "https://www.google.com/search?q=spring+boot+url+shortener",
  "createdAt": "2024-01-15T10:30:00Z",
  "expiresAt": null,
  "clicks": 5,
  "custom": true
}
```

### 4. Get Click Analytics
Get click count analytics for a shortened URL.

**Endpoint**: `GET /api/analytics/{alias}/clicks`

**cURL Example**:
```bash
curl http://localhost:8080/api/analytics/google-search/clicks
```

**Response**:
```json
{
  "alias": "google-search",
  "totalClicks": 5
}
```

## 🧪 Testing

### Manual Testing with cURL

1. **Create a short URL**:
```bash
curl -X POST http://localhost:8080/api/shorten \
  -H "Content-Type: application/json" \
  -d '{
    "longUrl": "https://github.com/spring-projects/spring-boot"
  }'
```

2. **Test redirection**:
```bash
curl -I http://localhost:8080/{returned-alias}
```

3. **Get URL info**:
```bash
curl http://localhost:8080/api/info/{returned-alias}
```

4. **Check analytics**:
```bash
curl http://localhost:8080/api/analytics/{returned-alias}/clicks
```

### Rate Limiting Test

The project includes a rate limiting test script:

```bash
chmod +x test_rate_limit.sh
./test_rate_limit.sh
```

This script tests the rate limiting feature (30 requests per 60 seconds).

### Unit Tests

```bash
./mvnw test
```

## ⚙️ Configuration

Key configuration options in `application.yml`:

```yaml
app:
  base-url: http://localhost:8080          # Base URL for short links
  default-expiration-days: 365             # Default expiration (1 year)
  rate-limit:
    window-seconds: 60                     # Rate limit window
    max-requests: 30                       # Max requests per window

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/url_shortener
    username: root
    password: PUT_YOUR_PASSWORD_HERE
  
  redis:
    host: localhost
    port: 6379
    timeout: 2000ms
  
  cache:
    type: redis
    redis:
      time-to-live: 600000                 # 10 minutes cache TTL
```

## 🐳 Docker Support

### Build Docker Image
```bash
docker build -t url-shortener .
```

### Run with Docker Compose
```bash
docker-compose up -d
```

### Stop Services
```bash
docker-compose down
```

## 📊 Rate Limiting

The API implements rate limiting with the following defaults:
- **Limit**: 30 requests per client IP
- **Window**: 60 seconds
- **Headers**: Response includes rate limit information
  - `X-RateLimit-Limit`: Maximum requests allowed
  - `X-RateLimit-Remaining`: Requests remaining in current window
  - `X-RateLimit-Window`: Window duration in seconds

## 🗄️ Database Schema

The application automatically creates the following tables:

- **url_mapping**: Stores URL mappings and metadata
- **click_event**: Stores click analytics data

## 🔒 Security Features

- Input validation for URLs
- SQL injection prevention through JPA
- Rate limiting by IP address
- Expiration date validation
- Custom alias conflict detection

## 🐛 Troubleshooting

### Common Issues

1. **MySQL Connection Issues**:
   - Ensure MySQL is running on port 3306
   - Check username/password in `application.yml`
   - Verify database `url_shortener` exists

2. **Redis Connection Issues**:
   - Ensure Redis is running on port 6379
   - Check Redis host configuration

3. **Port Already in Use**:
   - Change server port in `application.yml`
   - Or stop the process using port 8080

### Logs

Check application logs for detailed error information:
```bash
# If running with Docker Compose
docker-compose logs app

# If running locally
# Logs will appear in the console
```
