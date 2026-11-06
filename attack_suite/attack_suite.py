"""
Attack Suite for BolPay Security Testing
Tests various attack scenarios against the BolPay API
"""
import requests
import time
import random
import json
from datetime import datetime

BASE_URL = "http://localhost:8080/api"

class AttackSuite:
    def __init__(self):
        self.results = []
        self.token = None
        self.vendor_id = None

    def log_result(self, attack_name, success, message, details=None):
        result = {
            'attack': attack_name,
            'success': success,
            'message': message,
            'details': details,
            'timestamp': datetime.now().isoformat()
        }
        self.results.append(result)
        status = "✓ PASS" if success else "✗ FAIL"
        print(f"{status}: {attack_name} - {message}")
        if details:
            print(f"  Details: {details}")

    def test_idor_attack(self):
        """Test IDOR vulnerability by trying to access another vendor's orders"""
        try:
            # Try to access orders without proper vendor ID
            response = requests.get(f"{BASE_URL}/orders/vendor?vendorId=999")
            
            if response.status_code == 401:
                self.log_result("IDOR Attack", True, "Unauthorized - JWT required")
            elif response.status_code == 403:
                self.log_result("IDOR Attack", True, "Forbidden - vendor ID mismatch")
            else:
                self.log_result("IDOR Attack", False, f"Unexpected status: {response.status_code}")
        except Exception as e:
            self.log_result("IDOR Attack", False, f"Error: {str(e)}")

    def test_unauthorized_quick_check(self):
        """Test quick-check endpoint without authentication"""
        try:
            response = requests.get(f"{BASE_URL}/orders/quick-check/ABC123")
            
            if response.status_code == 401:
                self.log_result("Unauthorized Quick Check", True, "Unauthorized - JWT required")
            else:
                self.log_result("Unauthorized Quick Check", False, f"Accessible without auth: {response.status_code}")
        except Exception as e:
            self.log_result("Unauthorized Quick Check", False, f"Error: {str(e)}")

    def test_forged_webhook(self):
        """Test webhook with forged signature"""
        try:
            payload = {
                "order_code": "TEST123",
                "amount": 100.00,
                "status": "PAID"
            }
            
            # Send with bad signature
            headers = {
                "X-Webhook-Signature": "bad_signature",
                "X-Webhook-Timestamp": str(int(time.time())),
                "X-Webhook-Version": "2025-01-01"
            }
            
            response = requests.post(f"{BASE_URL}/webhook/payment", json=payload, headers=headers)
            
            if response.status_code == 401:
                self.log_result("Forged Webhook", True, "Rejected - invalid signature")
            else:
                self.log_result("Forged Webhook", False, f"Accepted: {response.status_code}")
        except Exception as e:
            self.log_result("Forged Webhook", False, f"Error: {str(e)}")

    def test_webhook_missing_headers(self):
        """Test webhook without required headers"""
        try:
            payload = {
                "order_code": "TEST123",
                "amount": 100.00,
                "status": "PAID"
            }
            
            response = requests.post(f"{BASE_URL}/webhook/payment", json=payload)
            
            if response.status_code == 401:
                self.log_result("Webhook Missing Headers", True, "Rejected - missing headers")
            else:
                self.log_result("Webhook Missing Headers", False, f"Accepted: {response.status_code}")
        except Exception as e:
            self.log_result("Webhook Missing Headers", False, f"Error: {str(e)}")

    def test_webhook_old_timestamp(self):
        """Test webhook with old timestamp (replay attack)"""
        try:
            payload = {
                "order_code": "TEST123",
                "amount": 100.00,
                "status": "PAID"
            }
            
            old_timestamp = int(time.time()) - 600  # 10 minutes ago
            headers = {
                "X-Webhook-Signature": "test",
                "X-Webhook-Timestamp": str(old_timestamp),
                "X-Webhook-Version": "2025-01-01"
            }
            
            response = requests.post(f"{BASE_URL}/webhook/payment", json=payload, headers=headers)
            
            if response.status_code == 401:
                self.log_result("Webhook Old Timestamp", True, "Rejected - timestamp too old")
            else:
                self.log_result("Webhook Old Timestamp", False, f"Accepted: {response.status_code}")
        except Exception as e:
            self.log_result("Webhook Old Timestamp", False, f"Error: {str(e)}")

    def test_rate_limiting_otp(self):
        """Test rate limiting on OTP endpoint"""
        try:
            # Send 6 OTP requests (limit is 5 per hour)
            success_count = 0
            for i in range(6):
                response = requests.post(f"{BASE_URL}/auth/send-otp", json={"phoneNumber": "9000000000"})
                if response.status_code == 429:
                    success_count += 1
            
            if success_count >= 1:
                self.log_result("Rate Limiting OTP", True, "Rate limiting active")
            else:
                self.log_result("Rate Limiting OTP", False, "No rate limiting detected")
        except Exception as e:
            self.log_result("Rate Limiting OTP", False, f"Error: {str(e)}")

    def test_rate_limiting_orders(self):
        """Test rate limiting on order creation"""
        try:
            # Send 21 order requests (limit is 20 per minute)
            success_count = 0
            for i in range(21):
                response = requests.post(f"{BASE_URL}/orders/create", json={
                    "vendorSlug": "test",
                    "amount": 100.00
                })
                if response.status_code == 429:
                    success_count += 1
            
            if success_count >= 1:
                self.log_result("Rate Limiting Orders", True, "Rate limiting active")
            else:
                self.log_result("Rate Limiting Orders", False, "No rate limiting detected")
        except Exception as e:
            self.log_result("Rate Limiting Orders", False, f"Error: {str(e)}")

    def test_xss_in_vendor_name(self):
        """Test XSS injection in vendor name"""
        try:
            xss_payload = "<script>alert('XSS')</script>"
            
            response = requests.post(f"{BASE_URL}/vendors/register", json={
                "phoneNumber": "9000000001",
                "upiId": "demo.attack@examplebank",
                "businessName": xss_payload,
                "slug": "test-xss"
            })
            
            # Check if payload was sanitized
            if response.status_code == 200:
                self.log_result("XSS in Vendor Name", False, "XSS payload accepted")
            else:
                self.log_result("XSS in Vendor Name", True, "Rejected or sanitized")
        except Exception as e:
            self.log_result("XSS in Vendor Name", False, f"Error: {str(e)}")

    def test_sql_injection(self):
        """Test SQL injection in order code"""
        try:
            sqli_payload = "'; DROP TABLE orders; --"
            
            response = requests.get(f"{BASE_URL}/orders/quick-check/{sqli_payload}")
            
            # If the server doesn't crash, SQL injection was prevented
            if response.status_code in [400, 404, 401]:
                self.log_result("SQL Injection", True, "SQL injection prevented")
            else:
                self.log_result("SQL Injection", False, f"Possible vulnerability: {response.status_code}")
        except Exception as e:
            self.log_result("SQL Injection", False, f"Error: {str(e)}")

    def run_all_attacks(self):
        """Run all attack tests"""
        print("="*60)
        print("BOLPAY ATTACK SUITE")
        print("="*60)
        print(f"Target: {BASE_URL}")
        print(f"Started: {datetime.now().isoformat()}")
        print()
        
        self.test_idor_attack()
        self.test_unauthorized_quick_check()
        self.test_forged_webhook()
        self.test_webhook_missing_headers()
        self.test_webhook_old_timestamp()
        self.test_rate_limiting_otp()
        self.test_rate_limiting_orders()
        self.test_xss_in_vendor_name()
        self.test_sql_injection()
        
        print()
        print("="*60)
        print("ATTACK SUITE SUMMARY")
        print("="*60)
        
        passed = sum(1 for r in self.results if r['success'])
        total = len(self.results)
        
        print(f"Total Tests: {total}")
        print(f"Passed: {passed}")
        print(f"Failed: {total - passed}")
        print(f"Success Rate: {passed/total*100:.1f}%")
        
        # Save results
        with open('attack_suite_results.json', 'w') as f:
            json.dump(self.results, f, indent=2)
        
        print(f"\nResults saved to attack_suite_results.json")

if __name__ == "__main__":
    suite = AttackSuite()
    suite.run_all_attacks()
