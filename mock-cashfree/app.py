from flask import Flask, request, jsonify, render_template_string
import hmac
import hashlib
import base64
import time
import secrets
import json
import os
from functools import wraps
from datetime import datetime, timedelta

app = Flask(__name__)

# Configuration from environment
CLIENT_ID = os.environ.get("CASHFREE_CLIENT_ID", "bolpay-mock-client")
CLIENT_SECRET = os.environ.get("CASHFREE_CLIENT_SECRET", "bolpay-mock-client-secret")
WEBHOOK_SECRET = os.environ.get("CASHFREE_WEBHOOK_SECRET", "bolpay-mock-webhook-secret")
API_VERSION = "2025-01-01"
ADMIN_PASSWORD = os.environ.get("MOCK_ADMIN_PASSWORD", "")

def require_admin(view):
    @wraps(view)
    def wrapped(*args, **kwargs):
        if not ADMIN_PASSWORD:
            return "Not found", 404
        supplied = request.authorization.password if request.authorization else ""
        if not hmac.compare_digest(supplied, ADMIN_PASSWORD):
            return "Authentication required", 401, {"WWW-Authenticate": 'Basic realm="BolPay mock controls"'}
        return view(*args, **kwargs)
    return wrapped

# In-memory storage for orders
orders = {}
webhook_queue = []

# Mock checkout page HTML
CHECKOUT_HTML = """
<!DOCTYPE html>
<html>
<head>
    <title>BolPay Checkout</title>
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body { 
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            min-height: 100vh;
            display: flex;
            align-items: center;
            justify-content: center;
            padding: 20px;
        }
        .container {
            background: white;
            border-radius: 24px;
            box-shadow: 0 20px 60px rgba(0,0,0,0.3);
            max-width: 400px;
            width: 100%;
            overflow: hidden;
        }
        .header {
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            padding: 24px;
            text-align: center;
        }
        .header h1 {
            color: white;
            font-size: 24px;
            font-weight: 700;
            margin-bottom: 8px;
        }
        .header p {
            color: rgba(255,255,255,0.8);
            font-size: 14px;
        }
        .badge {
            background: rgba(255,255,255,0.2);
            color: white;
            padding: 4px 12px;
            border-radius: 20px;
            font-size: 12px;
            display: inline-block;
            margin-top: 8px;
        }
        .content { padding: 24px; }
        .payee-info {
            background: linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%);
            border-radius: 16px;
            padding: 20px;
            margin-bottom: 24px;
        }
        .payee-info p {
            margin: 8px 0;
            color: #333;
        }
        .payee-info .label {
            font-size: 12px;
            color: #666;
            text-transform: uppercase;
            letter-spacing: 0.5px;
        }
        .payee-info .value {
            font-size: 18px;
            font-weight: 600;
            color: #1a1a1a;
        }
        .amount-display {
            font-size: 48px;
            font-weight: 800;
            color: #667eea;
            text-align: center;
            margin: 24px 0;
        }
        .upi-apps {
            display: grid;
            grid-template-columns: repeat(2, 1fr);
            gap: 12px;
            margin-bottom: 24px;
        }
        .upi-app {
            background: white;
            border: 2px solid #e5e7eb;
            padding: 20px;
            text-align: center;
            border-radius: 16px;
            cursor: pointer;
            transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
        }
        .upi-app:hover {
            border-color: #667eea;
            transform: translateY(-4px);
            box-shadow: 0 8px 24px rgba(102, 126, 234, 0.2);
        }
        .upi-app:active {
            transform: scale(0.95);
        }
        .upi-app-icon {
            font-size: 40px;
            margin-bottom: 8px;
        }
        .upi-app-name {
            font-weight: 600;
            color: #333;
            font-size: 14px;
        }
        .demo-controls {
            background: #f9fafb;
            border-radius: 16px;
            padding: 16px;
            margin-top: 16px;
        }
        .demo-controls h3 {
            cursor: pointer;
            display: flex;
            justify-content: space-between;
            align-items: center;
            font-size: 14px;
            font-weight: 600;
            color: #666;
            margin-bottom: 0;
        }
        .demo-controls-content {
            display: none;
            margin-top: 16px;
        }
        .demo-controls-content.show { display: block; }
        .btn {
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
            padding: 16px 32px;
            border: none;
            border-radius: 12px;
            cursor: pointer;
            font-size: 16px;
            font-weight: 600;
            width: 100%;
            transition: all 0.3s;
            margin: 4px 0;
        }
        .btn:hover {
            transform: translateY(-2px);
            box-shadow: 0 8px 24px rgba(102, 126, 234, 0.4);
        }
        .btn:active {
            transform: scale(0.98);
        }
        .btn-fail { background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%); }
        .btn-abandon { background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%); }
        .confirm-screen { display: none; }
        .confirm-screen.show { display: block; }
        .checkout-screen { display: block; }
        .checkout-screen.hide { display: none; }
        .selected-app-display {
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
            padding: 16px;
            border-radius: 12px;
            text-align: center;
            margin-bottom: 16px;
            font-weight: 600;
        }
    </style>
</head>
<body>
    <div class="container">
        <div id="checkout-screen" class="checkout-screen">
            <div class="header">
                <h1>💳 BolPay Checkout</h1>
                <p>Secure Payment Gateway</p>
                <span class="badge">Simulated Money</span>
            </div>
            
            <div class="content">
                <div class="payee-info">
                    <p><span class="label">Payee</span><br><span class="value">Test Merchant</span></p>
                    <p><span class="label">Order Reference</span><br><span class="value">{{ order_id }}</span></p>
                    <p><span class="label">Amount</span><br><span class="amount-display" style="font-size: 32px; margin: 8px 0;">₹{{ amount }}</span></p>
                </div>
                
                <h3 style="text-align: center; color: #333; margin-bottom: 16px; font-size: 16px;">Choose Payment App</h3>
                <div class="upi-apps">
                    <div class="upi-app" onclick="openConfirm('UPI app 1')">
                        <div class="upi-app-icon">📱</div>
                        <div class="upi-app-name">UPI app 1</div>
                    </div>
                    <div class="upi-app" onclick="openConfirm('UPI app 2')">
                        <div class="upi-app-icon">�</div>
                        <div class="upi-app-name">UPI app 2</div>
                    </div>
                    <div class="upi-app" onclick="openConfirm('UPI app 3')">
                        <div class="upi-app-icon">�</div>
                        <div class="upi-app-name">UPI app 3</div>
                    </div>
                    <div class="upi-app" onclick="openConfirm('UPI app 4')">
                        <div class="upi-app-icon">🇮🇳</div>
                        <div class="upi-app-name">UPI app 4</div>
                    </div>
                </div>
                
                <div class="demo-controls">
                    <h3 onclick="toggleDemoControls()">
                        Demo Controls ▼
                    </h3>
                    <div class="demo-controls-content" id="demo-controls">
                        <button class="btn" onclick="pay('success')">✓ Pay Successfully</button>
                        <button class="btn btn-fail" onclick="pay('fail')">✗ Fail Payment</button>
                        <button class="btn btn-abandon" onclick="pay('abandon')">✕ Abandon</button>
                        <button class="btn" onclick="pay('slow')">⏱ Slow Pay (5s)</button>
                        <button class="btn btn-fail" onclick="pay('wrong_amount')">💰 Wrong Amount</button>
                        <button class="btn btn-fail" onclick="pay('forged')">🔓 Forged Webhook</button>
                        <button class="btn btn-fail" onclick="pay('duplicate')">📋 Duplicate Webhook</button>
                        <button class="btn btn-fail" onclick="pay('drop')">🚫 Drop Webhook</button>
                    </div>
                </div>
            </div>
        </div>
        
        <div id="confirm-screen" class="confirm-screen">
            <div class="header">
                <h1>Confirm Payment</h1>
                <p>Review before paying</p>
            </div>
            
            <div class="content">
                <div class="selected-app-display" id="selected-app-display">
                    Selected UPI app
                </div>
                
                <div class="payee-info">
                    <p><span class="label">Payee</span><br><span class="value">Test Merchant</span></p>
                    <p><span class="label">Amount</span><br><span class="amount-display" style="font-size: 32px; margin: 8px 0;">₹{{ amount }}</span></p>
                </div>
                
                <button class="btn" onclick="confirmPay()" style="font-size: 18px; padding: 20px;">
                    PAY NOW
                </button>
                <button class="btn btn-abandon" onclick="cancelConfirm()" style="margin-top: 12px;">
                    Cancel
                </button>
            </div>
        </div>
    </div>
    
    <script>
        let selectedApp = '';
        let scenario = 'success';
        
        function toggleDemoControls() {
            document.getElementById('demo-controls').classList.toggle('show');
        }
        
        function openConfirm(appName) {
            selectedApp = appName;
            document.getElementById('selected-app-display').textContent = appName;
            document.getElementById('checkout-screen').classList.add('hide');
            document.getElementById('checkout-screen').classList.remove('show');
            document.getElementById('confirm-screen').classList.add('show');
        }
        
        function cancelConfirm() {
            document.getElementById('confirm-screen').classList.remove('show');
            document.getElementById('checkout-screen').classList.remove('hide');
            document.getElementById('checkout-screen').classList.add('show');
        }
        
        function confirmPay() {
            sendWebhook(scenario);
        }
        
        function pay(scenarioType) {
            scenario = scenarioType;
            if (scenario === 'slow') {
                setTimeout(() => sendWebhook(scenario), 5000);
            } else if (scenario === 'abandon') {
                window.close();
            } else if (scenario === 'drop') {
                sendWebhook(scenario);
            } else {
                sendWebhook(scenario);
            }
        }
        
        function sendWebhook(scenarioType) {
            const payload = {
                order_id: '{{ order_id }}',
                cf_order_id: '{{ cf_order_id }}',
                order_status: scenarioType === 'success' || scenarioType === 'drop' ? 'PAID' : 'FAILED',
                order_amount: scenarioType === 'wrong_amount' ? {{ amount * 2 }} : {{ amount }},
                payment_time: new Date().toISOString(),
                scenario: scenarioType
            };
            
            fetch('/webhook/send', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            }).then(response => response.json()).then(data => {
                if (scenarioType === 'success') {
                    window.location.href = '{{ return_url }}';
                } else {
                    alert('Payment ' + scenarioType);
                    cancelConfirm();
                }
            });
        }
    </script>
</body>
</html>
"""

def generate_signature(timestamp, payload):
    """Generate Cashfree-style signature: Base64(HMAC_SHA256(secret, timestamp + rawBody))"""
    message = str(timestamp) + payload
    signature = hmac.new(
        WEBHOOK_SECRET.encode(),
        message.encode(),
        hashlib.sha256
    ).digest()
    return base64.b64encode(signature).decode()

@app.route('/pg/orders', methods=['POST'])
def create_order():
    """Create order endpoint"""
    headers = request.headers
    
    # Validate headers
    if headers.get('x-client-id') != CLIENT_ID:
        return jsonify({'error': 'Invalid client ID'}), 401
    if headers.get('x-client-secret') != CLIENT_SECRET:
        return jsonify({'error': 'Invalid client secret'}), 401
    if headers.get('x-api-version') != API_VERSION:
        return jsonify({'error': 'Invalid API version'}), 400
    
    data = request.json
    order_id = data.get('order_id')
    order_amount = data.get('order_amount')
    order_currency = data.get('order_currency', 'INR')
    
    cf_order_id = f"CF_{order_id}"
    payment_session_id = secrets.token_urlsafe(32)
    order_expiry_time = (datetime.utcnow() + timedelta(minutes=10)).isoformat()
    
    orders[order_id] = {
        'order_id': order_id,
        'cf_order_id': cf_order_id,
        'payment_session_id': payment_session_id,
        'order_status': 'ACTIVE',
        'order_amount': order_amount,
        'order_currency': order_currency,
        'order_expiry_time': order_expiry_time,
        'order_meta': data.get('order_meta', {}),
        'return_url': data.get('return_url', '')
    }
    
    return jsonify({
        'order_id': order_id,
        'cf_order_id': cf_order_id,
        'payment_session_id': payment_session_id,
        'order_status': 'ACTIVE',
        'order_expiry_time': order_expiry_time,
        'order_meta': data.get('order_meta', {})
    })

@app.route('/pg/orders/<order_id>', methods=['GET'])
def get_order(order_id):
    """Get order status endpoint"""
    if order_id not in orders:
        return jsonify({'error': 'Order not found'}), 404
    
    return jsonify(orders[order_id])

@app.route('/checkout/<payment_session_id>', methods=['GET'])
def checkout(payment_session_id):
    """Mock checkout page"""
    # Find order by payment_session_id
    order = None
    for oid, odata in orders.items():
        if odata.get('payment_session_id') == payment_session_id:
            order = odata
            break
    
    if not order:
        return "Order not found", 404
    
    # Cashfree's Get Order response uses order_amount. Keep that API field
    # unchanged and provide the template's display name separately.
    checkout_context = {**order, 'amount': order['order_amount']}
    return render_template_string(CHECKOUT_HTML, **checkout_context)

@app.route('/webhook/send', methods=['POST'])
def send_webhook():
    """Send webhook to BolPay backend"""
    data = request.json
    order_id = data.get('order_id')
    scenario = data.get('scenario', 'success')
    
    if order_id not in orders:
        return jsonify({'error': 'Order not found'}), 404
    
    order = orders[order_id]
    
    # Update order status
    order['order_status'] = data.get('order_status')
    if scenario == 'drop':
        return jsonify({'status': 'webhook dropped'})
    
    # Prepare webhook payload
    webhook_payload = {
        'event_id': f"evt_{int(time.time() * 1000)}",
        'order_code': order_id,
        'amount': data.get('order_amount', order['order_amount']),
        'status': data.get('order_status'),
        'gateway_ref': order['cf_order_id'],
        'timestamp': int(time.time())
    }
    
    # Generate signature (or bad signature for forged scenario)
    timestamp = int(time.time())
    payload_str = json.dumps(webhook_payload)
    
    if scenario == 'forged':
        signature = 'bad_signature'
    elif scenario == 'duplicate':
        # Use same event_id for duplicate
        webhook_payload['event_id'] = f"evt_{int(time.time() * 1000)}"
        signature = generate_signature(timestamp, payload_str)
    else:
        signature = generate_signature(timestamp, payload_str)
    
    # Send webhook to backend
    webhook_url = 'http://backend:8080/api/webhook/payment'
    headers = {
        'Content-Type': 'application/json',
        'X-Webhook-Signature': signature,
        'X-Webhook-Timestamp': str(timestamp),
        'X-Webhook-Version': '2025-01-01'
    }
    
    try:
        import requests
        response = requests.post(webhook_url, data=payload_str, headers=headers, timeout=5)
        return jsonify({'status': 'webhook sent', 'response_code': response.status_code})
    except Exception as e:
        return jsonify({'status': 'webhook failed', 'error': str(e)})

@app.route('/sdk/cashfree-mock.js', methods=['GET'])
def mock_sdk():
    """Mock Cashfree SDK shim"""
    js_code = """
    window.Cashfree = function(config) {
        this.mode = config.mode;
    };
    
    Cashfree.prototype.checkout = function(options) {
        return new Promise((resolve, reject) => {
            const { paymentSessionId, redirectTarget } = options;
            const checkoutUrl = '/checkout/' + paymentSessionId;
            
            if (redirectTarget === '_modal') {
                const modal = window.open(checkoutUrl, 'payment-modal', 'width=600,height=700');
                const checkClosed = setInterval(() => {
                    if (modal.closed) {
                        clearInterval(checkClosed);
                        resolve({ status: 'closed' });
                    }
                }, 500);
            } else if (redirectTarget === '_blank') {
                window.open(checkoutUrl, '_blank');
                resolve({ status: 'opened' });
            } else {
                window.location.href = checkoutUrl;
            }
        });
    };
    """
    return js_code, 200, {'Content-Type': 'application/javascript'}

@app.route('/mock-admin', methods=['GET'])
@require_admin
def mock_admin():
    """Mock admin panel for attack testing"""
    return render_template_string("""
    <!DOCTYPE html>
    <html>
    <head>
        <title>Mock Cashfree Admin</title>
        <style>
            body { font-family: Arial, sans-serif; max-width: 800px; margin: 50px auto; padding: 20px; }
            .badge { background: #ff9800; color: white; padding: 5px 10px; border-radius: 5px; }
            button { margin: 10px; padding: 10px 20px; cursor: pointer; }
        </style>
    </head>
    <body>
        <div class="badge">Simulated money · mock gateway</div>
        <h1>Mock Cashfree Admin Panel</h1>
        
        <h2>Attack Scenarios</h2>
        <button onclick="forgedWebhook()">Send Forged Webhook (bad signature)</button>
        <button onclick="missingHeaders()">Webhook with Missing Headers</button>
        <button onclick="oldTimestamp()">Replayed Old Timestamp</button>
        <button onclick="duplicateEvent()">Duplicate Webhook</button>
        <button onclick="wrongAmount()">Wrong Amount Webhook</button>
        <button onclick="latePayment()">Late Payment After Expiry</button>
        <button onclick="dropWebhook()">Drop Webhook (test reconciliation)</button>
        
        <h2>Active Orders</h2>
        <pre>{{ orders }}</pre>
        
        <script>
            function sendAttack(type, data) {
                fetch('/admin/attack/' + type, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(data)
                }).then(r => r.json()).then(console.log);
            }
            
            function forgedWebhook() {
                sendAttack('forged', { order_id: Object.keys({{ orders | safe }})[0] });
            }
            
            function missingHeaders() {
                sendAttack('missing_headers', { order_id: Object.keys({{ orders | safe }})[0] });
            }
            
            function oldTimestamp() {
                sendAttack('old_timestamp', { order_id: Object.keys({{ orders | safe }})[0] });
            }
            
            function duplicateEvent() {
                sendAttack('duplicate', { order_id: Object.keys({{ orders | safe }})[0] });
            }
            
            function wrongAmount() {
                sendAttack('wrong_amount', { order_id: Object.keys({{ orders | safe }})[0] });
            }
            
            function latePayment() {
                sendAttack('late_payment', { order_id: Object.keys({{ orders | safe }})[0] });
            }
            
            function dropWebhook() {
                sendAttack('drop', { order_id: Object.keys({{ orders | safe }})[0] });
            }
        </script>
    </body>
    </html>
    """, orders=json.dumps(orders, indent=2))

@app.route('/admin/attack/<attack_type>', methods=['POST'])
@require_admin
def execute_attack(attack_type):
    """Execute attack scenarios"""
    data = request.json
    order_id = data.get('order_id')
    
    if order_id not in orders:
        return jsonify({'error': 'Order not found'}), 404
    
    order = orders[order_id]
    webhook_url = 'http://backend:8080/api/webhook/payment'
    
    if attack_type == 'forged':
        # Send with bad signature
        payload = json.dumps({'order_code': order_id, 'amount': order['order_amount'], 'status': 'PAID'})
        return send_to_backend(webhook_url, payload, 'bad_signature')
    
    elif attack_type == 'missing_headers':
        # Send without signature/timestamp headers
        payload = json.dumps({'order_code': order_id, 'amount': order['order_amount'], 'status': 'PAID'})
        return send_to_backend(webhook_url, payload, None, headers={})
    
    elif attack_type == 'old_timestamp':
        # Send with old timestamp
        old_time = int(time.time()) - 600  # 10 minutes ago
        payload = json.dumps({'order_code': order_id, 'amount': order['order_amount'], 'status': 'PAID'})
        sig = generate_signature(old_time, payload)
        return send_to_backend(webhook_url, payload, sig, timestamp=str(old_time))
    
    elif attack_type == 'duplicate':
        # Send same webhook twice
        payload = json.dumps({'order_code': order_id, 'amount': order['order_amount'], 'status': 'PAID'})
        sig = generate_signature(int(time.time()), payload)
        send_to_backend(webhook_url, payload, sig)
        return send_to_backend(webhook_url, payload, sig)
    
    elif attack_type == 'wrong_amount':
        # Send with wrong amount
        payload = json.dumps({'order_code': order_id, 'amount': order['order_amount'] * 2, 'status': 'PAID'})
        sig = generate_signature(int(time.time()), payload)
        return send_to_backend(webhook_url, payload, sig)
    
    elif attack_type == 'late_payment':
        # Mark order as expired first, then send payment
        order['order_status'] = 'EXPIRED'
        payload = json.dumps({'order_code': order_id, 'amount': order['order_amount'], 'status': 'PAID'})
        sig = generate_signature(int(time.time()), payload)
        return send_to_backend(webhook_url, payload, sig)
    
    elif attack_type == 'drop':
        # Don't send webhook (test reconciliation)
        return jsonify({'status': 'webhook dropped', 'order_id': order_id})
    
    return jsonify({'status': 'attack executed'})

def send_to_backend(url, payload, signature, timestamp=None, headers=None):
    """Helper to send webhook to backend"""
    import requests
    
    if headers is None:
        headers = {
            'Content-Type': 'application/json',
            'X-Webhook-Signature': signature,
            'X-Webhook-Timestamp': str(int(time.time())),
            'X-Webhook-Version': '2025-01-01'
        }
    
    try:
        response = requests.post(url, data=payload, headers=headers)
        return jsonify({'status': 'sent', 'response_code': response.status_code, 'response': response.text})
    except Exception as e:
        return jsonify({'status': 'error', 'error': str(e)})

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=9000, debug=False)
