"""
Synthetic data generator for fraud detection training.
Simulates normal vendor traffic and fraud patterns.
"""

import pandas as pd
import numpy as np
from datetime import datetime, timedelta
import random
import json
import os

# Configuration
NUM_NORMAL_SAMPLES = 5000
NUM_FRAUD_SAMPLES = 1000
OUTPUT_DIR = 'data'
OUTPUT_FILE = os.path.join(OUTPUT_DIR, 'synthetic_fraud_data.csv')

def generate_normal_traffic():
    """Generate normal vendor payment patterns"""
    data = []
    
    for _ in range(NUM_NORMAL_SAMPLES):
        # Normal patterns
        seconds_to_first_attempt = np.random.exponential(scale=30)  # Most pay within 30s
        expired_orders_last_1h = np.random.poisson(0.5)  # Rare expired orders
        expired_orders_last_24h = np.random.poisson(2)
        pending_orders_last_10min = np.random.poisson(1)
        
        # Amount follows log-normal distribution (typical for payments)
        amount = np.random.lognormal(mean=3, sigma=0.5)  # Mean around ₹20
        
        # Calculate z-score based on vendor's typical amount (assume mean ₹25, std ₹15)
        amount_z_score = (amount - 25) / 15
        
        hour_of_day = np.random.randint(0, 24)
        
        # Time between repeat orders (hours)
        time_between_repeat_orders = np.random.exponential(scale=4) if np.random.random() > 0.7 else 0
        
        # Expiry ratio (expired / total)
        expiry_ratio = min(1.0, np.random.beta(0.5, 5))  # Most have low expiry ratio
        
        # Label: 0 = normal, 1 = fraud
        is_fraud = 0
        
        data.append({
            'seconds_to_first_attempt': seconds_to_first_attempt,
            'expired_orders_last_1h': expired_orders_last_1h,
            'expired_orders_last_24h': expired_orders_last_24h,
            'pending_orders_last_10min': pending_orders_last_10min,
            'amount_z_score': amount_z_score,
            'hour_of_day': hour_of_day,
            'time_between_repeat_orders': time_between_repeat_orders,
            'expiry_ratio': expiry_ratio,
            'is_fraud': is_fraud
        })
    
    return data

def generate_fraud_patterns():
    """Generate fraudulent payment patterns"""
    data = []
    
    fraud_types = ['repeated_expired', 'screenshot_replay', 'rapid_retries', 'unusual_amount']
    
    for _ in range(NUM_FRAUD_SAMPLES):
        fraud_type = random.choice(fraud_types)
        
        if fraud_type == 'repeated_expired':
            # Many expired orders in short time
            seconds_to_first_attempt = np.random.exponential(scale=5)
            expired_orders_last_1h = np.random.poisson(5)  # High expired count
            expired_orders_last_24h = np.random.poisson(10)
            pending_orders_last_10min = np.random.poisson(3)
            amount_z_score = np.random.normal(0, 1)
            hour_of_day = np.random.randint(0, 24)
            time_between_repeat_orders = np.random.exponential(scale=0.5)  # Very rapid
            expiry_ratio = min(1.0, np.random.beta(3, 1))  # High expiry ratio
            
        elif fraud_type == 'screenshot_replay':
            # Very fast attempts (automated)
            seconds_to_first_attempt = np.random.uniform(0, 2)
            expired_orders_last_1h = np.random.poisson(2)
            expired_orders_last_24h = np.random.poisson(5)
            pending_orders_last_10min = np.random.poisson(2)
            amount_z_score = np.random.normal(0, 0.5)  # Consistent amounts
            hour_of_day = np.random.randint(0, 24)
            time_between_repeat_orders = 0  # Instant retries
            expiry_ratio = min(1.0, np.random.beta(2, 2))
            
        elif fraud_type == 'rapid_retries':
            # Rapid attempts across vendors
            seconds_to_first_attempt = np.random.exponential(scale=10)
            expired_orders_last_1h = np.random.poisson(3)
            expired_orders_last_24h = np.random.poisson(8)
            pending_orders_last_10min = np.random.poisson(5)  # Many pending
            amount_z_score = np.random.normal(0, 1)
            hour_of_day = np.random.randint(0, 24)
            time_between_repeat_orders = np.random.exponential(scale=1)
            expiry_ratio = min(1.0, np.random.beta(2, 1))
            
        else:  # unusual_amount
            # Unusual amounts (testing limits)
            seconds_to_first_attempt = np.random.exponential(scale=20)
            expired_orders_last_1h = np.random.poisson(1)
            expired_orders_last_24h = np.random.poisson(3)
            pending_orders_last_10min = np.random.poisson(1)
            amount_z_score = np.random.choice([3, -3, 4, -4])  # Extreme z-scores
            hour_of_day = np.random.randint(0, 24)
            time_between_repeat_orders = np.random.exponential(scale=2)
            expiry_ratio = min(1.0, np.random.beta(1, 3))
        
        data.append({
            'seconds_to_first_attempt': seconds_to_first_attempt,
            'expired_orders_last_1h': expired_orders_last_1h,
            'expired_orders_last_24h': expired_orders_last_24h,
            'pending_orders_last_10min': pending_orders_last_10min,
            'amount_z_score': amount_z_score,
            'hour_of_day': hour_of_day,
            'time_between_repeat_orders': time_between_repeat_orders,
            'expiry_ratio': expiry_ratio,
            'is_fraud': 1,
            'fraud_type': fraud_type
        })
    
    return data

def main():
    print("Generating synthetic fraud detection data...")
    
    # Create output directory
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    
    # Generate data
    print(f"Generating {NUM_NORMAL_SAMPLES} normal samples...")
    normal_data = generate_normal_traffic()
    
    print(f"Generating {NUM_FRAUD_SAMPLES} fraud samples...")
    fraud_data = generate_fraud_patterns()
    
    # Combine
    all_data = normal_data + fraud_data
    
    # Create DataFrame
    df = pd.DataFrame(all_data)
    
    # Shuffle
    df = df.sample(frac=1).reset_index(drop=True)
    
    # Save
    df.to_csv(OUTPUT_FILE, index=False)
    
    print(f"\nData saved to {OUTPUT_FILE}")
    print(f"Total samples: {len(df)}")
    print(f"Normal samples: {len(df[df['is_fraud'] == 0])}")
    print(f"Fraud samples: {len(df[df['is_fraud'] == 1])}")
    print(f"Fraud rate: {df['is_fraud'].mean():.2%}")
    
    # Print fraud type distribution
    if 'fraud_type' in df.columns:
        print("\nFraud type distribution:")
        print(df[df['is_fraud'] == 1]['fraud_type'].value_counts())
    
    # Print feature statistics
    print("\nFeature statistics:")
    print(df.describe())

if __name__ == '__main__':
    main()
