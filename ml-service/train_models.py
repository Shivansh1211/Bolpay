"""Reproducible Isolation Forest, supervised baseline, and rule evaluation."""
from pathlib import Path
import json
import joblib
import numpy as np
import pandas as pd
from sklearn.ensemble import IsolationForest, RandomForestClassifier
from sklearn.metrics import (confusion_matrix, f1_score, precision_score,
                             recall_score, roc_auc_score)
from sklearn.model_selection import train_test_split
from generate_data import FEATURES, OUT, SEED, generate

ROOT = Path(__file__).resolve().parent
REPORTS = ROOT.parent / "ml" / "reports"
MODEL_DIR = ROOT / "models"


def rule_predict(frame):
    # The operational fallback's rule: three timeouts including this order in 15m.
    return (frame["timeouts_per_device_15m"].to_numpy() >= 3).astype(int)


def metrics(y, predicted, scores):
    tn, fp, fn, tp = confusion_matrix(y, predicted, labels=[0, 1]).ravel()
    return {
        "precision": float(precision_score(y, predicted, zero_division=0)),
        "recall": float(recall_score(y, predicted, zero_division=0)),
        "f1": float(f1_score(y, predicted, zero_division=0)),
        "false_positive_rate": float(fp / (fp + tn)) if fp + tn else 0.0,
        "roc_auc": float(roc_auc_score(y, scores)),
        "confusion_matrix": [[int(tn), int(fp)], [int(fn), int(tp)]],
    }


def main():
    np.random.seed(SEED)
    REPORTS.mkdir(parents=True, exist_ok=True)
    MODEL_DIR.mkdir(parents=True, exist_ok=True)
    data = generate()
    OUT.parent.mkdir(parents=True, exist_ok=True)
    data.to_csv(OUT, index=False)
    x_train, x_test, y_train, y_test = train_test_split(
        data[FEATURES], data["is_fraud"], test_size=.2, stratify=data["is_fraud"], random_state=SEED)

    iso = IsolationForest(n_estimators=200, contamination=.12, random_state=SEED, n_jobs=1)
    iso.fit(x_train)
    rf = RandomForestClassifier(n_estimators=200, max_depth=10, class_weight="balanced",
                                random_state=SEED, n_jobs=1)
    rf.fit(x_train, y_train)
    rule = rule_predict(x_test)

    iso_pred = (iso.predict(x_test) == -1).astype(int)
    iso_score = -iso.decision_function(x_test)
    rf_score = rf.predict_proba(x_test)[:, 1]
    rf_pred = (rf_score >= .5).astype(int)
    results = {
        "isolation_forest": metrics(y_test, iso_pred, iso_score),
        "random_forest": metrics(y_test, rf_pred, rf_score),
        "rule_baseline": metrics(y_test, rule, rule.astype(float)),
    }
    ml_best = max((results["isolation_forest"]["f1"], "Isolation Forest"),
                  (results["random_forest"]["f1"], "Random Forest"))
    beats = ml_best[0] > results["rule_baseline"]["f1"]
    report = {
        "seed": SEED, "total_samples": int(len(data)), "test_samples": int(len(y_test)),
        "synthetic_positive_rate": float(data["is_fraud"].mean()), "features": FEATURES,
        "metrics": results, "best_ml_by_f1": ml_best[1],
        "ml_beats_rule_by_f1": bool(beats),
        "conclusion": (f"{ml_best[1]} beats the rule baseline by F1 on this synthetic split." if beats
                       else "Neither ML model beats the rule baseline by F1 on this synthetic split."),
        "limitation": "All labels and patterns are synthetic; these metrics do not establish real-world performance.",
    }
    (REPORTS / "metrics.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    lines = ["# Synthetic review-signal evaluation", "", "This is a reproducible synthetic experiment, not evidence of real-world fraud detection.",
             "Synthetic labels and patterns limit all conclusions. Do not treat a score as an accusation.",
             "", f"Seed: {SEED}; samples: {len(data)}; holdout: {len(y_test)}; synthetic positive rate: {data.is_fraud.mean():.3f}.",
             "", "| Scorer | Precision | Recall | F1 | False positive rate | ROC-AUC | Confusion matrix [[TN,FP],[FN,TP]] |",
             "|---|---:|---:|---:|---:|---:|---|"]
    labels = [("isolation_forest", "Isolation Forest"), ("random_forest", "Random Forest"), ("rule_baseline", "Rule baseline")]
    for key, label in labels:
        m = results[key]
        lines.append(f"| {label} | {m['precision']:.3f} | {m['recall']:.3f} | {m['f1']:.3f} | {m['false_positive_rate']:.3f} | {m['roc_auc']:.3f} | `{m['confusion_matrix']}` |")
        (REPORTS / f"confusion-matrix-{key}.json").write_text(json.dumps(m["confusion_matrix"]) + "\n", encoding="utf-8")
    lines += ["", f"## Comparison", "", report["conclusion"], "", report["limitation"], "",
              "The operational fallback flags only when the current device has at least three recorded timeouts in 15 minutes. Model results do not change payment state.", ""]
    (REPORTS / "evaluation.md").write_text("\n".join(lines), encoding="utf-8")
    joblib.dump(iso, MODEL_DIR / "isolation_forest_model.pkl")
    print(f"Seed: {SEED}")
    for key, label in labels:
        m = results[key]
        print(f"{label}: precision={m['precision']:.3f} recall={m['recall']:.3f} f1={m['f1']:.3f} fpr={m['false_positive_rate']:.3f} roc_auc={m['roc_auc']:.3f} confusion={m['confusion_matrix']}")
    print(report["conclusion"])
    print(report["limitation"])
    print(f"Reports: {REPORTS}")


if __name__ == "__main__":
    main()
