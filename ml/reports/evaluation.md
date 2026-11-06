# Synthetic review-signal evaluation

This is a reproducible synthetic experiment, not evidence of real-world fraud detection.
Synthetic labels and patterns limit all conclusions. Do not treat a score as an accusation.

Seed: 42; samples: 6000; holdout: 1200; synthetic positive rate: 0.167.

| Scorer | Precision | Recall | F1 | False positive rate | ROC-AUC | Confusion matrix [[TN,FP],[FN,TP]] |
|---|---:|---:|---:|---:|---:|---|
| Isolation Forest | 0.985 | 0.675 | 0.801 | 0.002 | 0.998 | `[[998, 2], [65, 135]]` |
| Random Forest | 1.000 | 0.990 | 0.995 | 0.000 | 1.000 | `[[1000, 0], [2, 198]]` |
| Rule baseline | 1.000 | 0.790 | 0.883 | 0.000 | 0.895 | `[[1000, 0], [42, 158]]` |

## Comparison

Random Forest beats the rule baseline by F1 on this synthetic split.

All labels and patterns are synthetic; these metrics do not establish real-world performance.

The operational fallback flags only when the current device has at least three recorded timeouts in 15 minutes. Model results do not change payment state.
