Yes. I read `Documentation and Guides/implementation_guide.md`. Below is a **keyword glossary** for the important terms in that guide, with detailed explanations and how each one is used in this financial ledger MVP project.

---

# Keyword Glossary for the Ledger MVP

## 1. **MVP**

### Meaning

**MVP** stands for **Minimum Viable Product**.

It means the simplest working version of the system that proves the main idea works. It does **not** include every production feature yet.

### In this project

The MVP proves this flow:

```plain text
Client sends transaction
        ↓
API Gateway receives it
        ↓
Redis checks duplicate request
        ↓
Kafka queues the transaction
        ↓
Ledger Service processes it
        ↓
PostgreSQL stores account/event data
```


### Example usage

You can say:

> First I will build the MVP so I can prove money transfers move through the full system before adding retries, tracing, DLQs, and production security.

---

## 2. **Ledger**

### Meaning

A **ledger** is a record book of financial activity. In banking, every deposit, withdrawal, or transfer is recorded permanently.

### In this project

The ledger is represented by:

- Account balances
- Transaction events
- PostgreSQL database tables

The system records each transaction so you can audit what happened.

### Example usage

If John withdraws `$50`, the ledger should show:

```plain text
Account: acc_john_123
Transaction type: WITHDRAWAL
Amount: 50.00
Currency: USD
```


---

## 3. **Microservice**

### Meaning

A **microservice** is a small independent application that does one main job.

Instead of one huge application doing everything, you split the system into smaller services.

### In this project

There are two main services:

```plain text
api-gateway       → receives HTTP requests
ledger-service    → processes financial transactions
```


Each service can run separately.

### Example usage

The API Gateway can be running on port `8080`, while the Ledger Service can be running on port `8081`.

---

## 4. **API Gateway**

### Meaning

An **API Gateway** is the public entry point into your system.

Clients do not directly call the database or Kafka. They call the gateway.

### In this project

The API Gateway:

1. Accepts transfer requests.
2. Checks idempotency using Redis.
3. Publishes valid requests to Kafka.
4. Immediately returns a response to the client.

### Example usage

A client sends this request to the gateway:

```
POST /api/v1/ledger/transfer
Content-Type: application/json
X-Idempotency-Key: txn_unique_001
```


The gateway then queues the transaction instead of processing it directly.

---

## 5. **Client**

### Meaning

A **client** is anything that sends a request to your application.

It can be:

- A frontend website
- A mobile app
- Postman
- `curl`
- A load testing tool like k6

### In this project

The client sends a transaction request to the API Gateway.

### Example usage

Using `curl`, your terminal acts as the client:

```shell script
curl -X POST http://localhost:8080/api/v1/ledger/transfer
```


---

## 6. **HTTP**

### Meaning

**HTTP** is the protocol used by web applications to communicate.

Browsers, APIs, and backend services often communicate using HTTP requests and responses.

### In this project

The client sends an HTTP `POST` request to the API Gateway.

### Example usage

```
POST /api/v1/ledger/transfer
```


This means:

> Create or submit a new transfer request.

---

## 7. **POST**

### Meaning

`POST` is an HTTP method used to submit data to a server.

### In this project

The client uses `POST` to submit a new transaction request.

### Example usage

```shell script
curl -X POST http://localhost:8080/api/v1/ledger/transfer
```


This sends a new ledger transaction request to the system.

---

## 8. **Endpoint**

### Meaning

An **endpoint** is a specific URL where an API receives requests.

### In this project

The main endpoint is:

```plain text
/api/v1/ledger/transfer
```


### Example usage

Full local URL:

```plain text
http://localhost:8080/api/v1/ledger/transfer
```


This is the address clients use to submit deposits or withdrawals.

---

## 9. **Request Body**

### Meaning

The **request body** is the data sent inside an HTTP request.

For APIs, this is often JSON.

### In this project

The request body contains transaction details:

```json
{
  "accountId": "acc_john_123",
  "amount": 50.00,
  "currency": "USD",
  "transactionType": "WITHDRAWAL"
}
```


### Example usage

The API Gateway reads this body and turns it into a transaction event.

---

## 10. **Header**

### Meaning

An HTTP **header** is extra metadata sent with a request.

Headers are often used for:

- Authentication
- Content type
- Idempotency keys
- Trace IDs

### In this project

The important header is:

```
X-Idempotency-Key: txn_unique_001
```


This prevents duplicate transactions.

### Example usage

```shell script
-H "X-Idempotency-Key: txn_unique_001"
```


---

## 11. **Idempotency**

### Meaning

**Idempotency** means repeating the same request should not cause the same action to happen multiple times.

This is very important in financial systems.

If a user clicks “Pay” twice or the network retries a request, they should not be charged twice.

### In this project

The API Gateway checks whether an idempotency key has already been used.

If the key already exists, the request is rejected.

### Example usage

First request:

```plain text
X-Idempotency-Key: txn_unique_001
```


Result:

```plain text
Accepted
```


Second request with the same key:

```plain text
X-Idempotency-Key: txn_unique_001
```


Result:

```plain text
Rejected as duplicate
```


---

## 12. **Idempotency Key**

### Meaning

An **idempotency key** is a unique value attached to a request.

It acts like a receipt number.

### In this project

The key is sent in the HTTP header:

```
X-Idempotency-Key: txn_unique_001
```


Redis stores this key temporarily.

### Example usage

```plain text
idempotency:txn_unique_001 → processing
```


This means the system has already seen transaction `txn_unique_001`.

---

## 13. **Double-Spend Protection**

### Meaning

**Double-spend protection** prevents the same money movement from happening twice by accident or attack.

### In this project

Redis blocks repeated requests with the same idempotency key before they reach Kafka or PostgreSQL.

### Example usage

If a user tries this twice:

```plain text
Withdraw $50 from acc_john_123 using key txn_unique_001
```


The first request is accepted. The second is rejected.

---

## 14. **Redis**

### Meaning

**Redis** is a very fast in-memory data store.

It is commonly used for:

- Caching
- Temporary keys
- Rate limiting
- Distributed locks
- Idempotency checks

### In this project

Redis stores idempotency keys.

### Example usage

When the gateway receives a request, it stores:

```plain text
idempotency:txn_unique_001 = processing
```


If that key already exists, the request is treated as a duplicate.

---

## 15. **Cache**

### Meaning

A **cache** is a fast storage layer used to avoid slower operations.

### In this project

Redis is used as a cache for idempotency keys.

The system does not need to check the database first for every duplicate request.

### Example usage

Instead of asking PostgreSQL:

```plain text
Have I seen txn_unique_001 before?
```


The API Gateway asks Redis, which is much faster.

---

## 16. **TTL**

### Meaning

**TTL** means **Time To Live**.

It controls how long a key remains stored before expiring automatically.

### In this project

Idempotency keys are stored for a limited time, such as 24 hours.

### Example usage

```plain text
Store txn_unique_001 for 24 hours
```


After 24 hours, Redis may delete the key.

---

## 17. **Kafka**

### Meaning

**Apache Kafka** is a distributed message broker.

It lets services communicate asynchronously using events.

### In this project

The API Gateway publishes transaction events to Kafka.

The Ledger Service consumes them later.

### Example usage

The gateway sends a transaction event to:

```plain text
ledger-transactions
```


Then the Ledger Service listens to that topic.

---

## 18. **Message Broker**

### Meaning

A **message broker** is middleware that moves messages between services.

Instead of services calling each other directly, one service publishes a message and another service consumes it.

### In this project

Kafka is the message broker.

### Example usage

```plain text
API Gateway → Kafka → Ledger Service
```


This makes the system asynchronous and more scalable.

---

## 19. **Topic**

### Meaning

A Kafka **topic** is like a named message channel.

Producers write messages to a topic. Consumers read messages from a topic.

### In this project

The topic is:

```plain text
ledger-transactions
```


### Example usage

All transfer requests are published to the `ledger-transactions` topic.

---

## 20. **Producer**

### Meaning

A **producer** is an application that sends messages to Kafka.

### In this project

The API Gateway is the producer.

### Example usage

When a valid request arrives:

```plain text
API Gateway publishes event to Kafka
```


That means the gateway is producing a Kafka message.

---

## 21. **Consumer**

### Meaning

A **consumer** is an application that reads messages from Kafka.

### In this project

The Ledger Service is the consumer.

### Example usage

The Ledger Service listens for new messages and processes each transaction.

---

## 22. **Consumer Group**

### Meaning

A **consumer group** is a group of Kafka consumers that cooperate to process messages.

Kafka uses the group ID to track which messages have been processed.

### In this project

The guide uses a group like:

```plain text
ledger-group
```


### Example usage

If you run multiple Ledger Service instances with the same group ID, Kafka can distribute work among them.

---

## 23. **Asynchronous Processing**

### Meaning

**Asynchronous processing** means the client does not wait for all backend work to finish.

The system accepts the request now and processes it later.

### In this project

The API Gateway returns quickly after placing the message in Kafka.

The Ledger Service processes the transaction in the background.

### Example usage

Client receives:

```json
{
  "status": "QUEUED",
  "message": "Transaction accepted for processing"
}
```


This means:

> The transaction was accepted, but final processing happens later.

---

## 24. **Queue**

### Meaning

A **queue** stores work that needs to be processed.

Kafka is not exactly a traditional queue internally, but in this MVP it behaves like a conveyor belt for transaction events.

### In this project

Transactions wait in Kafka until the Ledger Service consumes them.

### Example usage

If 1000 requests arrive quickly, the API Gateway can queue them into Kafka, and the Ledger Service can process them at its own speed.

---

## 25. **PostgreSQL**

### Meaning

**PostgreSQL** is a relational database.

It stores structured data in tables and supports strong consistency.

### In this project

PostgreSQL stores:

- Accounts
- Balances
- Ledger events

### Example usage

The project uses tables similar to:

```plain text
accounts
ledger_events
```


---

## 26. **Database**

### Meaning

A **database** stores persistent data.

Persistent means the data should still exist after the application restarts.

### In this project

PostgreSQL is the permanent database for account and ledger records.

### Example usage

Redis may forget keys after TTL, but PostgreSQL keeps the financial ledger permanently.

---

## 27. **Table**

### Meaning

A database **table** stores rows of related data.

### In this project

There are two important tables:

```plain text
accounts
ledger_events
```


### Example usage

`accounts` stores current account balances.

`ledger_events` stores transaction history.

---

## 28. **Primary Key**

### Meaning

A **primary key** uniquely identifies a row in a database table.

### In this project

The account ID is the primary key for accounts.

### Example usage

```plain text
acc_john_123
```


This identifies John’s account.

---

## 29. **Unique Constraint**

### Meaning

A **unique constraint** prevents duplicate values in a database column.

### In this project

The ledger event idempotency key should be unique.

This adds another layer of duplicate protection.

### Example usage

If two events use:

```plain text
txn_unique_001
```


PostgreSQL should reject the second one.

---

## 30. **Account**

### Meaning

An **account** represents a financial balance owned by a user or entity.

### In this project

An account has:

- ID
- Balance
- Currency
- Version

### Example usage

```plain text
acc_john_123 has 1000.00 USD
```


After a `$50` withdrawal:

```plain text
acc_john_123 has 950.00 USD
```


---

## 31. **Balance**

### Meaning

A **balance** is how much money an account currently has.

### In this project

The Ledger Service reads and updates balances.

### Example usage

Before:

```plain text
balance = 1000.00
```


Withdraw `$50`:

```plain text
new balance = 950.00
```


Deposit `$100`:

```plain text
new balance = 1100.00
```


---

## 32. **Currency**

### Meaning

**Currency** identifies the money type, such as USD, EUR, GBP, or JPY.

### In this project

Each transaction includes currency.

### Example usage

```json
{
  "currency": "USD"
}
```


In a production system, you would reject a transaction if the account currency and transaction currency do not match.

---

## 33. **Transaction**

### Meaning

A **transaction** is a financial operation.

Common examples:

- Deposit
- Withdrawal
- Transfer

### In this project

The MVP supports:

```plain text
DEPOSIT
WITHDRAWAL
```


### Example usage

```plain text
Withdraw $50 from John's account
```


This is a transaction.

---

## 34. **Transaction Type**

### Meaning

The **transaction type** tells the system what kind of operation to perform.

### In this project

Valid values are:

```plain text
DEPOSIT
WITHDRAWAL
```


### Example usage

```json
{
  "transactionType": "WITHDRAWAL"
}
```


The Ledger Service subtracts money for withdrawals and adds money for deposits.

---

## 35. **Withdrawal**

### Meaning

A **withdrawal** removes money from an account.

### In this project

The Ledger Service checks that the account has enough money before subtracting the amount.

### Example usage

```plain text
acc_john_123 balance: 1000.00
withdrawal amount: 50.00
new balance: 950.00
```


---

## 36. **Deposit**

### Meaning

A **deposit** adds money to an account.

### In this project

Deposits increase the account balance.

### Example usage

```plain text
acc_john_123 balance: 1000.00
deposit amount: 25.00
new balance: 1025.00
```


---

## 37. **Insufficient Funds**

### Meaning

**Insufficient funds** means an account does not have enough money for a withdrawal.

### In this project

If John has `$950` and tries to withdraw `$2000`, the Ledger Service rejects the transaction.

### Example usage

```plain text
Current balance: 950.00
Requested withdrawal: 2000.00
Result: rejected
```


---

## 38. **Event Sourcing**

### Meaning

**Event sourcing** means storing every change as an immutable event instead of only storing the latest state.

Rather than only saying:

```plain text
John has 950.00
```


You record:

```plain text
John started with 1000.00
John withdrew 50.00
```


### In this project

The `ledger_events` table records transaction events.

### Example usage

After a withdrawal, the system stores an event like:

```plain text
account_id = acc_john_123
amount = 50.00
transaction_type = WITHDRAWAL
idempotency_key = txn_unique_001
```


This creates an audit trail.

---

## 39. **Immutable Event**

### Meaning

An **immutable event** is a record that should not be changed after it is created.

Financial systems prefer this because audit history must be trustworthy.

### In this project

Once a ledger event is inserted, it should represent what happened at that time.

### Example usage

Instead of editing an old withdrawal, you would add a new correcting event.

---

## 40. **Audit Trail**

### Meaning

An **audit trail** is a historical record of actions.

It lets you answer:

- Who changed money?
- When did it happen?
- How much was changed?
- Why did the balance become this amount?

### In this project

The `ledger_events` table acts as the audit trail.

### Example usage

To investigate a balance, you would inspect all events for an account.

---

## 41. **Optimistic Concurrency Control**

### Meaning

**Optimistic Concurrency Control**, often called **OCC**, is a method for handling multiple updates to the same data.

It assumes conflicts are rare, but checks before saving.

### In this project

The account has a `version` field.

When the Ledger Service updates an account, the version helps detect if another process changed it at the same time.

### Example usage

Two withdrawals happen at once:

```plain text
Request A reads version 0
Request B reads version 0

Request A saves balance and version becomes 1
Request B tries to save with old version 0
Database rejects Request B because version is stale
```


This prevents lost updates.

---

## 42. **Version Field**

### Meaning

A **version field** is a number used to detect concurrent changes.

### In this project

The account row has a version value.

### Example usage

```plain text
Before update:
version = 0

After successful update:
version = 1
```


If another transaction tries to save using the old version, OCC can detect the conflict.

---

## 43. **ACID**

### Meaning

**ACID** describes database transaction safety:

| Letter | Meaning | Explanation |
|---|---|---|
| A | Atomicity | All changes succeed or none do |
| C | Consistency | Data remains valid |
| I | Isolation | Concurrent transactions do not corrupt each other |
| D | Durability | Saved data survives crashes |

### In this project

The Ledger Service uses a database transaction when processing a ledger event.

### Example usage

When processing a withdrawal, these should happen together:

```plain text
Update account balance
Insert ledger event
```


If one fails, the other should not remain partially completed.

---

## 44. **Database Transaction**

### Meaning

A **database transaction** is a group of database operations treated as one unit.

### In this project

The Ledger Service updates account balance and inserts a ledger event in one transaction.

### Example usage

If event insertion fails, the balance update should roll back.

---

## 45. **Rollback**

### Meaning

A **rollback** cancels database changes made during a failed transaction.

### In this project

If saving the ledger event fails, the account balance should not remain changed.

### Example usage

```plain text
Balance changed from 1000 to 950
Event insert fails
Rollback happens
Balance returns to 1000
```


---

## 46. **Entity**

### Meaning

An **entity** is a Java class mapped to a database table.

### In this project

The account and ledger event models are entities.

### Example usage

An Account entity maps to the `accounts` table.

A LedgerEvent entity maps to the `ledger_events` table.

---

## 47. **Repository**

### Meaning

A **repository** is an object that handles database access.

It lets your code save, find, update, or delete data without writing raw SQL every time.

### In this project

Repositories are used to:

- Find account by ID
- Save account balance
- Save ledger event

### Example usage

Conceptually:

```plain text
Find account acc_john_123
Save updated account
Save transaction event
```


---

## 48. **Spring Boot**

### Meaning

**Spring Boot** is a Java framework for building applications quickly.

It provides:

- Web server
- Dependency injection
- Configuration
- Database integration
- Kafka integration
- Redis integration

### In this project

Both the API Gateway and Ledger Service are Spring Boot applications.

### Example usage

You run each service as a Spring Boot application from IntelliJ.

---

## 49. **Dependency**

### Meaning

A **dependency** is an external library your project needs.

### In this project

Dependencies are listed in `pom.xml`.

Examples include libraries for:

- Web API
- Redis
- Kafka
- JPA/database
- PostgreSQL driver
- JSON parsing

### Example usage

The API Gateway needs dependencies for HTTP, Redis, and Kafka.

The Ledger Service needs dependencies for Kafka, PostgreSQL, JPA, and JSON.

---

## 50. **Maven**

### Meaning

**Maven** is a Java build tool.

It downloads dependencies, compiles code, runs tests, and packages applications.

### In this project

Each service has a `pom.xml` file for Maven.

### Example usage

You can build a service with:

```shell script
mvn clean package
```


---

## 51. **pom.xml**

### Meaning

`pom.xml` is Maven’s project configuration file.

It defines:

- Project name
- Version
- Java version
- Dependencies
- Build settings

### In this project

There is one `pom.xml` for the API Gateway and one for the Ledger Service.

### Example usage

The API Gateway `pom.xml` includes web, Redis, and Kafka dependencies.

---

## 52. **JDK**

### Meaning

**JDK** means **Java Development Kit**.

It contains tools needed to compile and run Java programs.

### In this project

You need a JDK installed to run the Spring Boot services.

### Example usage

If Java is installed correctly, this command should work:

```shell script
java -version
```


---

## 53. **Docker**

### Meaning

**Docker** runs applications and services inside isolated environments called containers.

### In this project

Docker runs infrastructure services:

- PostgreSQL
- Redis
- Kafka

### Example usage

Instead of installing PostgreSQL manually, Docker starts it for you.

---

## 54. **Docker Compose**

### Meaning

**Docker Compose** lets you define and run multiple Docker containers using one YAML file.

### In this project

`docker-compose.yml` starts the infrastructure.

### Example usage

```shell script
docker-compose up -d
```


This starts PostgreSQL, Redis, and Kafka.

---

## 55. **Container**

### Meaning

A **container** is a lightweight isolated runtime for an application or service.

### In this project

Each infrastructure component runs in a container.

### Example usage

```plain text
ledger-postgres
ledger-redis
ledger-kafka
```


These are container names.

---

## 56. **Image**

### Meaning

A Docker **image** is a packaged template used to create containers.

### In this project

The guide uses images for PostgreSQL, Redis, and Kafka.

### Example usage

```yaml
image: postgres:15-alpine
```


This tells Docker which PostgreSQL image to download and run.

---

## 57. **Port**

### Meaning

A **port** is a network number where a service listens for connections.

### In this project

Important ports include:

```plain text
8080 → API Gateway
8081 → Ledger Service
5432 → PostgreSQL
6379 → Redis
9092 → Kafka
```


### Example usage

When you call:

```plain text
http://localhost:8080
```


You are connecting to port `8080`.

---

## 58. **localhost**

### Meaning

`localhost` means your own computer.

It usually points to:

```plain text
127.0.0.1
```


### In this project

All services run locally during MVP development.

### Example usage

```plain text
http://localhost:8080/api/v1/ledger/transfer
```


This calls the API Gateway running on your machine.

---

## 59. **Volume**

### Meaning

A Docker **volume** stores data outside the container lifecycle.

Without volumes, deleting a container may delete its data.

### In this project

PostgreSQL uses a volume so database data can persist.

### Example usage

```plain text
postgres_data
```


This stores PostgreSQL data.

---

## 60. **Environment Variables**

### Meaning

**Environment variables** are configuration values passed to an application or container.

### In this project

PostgreSQL uses environment variables for:

- Database name
- Username
- Password

### Example usage

```plain text
POSTGRES_USER=ledger_user
POSTGRES_PASSWORD=ledger_password
POSTGRES_DB=ledger_db
```


---

## 61. **application.properties**

### Meaning

`application.properties` is a Spring Boot configuration file.

It tells the application things like:

- Which port to run on
- Where Redis is
- Where Kafka is
- How to connect to PostgreSQL

### In this project

The API Gateway and Ledger Service each have their own `application.properties`.

### Example usage

The gateway uses configuration like:

```properties
server.port=8080
spring.kafka.bootstrap-servers=localhost:9092
```


---

## 62. **JSON**

### Meaning

**JSON** is a text format used to send structured data.

APIs commonly use JSON.

### In this project

The client sends transaction details as JSON.

Kafka messages are also represented as JSON strings in the MVP.

### Example usage

```json
{
  "accountId": "acc_john_123",
  "amount": 50.00,
  "currency": "USD",
  "transactionType": "WITHDRAWAL"
}
```


---

## 63. **Serialization**

### Meaning

**Serialization** means converting an object into a format that can be sent or stored, such as JSON.

### In this project

The API Gateway converts the transfer request into a JSON event payload before sending it to Kafka.

### Example usage

A Java request object becomes a JSON message like:

```json
{
  "idempotencyKey": "txn_unique_001",
  "accountId": "acc_john_123",
  "amount": 50.00,
  "currency": "USD",
  "transactionType": "WITHDRAWAL"
}
```


---

## 64. **Deserialization**

### Meaning

**Deserialization** means converting JSON back into an object.

### In this project

The Ledger Service reads a JSON message from Kafka and converts it into a transaction object.

### Example usage

Kafka message:

```json
{
  "accountId": "acc_john_123",
  "amount": 50.00
}
```


becomes a Java object that the Ledger Service can process.

---

## 65. **ObjectMapper**

### Meaning

`ObjectMapper` is a Jackson library class used to convert between Java objects and JSON.

### In this project

The Ledger Service uses it to parse Kafka messages.

### Example usage

Conceptually:

```plain text
JSON message from Kafka → TransactionEvent object
```


---

## 66. **BigDecimal**

### Meaning

`BigDecimal` is a Java type used for precise decimal numbers.

It is preferred for money because floating-point types like `double` can have rounding errors.

### In this project

Account balances and transaction amounts should use `BigDecimal`.

### Example usage

A balance like:

```plain text
950.00
```


should be represented precisely, not approximately.

---

## 67. **Validation**

### Meaning

**Validation** means checking that input is correct before processing it.

### In this project

The API Gateway validates that an idempotency key exists.

A stronger version would also validate:

- Amount is positive
- Currency is valid
- Account ID is present
- Transaction type is valid

### Example usage

Invalid request:

```json
{
  "amount": -50.00
}
```


Should be rejected.

---

## 68. **Response**

### Meaning

A **response** is what the server sends back after receiving a request.

### In this project

The API Gateway returns responses like:

- `202 Accepted`
- `400 Bad Request`
- `409 Conflict`

### Example usage

Successful queueing:

```json
{
  "status": "QUEUED",
  "message": "Transaction accepted for processing"
}
```


---

## 69. **202 Accepted**

### Meaning

`202 Accepted` means:

> The request was accepted, but processing is not finished yet.

### In this project

The API Gateway returns `202 Accepted` after placing a transaction on Kafka.

### Example usage

The client receives:

```
HTTP/1.1 202 Accepted
```


This does not mean the withdrawal definitely succeeded. It means it was queued.

---

## 70. **400 Bad Request**

### Meaning

`400 Bad Request` means the client sent invalid input.

### In this project

If the idempotency key is missing, the gateway should return a bad request.

### Example usage

Missing header:

```plain text
X-Idempotency-Key not provided
```


Response:

```json
{
  "error": "MISSING_IDEMPOTENCY_KEY"
}
```


---

## 71. **409 Conflict**

### Meaning

`409 Conflict` means the request conflicts with current system state.

### In this project

A duplicate idempotency key causes a conflict.

### Example usage

Same key used twice:

```plain text
txn_unique_001
```


Second request gets:

```
HTTP/1.1 409 Conflict
```


---

## 72. **curl**

### Meaning

`curl` is a command-line tool used to send HTTP requests.

### In this project

You use `curl` to manually test the API Gateway.

### Example usage

```shell script
curl -X POST http://localhost:8080/api/v1/ledger/transfer
```


---

## 73. **PowerShell**

### Meaning

**PowerShell** is a command-line shell on Windows.

### In this project

The guide uses PowerShell-style multiline commands with backticks.

### Example usage

```shell script
curl -X POST http://localhost:8080/api/v1/ledger/transfer `
  -H "Content-Type: application/json"
```


The backtick continues the command on the next line.

---

## 74. **CMD**

### Meaning

**CMD** is the older Windows Command Prompt.

### In this project

If using CMD instead of PowerShell, multiline commands use `^` instead of backticks.

### Example usage

```shell script
curl -X POST http://localhost:8080/api/v1/ledger/transfer ^
  -H "Content-Type: application/json"
```


---

## 75. **IntelliJ IDEA**

### Meaning

**IntelliJ IDEA** is an IDE used for writing and running code.

### In this project

You use IntelliJ to open the project, create files, and run Java services.

### Example usage

You can click the green run button next to a Spring Boot application class to start a service.

---

## 76. **IDE**

### Meaning

An **IDE** is an Integrated Development Environment.

It provides tools for:

- Editing code
- Running code
- Debugging
- Managing projects
- Viewing errors

### In this project

IntelliJ IDEA is the recommended IDE.

### Example usage

You open the project folder in IntelliJ and run the API Gateway from there.

---

## 77. **Git**

### Meaning

**Git** is a version control system.

It tracks changes to files over time.

### In this project

Git helps you save project progress and safely make changes.

### Example usage

Typical Git workflow:

```shell script
git add .
git commit -m "Build MVP infrastructure"
```


---

## 78. **JPA**

### Meaning

**JPA** stands for Java Persistence API.

It is a standard way for Java applications to map objects to database tables.

### In this project

The Ledger Service uses JPA to interact with PostgreSQL.

### Example usage

A Java `Account` object maps to a row in the `accounts` table.

---

## 79. **Hibernate**

### Meaning

**Hibernate** is a popular implementation of JPA.

It handles much of the database mapping and SQL generation.

### In this project

Spring Boot uses Hibernate behind the scenes for JPA.

### Example usage

When the Ledger Service saves an account object, Hibernate generates SQL to update PostgreSQL.

---

## 80. **DDL**

### Meaning

**DDL** means **Data Definition Language**.

It refers to SQL commands that define database structure, such as creating tables.

### In this project

The guide uses `init.sql` to define database tables manually.

### Example usage

```sql
CREATE TABLE accounts (...);
```


That is DDL.

---

## 81. **SQL**

### Meaning

**SQL** is the language used to communicate with relational databases.

### In this project

SQL creates the accounts and ledger events tables.

### Example usage

```sql
SELECT * FROM accounts;
```


This asks PostgreSQL to show account rows.

---

## 82. **Schema**

### Meaning

A database **schema** is the structure of tables, columns, types, and constraints.

### In this project

The schema includes:

```plain text
accounts table
ledger_events table
primary keys
unique constraints
column types
```


### Example usage

The schema says `idempotency_key` must be unique in the ledger events table.

---

## 83. **Port Mapping**

### Meaning

**Port mapping** connects a port inside a Docker container to a port on your computer.

### In this project

PostgreSQL inside Docker listens on `5432`, and your computer also exposes it on `5432`.

### Example usage

```yaml
ports:
  - "5432:5432"
```


Meaning:

```plain text
host port 5432 → container port 5432
```


---

## 84. **Bootstrap Servers**

### Meaning

Kafka **bootstrap servers** are the addresses a Kafka client uses to connect to the Kafka cluster.

### In this project

Both the API Gateway and Ledger Service connect to Kafka at:

```plain text
localhost:9092
```


### Example usage

```properties
spring.kafka.bootstrap-servers=localhost:9092
```


---

## 85. **Connection String**

### Meaning

A **connection string** tells an application how to connect to a database.

### In this project

The Ledger Service uses a PostgreSQL JDBC URL.

### Example usage

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/ledger_db
```


This means:

```plain text
Connect to PostgreSQL on localhost port 5432, database ledger_db
```


---

## 86. **JDBC**

### Meaning

**JDBC** means Java Database Connectivity.

It is how Java talks to relational databases.

### In this project

The PostgreSQL driver allows Java/Spring to connect to PostgreSQL using JDBC.

### Example usage

The URL begins with:

```plain text
jdbc:postgresql://
```


---

## 87. **Runtime Dependency**

### Meaning

A **runtime dependency** is needed when the application runs, but may not be directly needed while compiling source code.

### In this project

The PostgreSQL driver is commonly configured as a runtime dependency.

### Example usage

The Ledger Service needs the PostgreSQL driver at runtime to connect to the database.

---

## 88. **Transaction ID**

### Meaning

A **transaction ID** is a unique identifier for a transaction request.

### In this project

The gateway returns a generated transaction ID in the response.

### Example usage

```json
{
  "transactionId": "550e8400-e29b-41d4-a716-446655440000"
}
```


This lets the client reference the request.

---

## 89. **UUID**

### Meaning

A **UUID** is a universally unique identifier.

It is often used to generate unique IDs.

### In this project

The gateway can return a UUID as the transaction ID.

### Example usage

```plain text
550e8400-e29b-41d4-a716-446655440000
```


---

## 90. **Service**

### Meaning

A **service** is a component that performs business logic.

### In this project

The Ledger Processor service handles transaction processing logic.

### Example usage

The service decides:

```plain text
If withdrawal and enough funds → subtract balance
If deposit → add balance
If insufficient funds → reject
```


---

## 91. **Business Logic**

### Meaning

**Business logic** is the actual rule-based behavior of the application.

### In this project

Business rules include:

- Do not allow duplicate idempotency keys.
- Do not allow withdrawals above the current balance.
- Add money for deposits.
- Subtract money for withdrawals.
- Save successful transactions as ledger events.

### Example usage

The rule:

```plain text
You cannot withdraw more money than the account has.
```


is business logic.

---

## 92. **Fault-Tolerant**

### Meaning

**Fault-tolerant** means a system can keep working, or fail safely, when something goes wrong.

### In this project

The MVP is only partially fault-tolerant. It has basic protection through:

- Redis idempotency
- PostgreSQL constraints
- Kafka queueing

But production-grade fault tolerance would need more features later.

### Example usage

If duplicate requests arrive, Redis helps the system fail safely by rejecting duplicates.

---

## 93. **High-Throughput**

### Meaning

**High-throughput** means the system can handle many requests per second.

### In this project

The gateway can respond quickly because it does not wait for the database transaction to complete. It queues work through Kafka.

### Example usage

Instead of:

```plain text
Client waits for database update
```


The MVP does:

```plain text
Client request → queue message → respond quickly
```


---

## 94. **Scalability**

### Meaning

**Scalability** means the system can handle more work by adding more resources or instances.

### In this project

Kafka helps because multiple Ledger Service instances could process messages from the transaction topic.

### Example usage

If one Ledger Service cannot keep up, you may later run multiple instances in the same consumer group.

---

## 95. **Dead Letter Queue**

### Meaning

A **Dead Letter Queue**, or **DLQ**, stores messages that cannot be processed successfully.

### In this project

The MVP mentions this as a production improvement, not a full MVP feature.

### Example usage

If a Kafka message contains invalid JSON, a production system would eventually move it to a DLQ instead of retrying forever.

---

## 96. **Poison Message**

### Meaning

A **poison message** is a bad message that repeatedly causes a consumer to fail.

### In this project

An invalid Kafka transaction event could become a poison message.

### Example usage

Bad event:

```json
{
  "amount": "not-a-number"
}
```


A DLQ would prevent this message from blocking the whole consumer.

---

## 97. **Load Testing**

### Meaning

**Load testing** means simulating many users or requests to see how the system performs.

### In this project

After the MVP works manually, you can use tools like k6 or Locust to simulate many transactions.

### Example usage

A load test might simulate:

```plain text
100 virtual users sending deposits and withdrawals
```


---

## 98. **Concurrency**

### Meaning

**Concurrency** means multiple operations happening at the same time.

### In this project

Many users may try to update the same account balance at the same time.

### Example usage

```plain text
User A withdraws $10
User B withdraws $10
Both happen at the same time
```


OCC helps prevent incorrect balance updates.

---

## 99. **Race Condition**

### Meaning

A **race condition** happens when two operations interfere with each other because timing affects the result.

### In this project

Two withdrawals from the same account could read the same starting balance and both try to save changes.

### Example usage

Bad scenario without protection:

```plain text
Balance starts at 100
Request A reads 100
Request B reads 100
A subtracts 10 and saves 90
B subtracts 10 and also saves 90
Expected balance should be 80
```


OCC helps prevent this.

---

## 100. **Local Development**

### Meaning

**Local development** means running the system on your own computer.

### In this project

You run:

- Docker containers locally
- API Gateway locally
- Ledger Service locally

### Example usage

Local API address:

```plain text
http://localhost:8080
```


---

# Most Important Keywords to Understand First

If you are new, focus on these first:

1. **API Gateway**
2. **Idempotency Key**
3. **Redis**
4. **Kafka**
5. **Ledger Service**
6. **PostgreSQL**
7. **Transaction**
8. **Event Sourcing**
9. **OCC**
10. **Docker Compose**

If you understand those 10, you understand the core idea of the whole MVP.