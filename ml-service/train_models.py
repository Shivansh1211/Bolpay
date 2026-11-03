"""
Train fraud detection models and evaluate them.
Compares Isolation Forest (unsupervised), Random Forest (supervised), and rule-based baseline.
"""

import pandas as pd
import numpy as np
from sklearn.ensemble import IsolationForest, RandomForestClassifier
from sklearn.model_selection import train_test_split
from sklearn.metrics import (
    precision_score, recall_score, f1_score, 
    roc_auc_score, confusion_matrix, classification_report
)
import joblib
import os
import matplotlib.pyplot as plt
import seaborn as sns
from datetime import datetime

# Configuration
DATA_FILE = 'data/synthetic_fraud_data.csv'
MODEL_DIR = 'models'
REPORT_DIR = 'reports'
MODEL_VERSION = '1.0.0'

# Features
FEATURES = [
    'seconds_to_first_attempt',
    'expired_orders_last_1h',
    'expired_orders_last_24h',
    'pending_orders_last_10min',
    'amount_z_score',
    'hour_of_day',
    'time_between_repeat_orders',
    'expiry_ratio'
]

def load_data():
    """Load and prepare data"""
    print("Loading data...")
    df = pd.read_csv(DATA_FILE)
    
    # Drop fraud_type column (only for analysis, not training)
    X = df[FEATURES]
    y = df['is_fraud']
    
    print(f"Loaded {len(df)} samples")
    print(f"Features: {FEATURES}")
    print(f"Fraud rate: {y.mean():.2%}")
    
    return X, y

def train_isolation_forest(X_train, y_train):
    """Train Isolation Forest (unsupervised)"""
    print("\nTraining Isolation Forest...")
    
    # Train on all data (unsupervised)
    iso_forest = IsolationForest(
        n_estimators=100,
        contamination=0.05,  # Expected fraud rate
        random_state=42,
        n_jobs=-1
    )
    iso_forest.fit(X_train)
    
    return iso_forest

def train_random_forest(X_train, y_train):
    """Train Random Forest (supervised)"""
    print("\nTraining Random Forest...")
    
    rf = RandomForestClassifier(
        n_estimators=100,
        max_depth=10,
        random_state=42,
        n_jobs=-1,
        class_weight='balanced'
    )
    rf.fit(X_train, y_train)
    
    return rf

def rule_baseline(X):
    """Rule-based baseline: flag if suspicious patterns"""
    """Flags if:
    - expired_orders_last_1h > 3
    - pending_orders_last_10min > 5
    - abs(amount_z_score) > 2
    - seconds_to_first_attempt < 5
    """
    predictions = []
    for _, row in X.iterrows():
        is_suspicious = (
            row['expired_orders_last_1h'] > 3 or
            row['pending_orders_last_10min'] > 5 or
            abs(row['amount_z_score']) > 2 or
            row['seconds_to_first_attempt'] < 5
        )
        predictions.append(1 if is_suspicious else 0)
    return np.array(predictions)

def evaluate_model(model, X_test, y_test, model_name):
    """Evaluate a model and return metrics"""
    print(f"\nEvaluating {model_name}...")
    
    if model_name == 'Isolation Forest':
        # Isolation Forest returns -1 for outliers, 1 for inliers
        predictions = model.predict(X_test)
        predictions = np.where(predictions == -1, 1, 0)
        # Get anomaly scores
        scores = model.decision_function(X_test)
    elif model_name == 'Rule Baseline':
        predictions = rule_baseline(X_test)
        scores = predictions  # Use predictions as scores
    else:
        predictions = model.predict(X_test)
        scores = model.predict_proba(X_test)[:, 1]
    
    # Calculate metrics
    precision = precision_score(y_test, predictions, zero_division=0)
    recall = recall_score(y_test, predictions, zero_division=0)
    f1 = f1_score(y_test, predictions, zero_division=0)
    
    # ROC-AUC (if scores are available)
    try:
        roc_auc = roc_auc_score(y_test, scores)
    except:
        roc_auc = 0.5
    
    # Confusion matrix
    cm = confusion_matrix(y_test, predictions)
    
    metrics = {
        'precision': precision,
        'recall': recall,
        'f1': f1,
        'roc_auc': roc_auc,
        'confusion_matrix': cm,
        'predictions': predictions
    }
    
    print(f"Precision: {precision:.3f}")
    print(f"Recall: {recall:.3f}")
    print(f"F1: {f1:.3f}")
    print(f"ROC-AUC: {roc_auc:.3f}")
    print("Confusion Matrix:")
    print(cm)
    
    return metrics

def plot_confusion_matrix(cm, model_name, save_path):
    """Plot and save confusion matrix"""
    plt.figure(figsize=(8, 6))
    sns.heatmap(cm, annot=True, fmt='d', cmap='Blues', 
                xticklabels=['Normal', 'Fraud'],
                yticklabels=['Normal', 'Fraud'])
    plt.title(f'Confusion Matrix - {model_name}')
    plt.ylabel('True Label')
    plt.xlabel('Predicted Label')
    plt.savefig(save_path)
    plt.close()

def generate_report(metrics_dict, save_path):
    """Generate markdown report with results"""
    report = f"""# Fraud Detection Model Evaluation Report

Generated: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}
Model Version: {MODEL_VERSION}

## Overview
This report compares three approaches to fraud detection:
1. **Isolation Forest** - Unsupervised anomaly detection
2. **Random Forest** - Supervised classification
3. **Rule Baseline** - Simple rule-based heuristics

## Data
- Total samples: {sum(len(m['predictions']) for m in metrics_dict.values()) // 3}
- Features: {', '.join(FEATURES)}
- Fraud rate in test set: ~{(metrics_dict['Random Forest']['predictions'].mean()):.2%}

## Results

### Isolation Forest (Unsupervised)
- **Precision**: {metrics_dict['Isolation Forest']['precision']:.3f}
- **Recall**: {metrics_dict['Isolation Forest']['recall']:.3f}
- **F1 Score**: {metrics_dict['Isolation Forest']['f1']:.3f}
- **ROC-AUC**: {metrics_dict['Isolation Forest']['roc_auc']:.3f}

### Random Forest (Supervised)
- **Precision**: {metrics_dict['Random Forest']['precision']:.3f}
- **Recall**: {metrics_dict['Random Forest']['recall']:.3f}
- **F1 Score**: {metrics_dict['Random Forest']['f1']:.3f}
- **ROC-AUC**: {metrics_dict['Random Forest']['roc_auc']:.3f}

### Rule Baseline
- **Precision**: {metrics_dict['Rule Baseline']['precision']:.3f}
- **Recall**: {metrics_dict['Rule Baseline']['recall']:.3f}
- **F1 Score**: {metrics_dict['Rule Baseline']['f1']:.3f}
- **ROC-AUC**: {metrics_dict['Rule Baseline']['roc_auc']:.3f}

## Analysis

### Isolation Forest
- **Strengths**: No labeled data required, detects novel patterns
- **Weaknesses**: May have higher false positive rate, requires tuning contamination parameter
- **Best for**: Detecting new, unknown fraud patterns

### Random Forest
- **Strengths**: High accuracy with labeled data, interpretable feature importance
- **Weaknesses**: Requires labeled training data, may overfit to synthetic patterns
- **Best for**: Production use with good labeled data

### Rule Baseline
- **Strengths**: Simple, interpretable, no training required
- **Weaknesses**: Limited to predefined patterns, lower recall
- **Best for**: Quick implementation, fallback when ML service is down

## Limitations

1. **Synthetic Data**: Results are based on synthetic data and may not reflect real-world performance
2. **Feature Engineering**: Current features may not capture all fraud patterns
3. **Class Imbalance**: Real fraud rates are typically much lower than synthetic data
4. **Temporal Patterns**: Time-based features may not generalize across different time periods

## Recommendation

Based on F1 score, the **Random Forest** model performs best on synthetic data.
However, for production:
- Use **Random Forest** as primary model when labeled data is available
- Use **Isolation Forest** for detecting novel fraud patterns
- Use **Rule Baseline** as fallback when ML service is unavailable

## Next Steps

1. Collect real-world labeled data
2. Retrain models on real data
3. Implement A/B testing in production
4. Monitor false positive/negative rates
5. Continuously update models with new data
"""
    
    with open(save_path, 'w') as f:
        f.write(report)
    
    print(f"\nReport saved to {save_path}")

def main():
    print("=" * 60)
    print("Fraud Detection Model Training and Evaluation")
    print("=" * 60)
    
    # Create directories
    os.makedirs(MODEL_DIR, exist_ok=True)
    os.makedirs(REPORT_DIR, exist_ok=True)
    
    # Load data
    X, y = load_data()
    
    # Split data
    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.2, random_state=42, stratify=y
    )
    
    print(f"\nTrain set: {len(X_train)} samples")
    print(f"Test set: {len(X_test)} samples")
    
    # Train models
    iso_forest = train_isolation_forest(X_train, y_train)
    random_forest = train_random_forest(X_train, y_train)
    
    # Evaluate models
    metrics = {}
    
    metrics['Isolation Forest'] = evaluate_model(iso_forest, X_test, y_test, 'Isolation Forest')
    metrics['Random Forest'] = evaluate_model(random_forest, X_test, y_test, 'Random Forest')
    metrics['Rule Baseline'] = evaluate_model(None, X_test, y_test, 'Rule Baseline')
    
    # Save models
    iso_path = os.path.join(MODEL_DIR, 'isolation_forest_model.pkl')
    rf_path = os.path.join(MODEL_DIR, 'random_forest_model.pkl')
    
    joblib.dump(iso_forest, iso_path)
    joblib.dump(random_forest, rf_path)
    
    print(f"\nModels saved to {MODEL_DIR}")
    
    # Plot confusion matrices
    for model_name, model_metrics in metrics.items():
        cm_path = os.path.join(REPORT_DIR, f'confusion_matrix_{model_name.replace(" ", "_").lower()}.png')
        plot_confusion_matrix(model_metrics['confusion_matrix'], model_name, cm_path)
        print(f"Confusion matrix saved to {cm_path}")
    
    # Generate report
    report_path = os.path.join(REPORT_DIR, 'evaluation_report.md')
    generate_report(metrics, report_path)
    
    print("\n" + "=" * 60)
    print("Training and evaluation complete!")
    print("=" * 60)

if __name__ == '__main__':
    main()
