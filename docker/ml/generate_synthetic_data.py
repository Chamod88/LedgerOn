import os
import random
import numpy as np
import pandas as pd
from faker import Faker

# Set random seeds for reproducibility
SEED = 42
random.seed(SEED)
np.random.seed(SEED)
fake = Faker()
Faker.seed(SEED)

FEATURE_COLUMNS = [
    "amount",
    "tx_velocity_1h",
    "tx_velocity_24h",
    "user_avg_amount_24h",
    "amount_to_avg_ratio",
    "time_of_day_hour",
    "is_weekend",
    "device_fingerprint_age_days",
    "is_international",
    "account_age_days",
    "failed_attempts_1h",
    "distance_from_home_km"
]

def generate_synthetic_transactions(n_samples=10000, fraud_ratio=0.05):
    """
    Generate synthetic transaction dataset with realistic financial payment fraud patterns.
    Uses Faker for identity, network, and timestamp attributes alongside numpy distributions.
    """
    n_fraud = int(n_samples * fraud_ratio)
    n_legit = n_samples - n_fraud

    print(f"Generating {n_samples} total transactions ({n_legit} legitimate, {n_fraud} fraudulent)...")

    data = []

    # 1. Generate Legitimate Transactions (n_legit)
    for _ in range(n_legit):
        account_id = f"acc_{fake.hexify(text='^^^^^^^^').lower()}"
        user_avg = round(float(np.random.lognormal(mean=4.2, sigma=0.6)), 2) # ~$50 to $200 avg
        user_avg = max(10.0, min(user_avg, 3000.0))
        
        # Amount close to user average
        amount_ratio = float(np.random.normal(loc=1.0, scale=0.3))
        amount_ratio = max(0.1, min(amount_ratio, 3.0))
        amount = round(user_avg * amount_ratio, 2)

        tx_vel_1h = int(np.random.poisson(lam=0.5))
        tx_vel_1h = min(tx_vel_1h, 4) # Legitimate threshold below rule limit 5

        tx_vel_24h = tx_vel_1h + int(np.random.poisson(lam=2.5))
        tx_vel_24h = min(tx_vel_24h, 15)

        p_hour = np.array([
            0.01, 0.01, 0.01, 0.01, 0.01, 0.02, # 00-05: Low
            0.03, 0.05, 0.07, 0.08, 0.08, 0.08, # 06-11: Morning
            0.08, 0.08, 0.07, 0.07, 0.06, 0.06, # 12-17: Afternoon
            0.05, 0.04, 0.03, 0.03, 0.02, 0.02  # 18-23: Evening
        ])
        p_hour = p_hour / p_hour.sum()
        time_hour = int(np.random.choice(range(24), p=p_hour))

        is_weekend = 1 if np.random.rand() < 0.28 else 0
        device_age = int(np.random.exponential(scale=120)) + 5 # 5 to 500+ days
        is_international = 1 if np.random.rand() < 0.04 else 0
        account_age = int(np.random.uniform(30, 1200))
        failed_attempts = int(np.random.choice([0, 1, 2], p=[0.95, 0.04, 0.01]))
        distance_home = round(float(np.random.exponential(scale=8.0)), 1)

        tx_id = fake.uuid4()
        timestamp = fake.date_time_between(start_date="-30d", end_date="now").isoformat()
        country = "USA" if not is_international else fake.country_code()
        ip_addr = fake.ipv4()

        data.append({
            "transaction_id": tx_id,
            "account_id": account_id,
            "timestamp": timestamp,
            "ip_address": ip_addr,
            "country": country,
            "amount": amount,
            "tx_velocity_1h": tx_vel_1h,
            "tx_velocity_24h": tx_vel_24h,
            "user_avg_amount_24h": user_avg,
            "amount_to_avg_ratio": round(amount / user_avg, 4),
            "time_of_day_hour": time_hour,
            "is_weekend": is_weekend,
            "device_fingerprint_age_days": device_age,
            "is_international": is_international,
            "account_age_days": account_age,
            "failed_attempts_1h": failed_attempts,
            "distance_from_home_km": distance_home,
            "is_fraud": 0
        })

    # 2. Generate Fraudulent Transactions (n_fraud)
    for _ in range(n_fraud):
        account_id = f"acc_{fake.hexify(text='^^^^^^^^').lower()}"
        user_avg = round(float(np.random.lognormal(mean=4.0, sigma=0.5)), 2)
        user_avg = max(10.0, min(user_avg, 1000.0))

        # Pick fraud typology (Pattern A: High Amount Spike, Pattern B: Velocity Spike, Pattern C: ATO / Anomaly)
        typology = np.random.choice(["HIGH_AMOUNT", "VELOCITY_BURST", "ACCOUNT_TAKEOVER"], p=[0.35, 0.35, 0.30])

        if typology == "HIGH_AMOUNT":
            amount_ratio = float(np.random.uniform(5.0, 35.0))
            amount = round(user_avg * amount_ratio, 2)
            tx_vel_1h = int(np.random.choice([1, 2, 3, 4]))
            tx_vel_24h = tx_vel_1h + int(np.random.uniform(1, 5))
            failed_attempts = int(np.random.choice([0, 1, 2], p=[0.7, 0.2, 0.1]))
            device_age = int(np.random.uniform(0, 10))
            distance_home = round(float(np.random.uniform(50.0, 300.0)), 1)
            time_hour = int(np.random.choice([1, 2, 3, 4, 22, 23]))
            is_international = 1 if np.random.rand() < 0.3 else 0

        elif typology == "VELOCITY_BURST":
            amount = round(float(np.random.uniform(150.0, 1500.0)), 2)
            amount_ratio = round(amount / user_avg, 4)
            tx_vel_1h = int(np.random.randint(5, 15)) # Exceeds velocity limits
            tx_vel_24h = tx_vel_1h + int(np.random.randint(5, 20))
            failed_attempts = int(np.random.randint(2, 7)) # Multiple failed logins/pins
            device_age = int(np.random.randint(0, 5))
            distance_home = round(float(np.random.uniform(10.0, 500.0)), 1)
            time_hour = int(np.random.randint(0, 24))
            is_international = 1 if np.random.rand() < 0.2 else 0

        else: # ACCOUNT_TAKEOVER / GEO_ANOMALY
            amount = round(float(np.random.uniform(800.0, 8000.0)), 2)
            amount_ratio = round(amount / user_avg, 4)
            tx_vel_1h = int(np.random.randint(2, 7))
            tx_vel_24h = tx_vel_1h + int(np.random.randint(2, 10))
            failed_attempts = int(np.random.randint(1, 5))
            device_age = int(np.random.randint(0, 2)) # Brand new device
            distance_home = round(float(np.random.uniform(500.0, 9000.0)), 1) # Impossible travel
            time_hour = int(np.random.choice([0, 1, 2, 3, 4, 5])) # Night time
            is_international = 1 if np.random.rand() < 0.75 else 0

        account_age = int(np.random.randint(1, 60)) # Relatively young accounts
        is_weekend = 1 if np.random.rand() < 0.40 else 0

        tx_id = fake.uuid4()
        timestamp = fake.date_time_between(start_date="-30d", end_date="now").isoformat()
        country = fake.country_code() if is_international else "USA"
        ip_addr = fake.ipv4()

        data.append({
            "transaction_id": tx_id,
            "account_id": account_id,
            "timestamp": timestamp,
            "ip_address": ip_addr,
            "country": country,
            "amount": amount,
            "tx_velocity_1h": tx_vel_1h,
            "tx_velocity_24h": tx_vel_24h,
            "user_avg_amount_24h": user_avg,
            "amount_to_avg_ratio": amount_ratio if typology != "HIGH_AMOUNT" else round(amount / user_avg, 4),
            "time_of_day_hour": time_hour,
            "is_weekend": is_weekend,
            "device_fingerprint_age_days": device_age,
            "is_international": is_international,
            "account_age_days": account_age,
            "failed_attempts_1h": failed_attempts,
            "distance_from_home_km": distance_home,
            "is_fraud": 1
        })

    df = pd.DataFrame(data)
    # Shuffle dataset
    df = df.sample(frac=1.0, random_state=SEED).reset_index(drop=True)
    return df

if __name__ == "__main__":
    output_dir = os.path.dirname(os.path.abspath(__file__))
    output_path = os.path.join(output_dir, "synthetic_fraud_dataset.csv")

    df = generate_synthetic_transactions(n_samples=10000, fraud_ratio=0.05)
    df.to_csv(output_path, index=False)

    print(f"\n[SUCCESS] Synthetic dataset generated and saved to: {output_path}")
    print(f"Dataset Shape: {df.shape}")
    print(f"Class Balance:\n{df['is_fraud'].value_counts(normalize=True)}")
    print("\nSample 12-Dimension Feature Vector (First 3 rows):")
    print(df[FEATURE_COLUMNS].head(3))
