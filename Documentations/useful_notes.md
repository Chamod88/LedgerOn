# 🛠️ Useful Notes: Local Development Commands

## Basic Docker Compose Commands
Now that you understand the concept and the structure of the blueprint, here are the most important commands you will run in your terminal to manage your infrastructure (Postgres, Redis, Kafka):

* **`docker-compose up`**: Starts all the containers defined in the `docker-compose.yml` file.
  - *Tip*: Add the `-d` flag (`docker-compose up -d`) to run them in "detached" mode, which means they run in the background and don't block your terminal.
* **`docker-compose down`**: Stops all the containers and cleans up the network.
* **`docker-compose logs`**: Shows the console output (logs) of all running containers.
  - *Tip*: Use `docker-compose logs -f [service_name]` (e.g., `docker-compose logs -f postgres`) to follow the logs of a specific container.
* **`docker-compose ps`**: Lists the running containers and shows their status (running, exited, ports exposed).
* **`docker-compose exec`**: Runs a command inside a running container.
  - *Tip*: Use `docker-compose exec redis redis-cli ping` to check if Redis is alive.

## Database (PostgreSQL) Commands
Since PostgreSQL is our durable source of truth, you'll often need to check the state of accounts and the event log.

* **Access SQL Shell**:
  `docker-compose exec postgres psql -U ledger_user -d ledger_db`
* **Check Balances**:
  `SELECT * FROM accounts;`
* **View Event Audit Trail**:
  `SELECT * FROM ledger_events ORDER BY created_at DESC;`

## Kafka (Message Broker) Commands
Useful for debugging the "digital conveyor belt" and seeing messages in real-time.

* **List Topics**:
  `docker-compose exec kafka kafka-topics --list --bootstrap-server localhost:9092`
* **Watch Transactions in Real-time**:
  `docker-compose exec kafka kafka-console-consumer --topic ledger-transactions --from-beginning --bootstrap-server localhost:9092`

## Cleanup & Reset
If you want to start from a completely clean slate (wiping the database and cache):

* **Reset Everything**:
  `docker-compose down -v`
  - *Note*: The `-v` flag deletes the "volumes," which effectively erases all stored data in PostgreSQL and Redis.

91. Business Logic
    Meaning
    Business logic is the actual rule-based behavior of the application.
    In this project
    Business rules include:
    Do not allow duplicate idempotency keys.
    Do not allow withdrawals above the current balance.
    Add money for deposits.
    Subtract money for withdrawals.
    Save successful transactions as ledger events.
    Example usage
    The rule:``` text
    You cannot withdraw more money than the account has.
```
2. **Formatting inconsistencies** - Sections 91-93 use different indentation/spacing than the rest
3. **Missing visual hierarchy** - Terms should follow the same format as other sections
4. **Incomplete context** - Abrupt start at section 91

## Suggested Changes:

```markdown
is business logic.

---

## 91. Business Logic

**Meaning**  
Business logic is the actual rule-based behavior of the application.

**In this project**  
Business rules include:
* Do not allow duplicate idempotency keys.
* Do not allow withdrawals above the current balance.
* Add money for deposits.
* Subtract money for withdrawals.
* Save successful transactions as ledger events.

**Example usage**  
The rule:
```text
You cannot withdraw more money than the account has.
```

is business logic.

---

## 92. Fault-Tolerant

**Meaning**  
Fault-tolerant means a system can keep working, or fail safely, when something goes wrong.

**In this project**  
The MVP is only partially fault-tolerant. It has basic protection through:

* Redis idempotency
* PostgreSQL constraints
* Kafka queueing

But production-grade fault tolerance would need more features later.

**Example usage**  
If duplicate requests arrive, Redis helps the system fail safely by rejecting duplicates.

---

## 93. High-Throughput

**Meaning**  
High-throughput means the system can handle many requests per second.

**In this project**  
The gateway can respond quickly because it does not wait for the database transaction to complete. It queues work
through Kafka.

**Example usage**  
Instead of:

```text
Client waits for database update
```

The MVP does:

```text
Client request → queue message → respond quickly
```

---

```
## Core Concepts to Learn First

If you are new to this project, start by understanding these key concepts:

1. API Gateway
2. Idempotency Key
3. Redis
4. Kafka
5. Ledger Service
6. PostgreSQL
7. Transaction
8. Event Sourcing
9. OCC
10. Docker Compose

Understanding these 10 concepts will give you a solid grasp of the MVP's core architecture and behavior.



1. Simple Explanation
If the API Gateway is the bank's front desk, the Ledger Service is the vault accountant. It doesn't talk to the user. Instead, it:

Watches the Kafka conveyor belt.
Picks up a transaction message.
Opens the PostgreSQL ledger database, finds the customer's account, and checks their balance.
If they have enough money: it updates their account balance and appends a new row to an audit journal (an event log) showing the deposit or withdrawal.
If they don't have enough money: it prints a warning and cancels the transaction.



