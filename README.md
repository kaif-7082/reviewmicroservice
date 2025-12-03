**Review Microservice**
* Overview The Review Microservice handles user feedback and ratings. It is designed around an Event-Driven Architecture, acting as a Kafka Producer. When reviews are created, it publishes events to update other services asynchronously, ensuring decoupling and data consistency across the system.

**Tech Stack**

* Java 21

* Spring Boot 3.5.7

* Apache Kafka (Producer)

* Spring Cloud OpenFeign

* MySQL & H2 Database

* Micrometer & Zipkin (Distributed Tracing)

* Spring Security (JWT)

**Configuration**This service supports multiple profiles for different environments:

**Development (Dev)**

* Profile: dev

* Port: 8093

* Database: H2 In-Memory

**Production (Prod)**

* Profile: prod

* Port: 8083

* Database: MySQL (reviewms_db)

**Application Name**: reviewms

**Key Features**

* **Event-Driven Architecture (Kafka Producer):** Decouples review creation from rating updates. Upon saving a review, it publishes a message to the companyRatingQueue topic, allowing the Company Service to update its average rating asynchronously.

* **Transactional Data Integrity:** Uses @Transactional to ensure that database operations and messaging remain consistent. If a message fails to send, the transaction is managed to prevent data discrepancies.

* **Service Validation via Feign:** Validates the existence of a company by making a synchronous call to the Company Microservice before allowing a review to be created, ensuring relational integrity across microservices.

* **Internal Statistics API:** Exposes a specialized endpoint to calculate the average rating for a specific company. This "Source of Truth" is used by other services to fetch accurate data when processing events.

* **Flexible Data Retrieval:** Supports pagination and sorting, allowing clients to efficiently retrieve large lists of reviews sorted by rating or date.

* **Global Exception Handling:** Centralized handling for validation errors and business exceptions (e.g., CompanyNotFoundException), providing clear and standardized JSON error responses to clients.

**API Endpoints**

| Method | Endpoint | Description | Auth |

| GET | /reviews | Get all reviews (optional companyId filter) | User/Admin |

| POST | /reviews | Submit a new review | User/Admin |

| PUT | /reviews/{reviewId} | Update an existing review | Admin |

| DELETE | /reviews/{reviewId} | Delete a review | Admin |

| GET | /reviews/stats/average-rating | Calculate average rating (Internal) | Public/Internal |

**How to Run** 
Infrastructure: Ensure MySQL, Kafka, Zookeeper, Zipkin, and Eureka are running.

Run (default/dev):

./mvnw spring-boot:run

Run (prod):

./mvnw spring-boot:run -Dspring.profiles.active=prod