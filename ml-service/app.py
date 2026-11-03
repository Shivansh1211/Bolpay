from flask import Flask, request, jsonify
from flask_cors import CORS
import joblib
import os
import numpy as np

app = Flask(__name__)
CORS(app)

# Model version and path
MODEL_VERSION = "1.0.0"
MODEL_PATH = os.path.join(os.path.dirname(__file__), 'models', 'isolation_forest_model.pkl')

# Load model if available, otherwise return error
model = None
try:
    if os.path.exists(MODEL_PATH):
        model = joblib.load(MODEL_PATH)
        print(f"Model loaded successfully from {MODEL_PATH}")
    else:
        print(f"Warning: Model file not found at {MODEL_PATH}")
except Exception as e:
    print(f"Warning: Could not load model: {e}")


@app.route('/health', methods=['GET'])
def health():
    """Health check endpoint"""
    return jsonify({
        'status': 'healthy',
        'model_loaded': model is not None,
        'model_version': MODEL_VERSION
    }), 200


@app.route('/model-info', methods=['GET'])
def model_info():
    """Return model information"""
    return jsonify({
        'model_version': MODEL_VERSION,
        'model_loaded': model is not None,
        'model_type': type(model).__name__ if model else 'Not loaded',
        'features': [
            'seconds_to_first_attempt',
            'expired_orders_last_1h',
            'expired_orders_last_24h',
            'expired_orders_last_15min',
            'pending_orders_last_10min',
            'amount_z_score',
            'hour_of_day',
            'time_between_repeat_orders',
            'expiry_ratio',
            'time_pending',
            'bulk_order_count_per_device'
        ]
    }), 200


@app.route('/score', methods=['POST'])
def score():
    """
    Score an order for fraud detection.
    
    Expected JSON body:
    {
        "features": {
            "seconds_to_first_attempt": float,
            "expired_orders_last_1h": int,
            "expired_orders_last_24h": int,
            "expired_orders_last_15min": int,
            "pending_orders_last_10min": int,
            "amount_z_score": float,
            "hour_of_day": int,
            "time_between_repeat_orders": float,
            "expiry_ratio": float,
            "time_pending": float,
            "bulk_order_count_per_device": int
        }
    }
    
    Returns:
    {
        "anomaly_score": float,
        "is_suspicious": bool,
        "reasons": [string]
    }
    """
    if model is None:
        # Fallback to rule-based scoring if model is not loaded
        return fallback_score(request.json)
    
    try:
        data = request.json
        features = data.get('features', {})
        
        # Extract features in the correct order
        feature_vector = np.array([
            features.get('seconds_to_first_attempt', 0),
            features.get('expired_orders_last_1h', 0),
            features.get('expired_orders_last_24h', 0),
            features.get('expired_orders_last_15min', 0),
            features.get('pending_orders_last_10min', 0),
            features.get('amount_z_score', 0),
            features.get('hour_of_day', 12),
            features.get('time_between_repeat_orders', 0),
            features.get('expiry_ratio', 0),
            features.get('time_pending', 0),
            features.get('bulk_order_count_per_device', 0)
        ]).reshape(1, -1)
        
        # Get anomaly score (Isolation Forest returns -1 for outliers, 1 for inliers)
        anomaly_score = model.decision_function(feature_vector)[0]
        is_outlier = model.predict(feature_vector)[0] == -1
        
        # Generate reasons based on features
        reasons = []
        if features.get('expired_orders_last_1h', 0) > 3:
            reasons.append("High rate of expired orders in last hour")
        if features.get('expired_orders_last_15min', 0) > 2:
            reasons.append("Multiple timeouts in last 15 minutes")
        if features.get('pending_orders_last_10min', 0) > 5:
            reasons.append("Many pending orders for this vendor")
        if abs(features.get('amount_z_score', 0)) > 2:
            reasons.append("Unusual amount compared to vendor's average")
        if features.get('seconds_to_first_attempt', 0) < 5:
            reasons.append("Suspiciously fast payment attempt")
        if features.get('bulk_order_count_per_device', 0) > 3:
            reasons.append("Bulk orders from same device")
        
        return jsonify({
            'anomaly_score': float(anomaly_score),
            'is_suspicious': is_outlier,
            'reasons': reasons,
            'model_version': MODEL_VERSION
        }), 200
        
    except Exception as e:
        return jsonify({'error': str(e)}), 500


def fallback_score(data):
    """
    Rule-based fallback scoring when ML model is not available.
    Flags if unpaid after 60s or has suspicious patterns.
    """
    try:
        features = data.get('features', {})
        reasons = []
        is_suspicious = False
        
        # Rule 1: High expired orders in last hour
        if features.get('expired_orders_last_1h', 0) > 3:
            reasons.append("High rate of expired orders in last hour")
            is_suspicious = True
        
        # Rule 2: Timeouts in last 15 minutes
        if features.get('expired_orders_last_15min', 0) > 2:
            reasons.append("Multiple timeouts in last 15 minutes")
            is_suspicious = True
        
        # Rule 3: Many pending orders
        if features.get('pending_orders_last_10min', 0) > 5:
            reasons.append("Many pending orders for this vendor")
            is_suspicious = True
        
        # Rule 4: Unusual amount
        if abs(features.get('amount_z_score', 0)) > 2:
            reasons.append("Unusual amount compared to vendor's average")
            is_suspicious = True
        
        # Rule 5: Bulk orders
        if features.get('bulk_order_count_per_device', 0) > 3:
            reasons.append("Bulk orders from same device")
            is_suspicious = True
        
        return jsonify({
            'anomaly_score': 0.5 if is_suspicious else 0.0,
            'is_suspicious': is_suspicious,
            'reasons': reasons,
            'model_version': MODEL_VERSION,
            'fallback': 'rule-based'
        }), 200
        
    except Exception as e:
        return jsonify({'error': str(e)}), 500


if __name__ == '__main__':
    # Create models directory if it doesn't exist
    os.makedirs(os.path.join(os.path.dirname(__file__), 'models'), exist_ok=True)
    
    app.run(host='0.0.0.0', port=5000, debug=True)
