-- Create Accounts Table
CREATE TABLE accounts (
                          id VARCHAR(255) PRIMARY KEY,
                          balance DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
                          currency VARCHAR(3) NOT NULL,
                          version INT NOT NULL DEFAULT 0 -- Used for Optimistic Concurrency Control
);

-- Create Ledger Events Table (Event Sourcing)
CREATE TABLE ledger_events (
                               id SERIAL PRIMARY KEY,
                               account_id VARCHAR(255) NOT NULL,
                               amount DECIMAL(15, 2) NOT NULL,
                               currency VARCHAR(3) NOT NULL,
                               transaction_type VARCHAR(50) NOT NULL, -- DEPOSIT or WITHDRAWAL
                               idempotency_key VARCHAR(255) UNIQUE NOT NULL,
                               created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Pre-populate some demo accounts so we have accounts to test with!
INSERT INTO accounts (id, balance, currency, version) VALUES
                                                          ('acc_john_123', 1000.00, 'USD', 0),
                                                          ('acc_jane_456', 500.00, 'USD', 0);