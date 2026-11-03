import { useState, useEffect } from 'react';
import { useParams } from 'react-router-dom';
import { orderAPI } from '../services/api';
import Button from '../components/Button';
import Card from '../components/Card';
import TokenDisplay from '../components/TokenDisplay';
import StatusBadge from '../components/StatusBadge';

const OrderStatusPage = () => {
  const { orderCode } = useParams();
  const [order, setOrder] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [timeLeft, setTimeLeft] = useState(0);

  useEffect(() => {
    const fetchOrderStatus = async () => {
      try {
        const response = await orderAPI.getStatus(orderCode);
        setOrder(response.data);
        
        // Calculate time remaining
        if (response.data.expiresAt) {
          const expiresAt = new Date(response.data.expiresAt);
          const now = new Date();
          const diff = Math.max(0, Math.floor((expiresAt - now) / 1000));
          setTimeLeft(diff);
        }
      } catch (err) {
        setError('Order not found');
      } finally {
        setLoading(false);
      }
    };

    fetchOrderStatus();

    // Poll for status updates every 2 seconds
    const interval = setInterval(fetchOrderStatus, 2000);

    return () => clearInterval(interval);
  }, [orderCode]);

  // Countdown timer
  useEffect(() => {
    if (timeLeft > 0) {
      const timer = setInterval(() => {
        setTimeLeft((prev) => Math.max(0, prev - 1));
      }, 1000);
      return () => clearInterval(timer);
    }
  }, [timeLeft]);

  const formatTime = (seconds) => {
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${mins}:${secs.toString().padStart(2, '0')}`;
  };

  if (loading) {
    return (
      <div className="min-h-screen bg-secondary flex items-center justify-center">
        <div className="text-text-primary text-h1">Loading...</div>
      </div>
    );
  }

  if (error || !order) {
    return (
      <div className="min-h-screen bg-secondary flex items-center justify-center">
        <div className="text-text-primary text-h1">{error || 'Order not found'}</div>
      </div>
    );
  }

  const isPaid = order.status === 'PAID';
  const isExpired = order.status === 'EXPIRED';
  const isPending = order.status === 'PENDING';

  return (
    <div className="min-h-screen bg-secondary flex items-center justify-center px-4 py-8">
      <div className="max-w-md w-full">
        {/* Paid State */}
        {isPaid && (
          <Card variant="default" padding="xl" shadow="lg" className="text-center border-4 border-status-paid">
            <div className="w-24 h-24 bg-status-paid/20 rounded-sm flex items-center justify-center mx-auto mb-6">
              <svg className="w-16 h-16 text-status-paid" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={3} d="M5 13l4 4L19 7" />
              </svg>
            </div>
            <h1 className="text-h2 font-bold text-status-paid mb-2">Payment verified</h1>
            <p className="text-text-secondary mb-6">Your payment has been confirmed</p>
            <div className="bg-status-paid/10 rounded-sm p-6 mb-6">
              <p className="text-5xl font-bold text-text-primary mb-2">₹{order.amount}</p>
              <p className="text-small text-text-secondary">Token: {order.token}</p>
              <p className="text-tiny text-text-secondary">Order: {order.orderCode}</p>
            </div>
            <div className="text-small text-text-secondary">
              Verified at {order.paidAt ? new Date(order.paidAt).toLocaleTimeString() : 'N/A'}
            </div>
            <div className="mt-6">
              <Button variant="ghost" size="md" onClick={() => window.history.back()} className="w-full">
                Done
              </Button>
            </div>
          </Card>
        )}

        {/* Expired State */}
        {isExpired && (
          <Card variant="default" padding="xl" shadow="lg" className="text-center">
            <div className="w-24 h-24 bg-status-expired/20 rounded-sm flex items-center justify-center mx-auto mb-6">
              <svg className="w-16 h-16 text-status-expired" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={3} d="M6 18L18 6M6 6l12 12" />
              </svg>
            </div>
            <h1 className="text-h2 font-bold text-status-expired mb-2">This order timed out</h1>
            <p className="text-text-secondary mb-6">Start again to get a new token</p>
            <div className="bg-status-expired/10 rounded-sm p-6 mb-6">
              <p className="text-5xl font-bold text-text-primary mb-2">₹{order.amount}</p>
              <p className="text-small text-text-secondary">Token: {order.token} (expired)</p>
              <p className="text-tiny text-text-secondary">Order: {order.orderCode}</p>
            </div>
            <Button
              variant="danger"
              size="lg"
              onClick={() => window.history.back()}
              className="w-full"
            >
              Try again
            </Button>
          </Card>
        )}

        {/* Pending State */}
        {isPending && (
          <Card variant="default" padding="xl" shadow="lg" className="text-center">
            <div className="w-24 h-24 bg-status-paying/20 rounded-sm flex items-center justify-center mx-auto mb-6 animate-pulse-slow">
              <svg className="w-16 h-16 text-status-paying" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
            </div>
            <h1 className="text-h2 font-bold text-text-primary mb-2">Waiting for payment</h1>
            <p className="text-text-secondary mb-6">Complete payment in your UPI app</p>
            <div className="bg-status-paying/10 rounded-sm p-6 mb-6">
              <p className="text-5xl font-bold text-text-primary mb-2">₹{order.amount}</p>
              <p className="text-small text-text-secondary">Token: {order.token}</p>
              <p className="text-tiny text-text-secondary">Order: {order.orderCode}</p>
            </div>
            
            {/* Countdown Timer */}
            {timeLeft > 0 ? (
              <div className="mb-6">
                <p className="text-small text-text-secondary mb-2">Time remaining</p>
                <div className="text-5xl font-bold text-status-paying">
                  {formatTime(timeLeft)}
                </div>
              </div>
            ) : (
              <div className="mb-6">
                <p className="text-small text-status-expired font-semibold">Payment window expired</p>
              </div>
            )}

            <p className="text-text-secondary text-small">
              This page updates automatically. Don't close it.
            </p>
          </Card>
        )}

        {/* Demo: Simulate Payment Button (only for pending orders) */}
        {isPending && (
          <div className="mt-6 text-center">
            <Button
              variant="ghost"
              size="md"
              onClick={async () => {
                try {
                  await fetch('http://localhost:8080/api/webhook/simulate-payment', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ order_code: orderCode }),
                  });
                  const response = await orderAPI.getStatus(orderCode);
                  setOrder(response.data);
                } catch (err) {
                  console.error('Failed to simulate payment:', err);
                }
              }}
              className="w-full"
            >
              Demo: Simulate payment
            </Button>
          </div>
        )}
      </div>
    </div>
  );
};

export default OrderStatusPage;
