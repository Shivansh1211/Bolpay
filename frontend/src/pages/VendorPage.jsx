import { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { vendorAPI, orderAPI } from '../services/api';
import Button from '../components/Button';
import Card from '../components/Card';
import Input from '../components/Input';
import TokenDisplay from '../components/TokenDisplay';
import StatusBadge from '../components/StatusBadge';

const VendorPage = () => {
  const { slug } = useParams();
  const navigate = useNavigate();
  const [vendor, setVendor] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [amount, setAmount] = useState('');
  const [deviceId, setDeviceId] = useState('');
  const [order, setOrder] = useState(null);
  const [creating, setCreating] = useState(false);
  const [orderStatus, setOrderStatus] = useState(null); // Poll for status

  useEffect(() => {
    const fetchVendor = async () => {
      try {
        const response = await vendorAPI.getBySlug(slug);
        setVendor(response.data);
      } catch (err) {
        setError('Vendor not found');
      } finally {
        setLoading(false);
      }
    };

    // Generate or retrieve device ID for fingerprinting
    let storedDeviceId = localStorage.getItem('bolpay_device_id');
    if (!storedDeviceId) {
      storedDeviceId = 'dev_' + Math.random().toString(36).substring(2, 15);
      localStorage.setItem('bolpay_device_id', storedDeviceId);
    }
    setDeviceId(storedDeviceId);

    // Track scan session
    const trackScan = async () => {
      try {
        await fetch(`/api/public/scan/${slug}`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json'
          },
          body: JSON.stringify({ deviceId: storedDeviceId })
        });
      } catch (err) {
        // Silently fail scan tracking
      }
    };

    fetchVendor();
    trackScan();
  }, [slug]);

  // Poll for order status when order exists
  useEffect(() => {
    if (!order) return;

    const pollStatus = async () => {
      try {
        const response = await fetch(`/api/orders/${order.orderCode}/status`);
        const data = await response.json();
        setOrderStatus(data);
        
        // If paid, stop polling
        if (data.status === 'PAID') {
          return true;
        }
        return false;
      } catch (err) {
        console.error('Failed to poll status:', err);
        return false;
      }
    };

    // Poll every 2 seconds
    const interval = setInterval(async () => {
      const shouldStop = await pollStatus();
      if (shouldStop) {
        clearInterval(interval);
      }
    }, 2000);

    // Initial poll
    pollStatus();

    return () => clearInterval(interval);
  }, [order]);

  const handleCreateOrder = async (e) => {
    e.preventDefault();
    if (!amount || creating) return;

    setCreating(true);
    try {
      const response = await orderAPI.create({
        vendorSlug: slug,
        amount: parseFloat(amount)
      });
      const createdOrder = response.data;
      setOrder(createdOrder);
      
      // Auto-open checkout after order creation
      setTimeout(() => {
        openCheckout(createdOrder);
      }, 500);
    } catch (err) {
      console.error('Failed to create order:', err);
      alert('Failed to create order');
    } finally {
      setCreating(false);
    }
  };

  const openCheckout = (checkoutOrder = order) => {
    if (checkoutOrder?.paymentSessionId) {
      // Use public URL for checkout (must be reachable from phone)
      const publicBaseUrl = import.meta.env.VITE_PUBLIC_BASE_URL || window.location.origin;
      const mockCheckoutUrl = `${publicBaseUrl}/checkout/${checkoutOrder.paymentSessionId}`;
      window.open(mockCheckoutUrl, '_blank');
    }
  };

  if (loading) {
    return (
      <div className="min-h-screen bg-secondary flex items-center justify-center">
        <div className="text-text-primary text-h1">Loading...</div>
      </div>
    );
  }

  if (error || !vendor) {
    return (
      <div className="min-h-screen bg-secondary flex items-center justify-center">
        <div className="text-text-primary text-h1">{error || 'Vendor not found'}</div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-secondary">
      {/* Header */}
      <div className="bg-brand border-b-4 border-brand-dark">
        <div className="max-w-md mx-auto px-4 py-4 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 bg-white rounded-sm flex items-center justify-center">
              <span className="text-brand font-bold text-lg">B</span>
            </div>
            <span className="text-white font-semibold text-h3">BolPay</span>
          </div>
          <div className="text-white/80 text-small">Secure Payment</div>
        </div>
      </div>

      <div className="max-w-md mx-auto px-4 py-8">
        {!order ? (
          <>
            {/* Merchant Card */}
            <Card variant="default" padding="lg" shadow="lg" className="mb-6">
              <div className="flex items-center gap-4 mb-6">
                <div className="w-16 h-16 bg-brand rounded-sm flex items-center justify-center">
                  <span className="text-white text-2xl font-bold">{vendor.businessName?.charAt(0) || 'M'}</span>
                </div>
                <div>
                  <h1 className="text-h2 font-bold text-text-primary">{vendor.businessName}</h1>
                  <p className="text-text-secondary text-small">UPI ID: {vendor.upiId || 'Not set'}</p>
                </div>
              </div>

              <form onSubmit={handleCreateOrder}>
                <div className="mb-6">
                  <Input
                    id="amount"
                    label="Enter amount"
                    type="number"
                    min="1"
                    step="0.01"
                    required
                    value={amount}
                    onChange={(e) => setAmount(e.target.value)}
                    prefix="₹"
                    placeholder="0"
                    autoFocus
                    className="text-4xl font-bold pl-16 pr-4 py-4"
                  />
                </div>

                <Button
                  type="submit"
                  variant="primary"
                  size="lg"
                  disabled={creating}
                  loading={creating}
                  className="w-full"
                >
                  Pay now
                </Button>
              </form>
            </Card>

            {/* Quick Amount Buttons */}
            <Card variant="status" padding="md" className="mb-6">
              <p className="text-text-secondary text-small mb-3">Quick amount</p>
              <div className="grid grid-cols-4 gap-2">
                {[10, 50, 100, 500].map((val) => (
                  <button
                    key={val}
                    onClick={() => setAmount(val.toString())}
                    className="bg-white border-2 border-gray-200 hover:border-brand hover:text-brand py-3 rounded-sm font-semibold transition-all text-text-primary"
                  >
                    ₹{val}
                  </button>
                ))}
              </div>
            </Card>

            {/* Security Badge */}
            <div className="flex items-center justify-center gap-2 text-text-secondary text-small">
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
              </svg>
              <span>Secured by BolPay</span>
            </div>
          </>
        ) : (
          <>
            {/* Token Card */}
            <Card 
              variant="default" 
              padding="xl" 
              shadow="lg" 
              className={`mb-6 text-center ${
                orderStatus?.status === 'PAID' ? 'border-4 border-status-paid' : ''
              }`}
            >
              {orderStatus?.status === 'PAID' ? (
                <>
                  <div className="w-20 h-20 bg-status-paid/20 rounded-sm flex items-center justify-center mx-auto mb-4">
                    <svg className="w-12 h-12 text-status-paid" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={3} d="M5 13l4 4L19 7" />
                    </svg>
                  </div>
                  <h2 className="text-h2 font-bold text-status-paid mb-4">Payment verified</h2>
                  <TokenDisplay token={order.token} size="xl" animated={true} />
                  <p className="text-h1 font-bold text-text-primary mb-2">₹{order.amount}</p>
                  <p className="text-text-secondary text-small">Paid at {new Date(orderStatus.paidAt).toLocaleTimeString()}</p>
                  <p className="text-text-secondary text-tiny mt-2">Show this to the vendor</p>
                </>
              ) : (
                <>
                  <div className="w-16 h-16 bg-status-paying/20 rounded-sm flex items-center justify-center mx-auto mb-4">
                    <svg className="w-8 h-8 text-status-paying" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
                    </svg>
                  </div>
                  <h2 className="text-h3 text-text-secondary mb-2">Your token</h2>
                  <TokenDisplay token={order.token} size="xl" />
                  <p className="text-h1 font-bold text-text-primary mb-2">₹{order.amount}</p>
                  <div className="flex items-center justify-center gap-2 text-text-secondary mt-4">
                    <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
                    </svg>
                    <span>Expires in {Math.ceil((new Date(order.expiresAt) - new Date()) / 1000)}s</span>
                  </div>
                  <p className="text-text-secondary text-small mt-4">Waiting for payment...</p>
                </>
              )}
            </Card>

            {/* Open Checkout Button (only if not paid) */}
            {orderStatus?.status !== 'PAID' && (
              <Button
                variant="primary"
                size="lg"
                onClick={openCheckout}
                className="w-full mb-4"
              >
                Open UPI app
              </Button>
            )}

            {/* Status Check */}
            <Button
              variant="ghost"
              size="md"
              onClick={() => navigate(`/o/${order.orderCode}`)}
              className="w-full"
            >
              Check payment status
            </Button>
          </>
        )}
      </div>
    </div>
  );
};

export default VendorPage;
