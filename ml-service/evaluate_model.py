"""
ML Model Evaluation Script
Compares Isolation Forest vs Rule-based baseline
"""
import json
import numpy as np
import pandas as pd
from sklearn.ensemble import RandomForestClassifier
from sklearn.linear_model import LogisticRegression
from sklearn.model_selection import train_test_split, cross_val_score
from sklearn.metrics import (
    precision_score, recall_score, f1_score, 
    confusion_matrix, roc_auc_score, classification_report
)
import joblib
import matplotlib.pyplot as plt
import seaborn as sns
from datetime import datetime
import os

# Create reports directory
os.makedirs('reports', exist_ok=True)

# Load synthetic data
with open('synthetic_features.json', 'r') as f:
    features = json.load(f)

with open('synthetic_labels.json', 'r') as f:
    labels = json.load(f)

# Convert to DataFrame
df = pd.DataFrame(features)
df['label'] = labels

# Split features and labels
X = df.drop('label', axis=1)
y = df['label']

# Train/test split
X_train, X_test, y_train, y_test = train_test_split(X, y, test_size=0.2, random_state=42, stratify=y)

print(f"Training samples: {len(X_train)}")
print(f"Test samples: {len(X_test)}")
print(f"Fraud rate in test: {y_test.mean() * 100:.2f}%")

# 1. Rule-based baseline
def rule_based_predict(X):
    """Rule-based prediction logic"""
    predictions = []
    for _, row in X.iterrows():
        reasons = 0
        if row['expired_orders_last_1h'] > 3:
            reasons += 1
        if row['expired_orders_last_15min'] > 2:
            reasons += 1
        if row['pending_orders_last_10min'] > 5:
            reasons += 1
        if abs(row['amount_z_score']) > 2:
            reasons += 1
        if row['bulk_order_count_per_device'] > 3:
            reasons += 1
        predictions.append(1 if reasons >= 2 else 0)
    return np.array(predictions)

y_pred_rule = rule_based_predict(X_test)

# 2. Supervised baseline (Random Forest)
rf = RandomForestClassifier(n_estimators=100, random_state=42)
rf.fit(X_train, y_train)
y_pred_rf = rf.predict(X_test)
y_proba_rf = rf.predict_proba(X_test)[:, 1]

# 3. Supervised baseline (Logistic Regression)
lr = LogisticRegression(random_state=42, max_iter=1000)
lr.fit(X_train, y_train)
y_pred_lr = lr.predict(X_test)
y_proba_lr = lr.predict_proba(X_test)[:, 1]

# 4. Isolation Forest (unsupervised)
try:
    from sklearn.ensemble import IsolationForest
    iso_forest = IsolationForest(contamination=0.1, random_state=42)
    iso_forest.fit(X_train)
    y_pred_iso = iso_forest.predict(X_test)
    y_pred_iso = np.where(y_pred_iso == -1, 1, 0)  # Convert -1 to 1 (fraud)
except:
    print("Isolation Forest not available, skipping")
    y_pred_iso = np.zeros_like(y_test)

# Calculate metrics for each model
def calculate_metrics(y_true, y_pred, y_proba=None):
    metrics = {
        'precision': precision_score(y_true, y_pred, zero_division=0),
        'recall': recall_score(y_true, y_pred, zero_division=0),
        'f1': f1_score(y_true, y_pred, zero_division=0),
    }
    if y_proba is not None:
        metrics['roc_auc'] = roc_auc_score(y_true, y_proba)
    return metrics

metrics_rule = calculate_metrics(y_test, y_pred_rule)
metrics_rf = calculate_metrics(y_test, y_pred_rf, y_proba_rf)
metrics_lr = calculate_metrics(y_test, y_pred_lr, y_proba_lr)
metrics_iso = calculate_metrics(y_test, y_pred_iso)

# Print results
print("\n" + "="*60)
print("MODEL EVALUATION RESULTS")
print("="*60)

print("\nRule-Based Baseline:")
print(f"  Precision: {metrics_rule['precision']:.3f}")
print(f"  Recall: {metrics_rule['recall']:.3f}")
print(f"  F1-Score: {metrics_rule['f1']:.3f}")

print("\nRandom Forest (Supervised):")
print(f"  Precision: {metrics_rf['precision']:.3f}")
print(f"  Recall: {metrics_rf['recall']:.3f}")
print(f"  F1-Score: {metrics_rf['f1']:.3f}")
print(f"  ROC-AUC: {metrics_rf['roc_auc']:.3f}")

print("\nLogistic Regression (Supervised):")
print(f"  Precision: {metrics_lr['precision']:.3f}")
print(f"  Recall: {metrics_lr['recall']:.3f}")
print(f"  F1-Score: {metrics_lr['f1']:.3f}")
print(f"  ROC-AUC: {metrics_lr['roc_auc']:.3f}")

print("\nIsolation Forest (Unsupervised):")
print(f"  Precision: {metrics_iso['precision']:.3f}")
print(f"  Recall: {metrics_iso['recall']:.3f}")
print(f"  F1-Score: {metrics_iso['f1']:.3f}")

# Determine which model performs best
best_model = max([
    ('Rule-Based', metrics_rule['f1']),
    ('Random Forest', metrics_rf['f1']),
    ('Logistic Regression', metrics_lr['f1']),
    ('Isolation Forest', metrics_iso['f1'])
], key=lambda x: x[1])

print(f"\nBest performing model: {best_model[0]} (F1: {best_model[1]:.3f})")

# Generate confusion matrices
fig, axes = plt.subplots(2, 2, figsize=(12, 10))
axes = axes.ravel()

cm_rule = confusion_matrix(y_test, y_pred_rule)
cm_rf = confusion_matrix(y_test, y_pred_rf)
cm_lr = confusion_matrix(y_test, y_pred_lr)
cm_iso = confusion_matrix(y_test, y_pred_iso)

sns.heatmap(cm_rule, annot=True, fmt='d', ax=axes[0], cmap='Blues')
axes[0].set_title('Rule-Based Confusion Matrix')
axes[0].set_ylabel('True')
axes[0].set_xlabel('Predicted')

sns.heatmap(cm_rf, annot=True, fmt='d', ax=axes[1], cmap='Blues')
axes[1].set_title('Random Forest Confusion Matrix')
axes[1].set_ylabel('True')
axes[1].set_xlabel('Predicted')

sns.heatmap(cm_lr, annot=True, fmt='d', ax=axes[2], cmap='Blues')
axes[2].set_title('Logistic Regression Confusion Matrix')
axes[2].set_ylabel('True')
axes[2].set_xlabel('Predicted')

sns.heatmap(cm_iso, annot=True, fmt='d', ax=axes[3], cmap='Blues')
axes[3].set_title('Isolation Forest Confusion Matrix')
axes[3].set_ylabel('True')
axes[3].set_xlabel('Predicted')

plt.tight_layout()
plt.savefig('reports/confusion_matrices.png')
print("\nConfusion matrices saved to reports/confusion_matrices.png")

# Save metrics to JSON
report = {
    'timestamp': datetime.now().isoformat(),
    'total_samples': len(y_test),
    'fraud_samples': y_test.sum(),
    'normal_samples': len(y_test) - y_test.sum(),
    'models': {
        'rule_based': metrics_rule,
        'random_forest': metrics_rf,
        'logistic_regression': metrics_lr,
        'isolation_forest': metrics_iso
    },
    'best_model': best_model[0],
    'ml_beats_rules': best_model[1] > metrics_rule['f1']
}

with open('reports/evaluation_report.json', 'w') as f:
    json.dump(report, f, indent=2)

print("Evaluation report saved to reports/evaluation_report.json")

# Save best model for production
if best_model[0] == 'Random Forest':
    joblib.dump(rf, 'models/best_model.pkl')
    print("Best model (Random Forest) saved to models/best_model.pkl")
elif best_model[0] == 'Logistic Regression':
    joblib.dump(lr, 'models/best_model.pkl')
    print("Best model (Logistic Regression) saved to models/best_model.pkl")

print("\nEvaluation complete!")
