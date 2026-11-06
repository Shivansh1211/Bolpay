"""Authenticated, best-effort anomaly scoring. Never handles payment state."""
from flask import Flask, jsonify, request
import hmac
import joblib
import os
import numpy as np

app = Flask(__name__)
ML_API_KEY = os.environ.get("ML_API_KEY", "")
MODEL_VERSION = "2.0.0"
FEATURES = [
    "seconds_to_first_attempt", "time_pending", "expired_orders_per_device_1h",
    "expired_orders_per_device_24h", "timeouts_per_device_15m",
    "pending_orders_per_vendor_10m", "amount_z_score", "hour_of_day",
    "repeat_order_gap_seconds", "bulk_orders_per_device_10m",
]
MODEL_PATH = os.path.join(os.path.dirname(__file__), "models", "isolation_forest_model.pkl")

try:
    model = joblib.load(MODEL_PATH) if os.path.exists(MODEL_PATH) else None
except Exception as exc:
    print(f"Model load failed: {type(exc).__name__}")
    model = None


@app.before_request
def require_api_key():
    if request.path == "/health":
        return None
    if not ML_API_KEY:
        return jsonify({"error": "ML API key is not configured"}), 503
    if not hmac.compare_digest(request.headers.get("X-API-Key", ""), ML_API_KEY):
        return jsonify({"error": "Authentication required"}), 401


@app.get("/health")
def health():
    return jsonify({"status": "healthy", "model_loaded": model is not None,
                    "model_version": MODEL_VERSION}), 200


@app.get("/model-info")
def model_info():
    return jsonify({"model_version": MODEL_VERSION, "model_loaded": model is not None,
                    "features": FEATURES}), 200


def review_reasons(features):
    reasons = []
    timeout_count = int(features.get("timeouts_per_device_15m", 0))
    if timeout_count >= 3:
        reasons.append(f"Same device timed out {timeout_count} times in 15 minutes. Look at this order.")
    if features.get("pending_orders_per_vendor_10m", 0) >= 6:
        reasons.append("Several payments are still waiting for this vendor. Look at this order.")
    if abs(features.get("amount_z_score", 0)) >= 2:
        reasons.append("The amount is unusual for this vendor. Look at this order.")
    if features.get("bulk_orders_per_device_10m", 0) >= 4:
        reasons.append("Several orders came from the same device recently. Look at this order.")
    if not reasons:
        reasons.append("Unusual payment pattern. Look at this order.")
    return reasons[:5]


@app.post("/score")
def score():
    if model is None:
        return jsonify({"error": "Scoring model is unavailable"}), 503
    try:
        body = request.get_json(force=True)
        features = body.get("features", {})
        vector = np.asarray([[float(features.get(name, 0)) for name in FEATURES]], dtype=float)
        decision = float(model.decision_function(vector)[0])
        suspicious = int(model.predict(vector)[0]) == -1
        risk_score = float(-decision)
        return jsonify({
            "anomaly_score": risk_score,
            "is_suspicious": suspicious,
            "reasons": review_reasons(features) if suspicious else [],
            "model_version": MODEL_VERSION,
            "scorer": "isolation_forest",
        }), 200
    except (TypeError, ValueError, KeyError):
        return jsonify({"error": "Invalid numeric features"}), 400
    except Exception:
        return jsonify({"error": "Scoring failed"}), 500


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=int(os.environ.get("PORT", "5000")), debug=False)
