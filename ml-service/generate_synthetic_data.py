"""
Synthetic data generator for ML fraud detection
Generates normal traffic and fraud patterns
"""
import json
import random
import numpy as np
from datetime import datetime, timedelta

def generate_normal_traffic(n=1000):
    """Generate normal payment traffic patterns"""
    data = []
    for _ in range(n):
        # Normal patterns: honest customers, occasional abandons
        data.append({
            'seconds_to_first_attempt': random.uniform(10, 300),
            'expired_orders_last_1h': random.randint(0, 2),
            'expired_orders_last_24h': random.randint(0, 5),
            'expired_orders_last_15min': random.randint(0, 1),
            'pending_orders_last_10min': random.randint(0, 3),
            'amount_z_score': random.gauss(0, 1),
            'hour_of_day': random.randint(0, 23),
            'time_between_repeat_orders': random.uniform(300, 3600),
            'expiry_ratio': random.uniform(0, 0.2),
            'time_pending': random.uniform(30, 120),
            'bulk_order_count_per_device': random.randint(0, 2),
            'label': 0  # Normal
        })
    return data

def generate_fraud_traffic(n=100):
    """Generate fraud patterns"""
    data = []
    
    # Pattern 1: Repeated timeouts
    for _ in range(n // 3):
        data.append({
            'seconds_to_first_attempt': random.uniform(5, 30),
            'expired_orders_last_1h': random.randint(5, 15),
            'expired_orders_last_24h': random.randint(10, 30),
            'expired_orders_last_15min': random.randint(3, 8),
            'pending_orders_last_10min': random.randint(5, 15),
            'amount_z_score': random.gauss(0, 1),
            'hour_of_day': random.randint(0, 23),
            'time_between_repeat_orders': random.uniform(10, 60),
            'expiry_ratio': random.uniform(0.7, 1.0),
            'time_pending': random.uniform(100, 200),
            'bulk_order_count_per_device': random.randint(0, 3),
            'label': 1  # Fraud
        })
    
    # Pattern 2: Bulk resale-style orders
    for _ in range(n // 3):
        data.append({
            'seconds_to_first_attempt': random.uniform(5, 20),
            'expired_orders_last_1h': random.randint(2, 5),
            'expired_orders_last_24h': random.randint(5, 10),
            'expired_orders_last_15min': random.randint(1, 3),
            'pending_orders_last_10min': random.randint(3, 8),
            'amount_z_score': random.gauss(2, 1),  # High amounts
            'hour_of_day': random.randint(0, 23),
            'time_between_repeat_orders': random.uniform(5, 30),
            'expiry_ratio': random.uniform(0.3, 0.6),
            'time_pending': random.uniform(20, 60),
            'bulk_order_count_per_device': random.randint(5, 20),
            'label': 1  # Fraud
        })
    
    # Pattern 3: Rapid retries across vendors
    for _ in range(n // 3):
        data.append({
            'seconds_to_first_attempt': random.uniform(2, 10),
            'expired_orders_last_1h': random.randint(3, 8),
            'expired_orders_last_24h': random.randint(8, 15),
            'expired_orders_last_15min': random.randint(2, 5),
            'pending_orders_last_10min': random.randint(4, 10),
            'amount_z_score': random.gauss(0, 1),
            'hour_of_day': random.randint(0, 23),
            'time_between_repeat_orders': random.uniform(5, 20),
            'expiry_ratio': random.uniform(0.5, 0.8),
            'time_pending': random.uniform(30, 90),
            'bulk_order_count_per_device': random.randint(0, 2),
            'label': 1  # Fraud
        })
    
    return data

if __name__ == '__main__':
    normal = generate_normal_traffic(1000)
    fraud = generate_fraud_traffic(300)
    
    all_data = normal + fraud
    random.shuffle(all_data)
    
    # Split features and labels
    features = [{k: v for k, v in d.items() if k != 'label'} for d in all_data]
    labels = [d['label'] for d in all_data]
    
    # Save to files
    with open('synthetic_features.json', 'w') as f:
        json.dump(features, f, indent=2)
    
    with open('synthetic_labels.json', 'w') as f:
        json.dump(labels, f, indent=2)
    
    print(f"Generated {len(normal)} normal and {len(fraud)} fraud samples")
    print(f"Total: {len(all_data)} samples")
    print(f"Fraud rate: {len(fraud) / len(all_data) * 100:.2f}%")
