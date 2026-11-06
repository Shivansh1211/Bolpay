"""Deterministic synthetic dataset for review-signal experiments only."""
from pathlib import Path
import numpy as np
import pandas as pd

SEED = 42
NORMAL = 5000
SYNTHETIC_RISK = 1000
FEATURES = [
    "seconds_to_first_attempt", "time_pending", "expired_orders_per_device_1h",
    "expired_orders_per_device_24h", "timeouts_per_device_15m",
    "pending_orders_per_vendor_10m", "amount_z_score", "hour_of_day",
    "repeat_order_gap_seconds", "bulk_orders_per_device_10m",
]
OUT = Path(__file__).resolve().parent / "data" / "synthetic_fraud_data.csv"


def generate():
    rng = np.random.default_rng(SEED)
    normal = pd.DataFrame({
        "seconds_to_first_attempt": rng.exponential(30, NORMAL),
        "time_pending": rng.exponential(35, NORMAL),
        "expired_orders_per_device_1h": rng.poisson(.25, NORMAL),
        "expired_orders_per_device_24h": rng.poisson(1.5, NORMAL),
        "timeouts_per_device_15m": rng.binomial(1, .08, NORMAL),
        "pending_orders_per_vendor_10m": rng.poisson(1, NORMAL),
        "amount_z_score": rng.normal(0, 1, NORMAL),
        "hour_of_day": rng.integers(0, 24, NORMAL),
        "repeat_order_gap_seconds": rng.exponential(14400, NORMAL),
        "bulk_orders_per_device_10m": rng.poisson(.5, NORMAL),
        "is_fraud": np.zeros(NORMAL, dtype=int),
    })
    unusual_amount = rng.random(SYNTHETIC_RISK) < .35
    risk_amount_z = rng.normal(0, 1, SYNTHETIC_RISK)
    risk_amount_z[unusual_amount] = rng.normal(3, 1, unusual_amount.sum())
    risk = pd.DataFrame({
        "seconds_to_first_attempt": rng.exponential(8, SYNTHETIC_RISK),
        "time_pending": rng.exponential(100, SYNTHETIC_RISK),
        "expired_orders_per_device_1h": rng.poisson(4, SYNTHETIC_RISK),
        "expired_orders_per_device_24h": rng.poisson(9, SYNTHETIC_RISK),
        "timeouts_per_device_15m": rng.choice([0, 1, 2, 3, 4, 5], SYNTHETIC_RISK,
                                                p=[.04, .07, .12, .36, .25, .16]),
        "pending_orders_per_vendor_10m": rng.poisson(4, SYNTHETIC_RISK),
        "amount_z_score": risk_amount_z,
        "hour_of_day": rng.integers(0, 24, SYNTHETIC_RISK),
        "repeat_order_gap_seconds": rng.exponential(1500, SYNTHETIC_RISK),
        "bulk_orders_per_device_10m": rng.poisson(4, SYNTHETIC_RISK),
        "is_fraud": np.ones(SYNTHETIC_RISK, dtype=int),
    })
    # Give every synthetic-risk row deterministic high-signal timeout/bulk mixes.
    data = pd.concat([normal, risk], ignore_index=True)
    return data.sample(frac=1, random_state=SEED).reset_index(drop=True)


if __name__ == "__main__":
    OUT.parent.mkdir(parents=True, exist_ok=True)
    data = generate()
    data.to_csv(OUT, index=False)
    print(f"Generated {len(data)} rows at {OUT}; synthetic positive rate={data.is_fraud.mean():.3f}; seed={SEED}")
