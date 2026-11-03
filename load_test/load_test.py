"""
Load Test for BolPay API
Simulates concurrent users creating orders
"""
import requests
import threading
import time
import statistics
from datetime import datetime
from concurrent.futures import ThreadPoolExecutor, as_completed
import random

BASE_URL = "http://localhost:8080/api"

class LoadTest:
    def __init__(self, concurrent_users=50, total_requests=1000):
        self.concurrent_users = concurrent_users
        self.total_requests = total_requests
        self.results = []
        self.errors = []

    def create_order(self, user_id):
        """Simulate a user creating an order"""
        start_time = time.time()
        try:
            response = requests.post(f"{BASE_URL}/orders/create", json={
                "vendorSlug": "test-vendor",
                "amount": round(random.uniform(10, 1000), 2)
            }, timeout=10)
            
            elapsed = time.time() - start_time
            
            return {
                'user_id': user_id,
                'status_code': response.status_code,
                'elapsed_time': elapsed,
                'success': response.status_code == 200
            }
        except Exception as e:
            elapsed = time.time() - start_time
            return {
                'user_id': user_id,
                'status_code': 0,
                'elapsed_time': elapsed,
                'success': False,
                'error': str(e)
            }

    def run_load_test(self):
        """Run the load test"""
        print("="*60)
        print("BOLPAY LOAD TEST")
        print("="*60)
        print(f"Concurrent Users: {self.concurrent_users}")
        print(f"Total Requests: {self.total_requests}")
        print(f"Target: {BASE_URL}")
        print(f"Started: {datetime.now().isoformat()}")
        print()
        
        start_time = time.time()
        
        with ThreadPoolExecutor(max_workers=self.concurrent_users) as executor:
            futures = []
            for i in range(self.total_requests):
                future = executor.submit(self.create_order, i)
                futures.append(future)
            
            for future in as_completed(futures):
                result = future.result()
                self.results.append(result)
                if not result['success']:
                    self.errors.append(result)
        
        total_time = time.time() - start_time
        
        # Calculate statistics
        successful = [r for r in self.results if r['success']]
        failed = [r for r in self.results if not r['success']]
        
        response_times = [r['elapsed_time'] for r in successful]
        
        print()
        print("="*60)
        print("LOAD TEST RESULTS")
        print("="*60)
        print(f"Total Requests: {len(self.results)}")
        print(f"Successful: {len(successful)}")
        print(f"Failed: {len(failed)}")
        print(f"Success Rate: {len(successful)/len(self.results)*100:.2f}%")
        print(f"Total Time: {total_time:.2f}s")
        print(f"Requests/Second: {len(self.results)/total_time:.2f}")
        
        if response_times:
            print()
            print("Response Time Statistics (seconds):")
            print(f"  Min: {min(response_times):.3f}")
            print(f"  Max: {max(response_times):.3f}")
            print(f"  Mean: {statistics.mean(response_times):.3f}")
            print(f"  Median: {statistics.median(response_times):.3f}")
            print(f"  95th Percentile: {statistics.quantiles(response_times, n=20)[18]:.3f}")
            print(f"  99th Percentile: {statistics.quantiles(response_times, n=100)[98]:.3f}")
        
        if failed:
            print()
            print(f"Failed Requests: {len(failed)}")
            for error in failed[:10]:  # Show first 10 errors
                print(f"  User {error['user_id']}: {error.get('error', 'Status ' + str(error['status_code']))}")
        
        # Save results
        import json
        with open('load_test_results.json', 'w') as f:
            json.dump({
                'concurrent_users': self.concurrent_users,
                'total_requests': self.total_requests,
                'total_time': total_time,
                'successful': len(successful),
                'failed': len(failed),
                'success_rate': len(successful)/len(self.results)*100,
                'requests_per_second': len(self.results)/total_time,
                'response_times': {
                    'min': min(response_times) if response_times else 0,
                    'max': max(response_times) if response_times else 0,
                    'mean': statistics.mean(response_times) if response_times else 0,
                    'median': statistics.median(response_times) if response_times else 0,
                },
                'errors': self.errors[:20]
            }, f, indent=2)
        
        print(f"\nResults saved to load_test_results.json")

if __name__ == "__main__":
    # Run with different load levels
    print("Running load test with 10 concurrent users, 100 requests...")
    test1 = LoadTest(concurrent_users=10, total_requests=100)
    test1.run_load_test()
    
    print("\n" + "="*60 + "\n")
    
    print("Running load test with 50 concurrent users, 500 requests...")
    test2 = LoadTest(concurrent_users=50, total_requests=500)
    test2.run_load_test()
    
    print("\n" + "="*60 + "\n")
    
    print("Running load test with 100 concurrent users, 1000 requests...")
    test3 = LoadTest(concurrent_users=100, total_requests=1000)
    test3.run_load_test()
