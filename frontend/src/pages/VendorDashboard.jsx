import { useEffect, useState } from 'react';
import { useAuth } from '../contexts/AuthContext';
import { useNavigate } from 'react-router-dom';
import { connectWebSocket, disconnectWebSocket } from '../services/websocket';
import { orderAPI } from '../services/api';
import Button from '../components/Button';
import Card from '../components/Card';
import StatusBadge from '../components/StatusBadge';

// OrderRow component for color-coded order rows
const OrderRow = ({ order, type }) => {
  const [timeLeft, setTimeLeft] = useState(0);

  useEffect(() => {
    if (type === 'pending' && order.expiresAt) {
      const updateTimeLeft = () => {
        const now = new Date();
        const expires = new Date(order.expiresAt);
        const diff = Math.max(0, Math.floor((expires - now) / 1000));
        setTimeLeft(diff);
      };

      updateTimeLeft();
      const interval = setInterval(updateTimeLeft, 1000);
      return () => clearInterval(interval);
    }
  }, [order.expiresAt, type]);

  const getRowStyle = () => {
    if (type === 'pending') return 'bg-status-paying/10 border-status-paying';
    if (order.status === 'PAID') return 'bg-status-paid/10 border-status-paid';
    if (order.status === 'EXPIRED') return 'bg-status-expired/10 border-status-expired';
    if (order.status === 'LATE_PAID') return 'bg-status-late/10 border-status-late';
    return 'bg-gray-50 border-gray-200';
  };

  return (
    <div className={`p-4 rounded-sm border-2 shadow-sm ${getRowStyle()} transition-all hover:shadow-md`}>
      <div className="flex justify-between items-center">
        <div className="flex-1">
          <div className="flex items-center gap-3 mb-2">
            <span className="text-h2 font-bold text-text-primary tracking-widest">{order.token}</span>
            <span className="text-h3 font-bold text-text-secondary">•</span>
            <span className="text-h3 font-bold text-text-primary">₹{order.amount}</span>
          </div>
          {type === 'pending' && (
            <div className="flex items-center gap-2 text-status-paying font-semibold text-small">
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
              <span>{timeLeft}s remaining</span>
            </div>
          )}
          {order.paidAt && (
            <div className="flex items-center gap-2 text-text-secondary text-small">
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
              </svg>
              <span>{new Date(order.paidAt).toLocaleTimeString()}</span>
            </div>
          )}
        </div>
        <StatusBadge status={order.status === 'LATE_PAID' ? 'LATE_PAID' : (type === 'pending' ? 'PENDING' : order.status)} />
      </div>
    </div>
  );
};

const VendorDashboard = () => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [boardData, setBoardData] = useState(null);
  const [recentPayment, setRecentPayment] = useState(null);
  const [showPaymentAlert, setShowPaymentAlert] = useState(false);
  const [quickCheckToken, setQuickCheckToken] = useState('');
  const [quickCheckResult, setQuickCheckResult] = useState(null);
  const [language, setLanguage] = useState('en'); // 'en' or 'hi'
  const [glanceMode, setGlanceMode] = useState(false);
  const [scanCount, setScanCount] = useState(0);

  useEffect(() => {
    // Fetch board data
    const fetchBoardData = async () => {
      if (user?.id) {
        try {
          const response = await fetch(`/api/vendor/board`, {
            headers: {
              'Authorization': `Bearer ${localStorage.getItem('token')}`
            }
          });
          const data = await response.json();
          setBoardData(data);
          setScanCount(data.scanned || 0);
        } catch (err) {
          console.error('Failed to fetch board data:', err);
        }
      }
    };

    fetchBoardData();

    // Connect to WebSocket for real-time updates (quiet by default)
    if (user?.id) {
      connectWebSocket((orderData) => {
        console.log('Payment received:', orderData);
        
        // Only show alert for exceptions (late payment, etc.)
        if (orderData.status === 'LATE_PAID' || orderData.status === 'FLAGGED') {
          setRecentPayment(orderData);
          setShowPaymentAlert(true);
          
          // Play sound only for exceptions
          if ('speechSynthesis' in window) {
            const utterance = new SpeechSynthesisUtterance(
              language === 'hi' 
                ? `असामान्य भुगतान: ${orderData.amount} रुपये`
                : `Unusual payment: ${orderData.amount} rupees`
            );
            utterance.lang = language === 'hi' ? 'hi-IN' : 'en-IN';
            window.speechSynthesis.speak(utterance);
          }
          
          // Vibrate for exceptions
          if ('vibrate' in navigator) {
            navigator.vibrate([200, 100, 200]);
          }
        }
        
        // Refresh board data
        fetchBoardData();
        
        // Hide alert after 5 seconds
        setTimeout(() => {
          setShowPaymentAlert(false);
        }, 5000);
      }, user.id);
    }

    return () => {
      disconnectWebSocket();
    };
  }, [user?.id]);

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  const handleQuickCheck = async (e) => {
    e.preventDefault();
    if (!quickCheckToken) return;

    try {
      const response = await fetch(`/api/vendor/orders/check/${quickCheckToken}`, {
        headers: {
          'Authorization': `Bearer ${localStorage.getItem('token')}`
        }
      });
      const data = await response.json();
      setQuickCheckResult(data);
    } catch (err) {
      setQuickCheckResult({ found: false, message: 'Order not found' });
    }
  };

  return (
    <div className="min-h-screen bg-secondary">
      {/* Payment Alert Overlay */}
      {showPaymentAlert && recentPayment && (
        <div className="fixed inset-0 bg-brand flex items-center justify-center z-50 animate-pulse-slow">
          <div className="text-center text-white">
            <div className="w-24 h-24 bg-white/20 rounded-sm flex items-center justify-center mx-auto mb-4">
              <svg className="w-16 h-16" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={3} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
              </svg>
            </div>
            <h1 className="text-6xl font-bold mb-2">₹{recentPayment.amount}</h1>
            <p className="text-h2 font-semibold mb-2">Unusual payment detected</p>
            <p className="text-body opacity-80">Order: {recentPayment.orderCode}</p>
          </div>
        </div>
      )}

      {/* Header */}
      <div className="bg-brand border-b-4 border-brand-dark shadow-md">
        <div className="max-w-7xl mx-auto px-4 py-4 flex justify-between items-center">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 bg-white rounded-sm flex items-center justify-center">
              <span className="text-brand font-bold text-lg">B</span>
            </div>
            <div>
              <h1 className="text-h2 font-bold text-white">BolPay</h1>
              <p className="text-white/80 text-small">Vendor board</p>
            </div>
          </div>
          <Button
            variant="secondary"
            size="sm"
            onClick={handleLogout}
          >
            Logout
          </Button>
        </div>
      </div>

      <div className="max-w-7xl mx-auto px-4 py-6">
        {/* Stats Cards */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-6">
          <Card variant="brand" padding="md" shadow="md" className="text-white">
            <div className="flex items-center justify-between mb-3">
              <div className="w-10 h-10 bg-white/20 rounded-sm flex items-center justify-center">
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
                </svg>
              </div>
              <span className="text-white/80 text-small">Scanned</span>
            </div>
            <p className="text-5xl font-bold mb-1">{scanCount}</p>
            <p className="text-white/80 text-tiny">Customers today</p>
          </Card>

          <Card variant="status" padding="md" shadow="md" className="border-l-4 border-status-paying">
            <div className="flex items-center justify-between mb-3">
              <div className="w-10 h-10 bg-status-paying/20 rounded-sm flex items-center justify-center">
                <svg className="w-5 h-5 text-status-paying" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
                </svg>
              </div>
              <span className="text-text-secondary text-small">Paying now</span>
            </div>
            <p className="text-5xl font-bold mb-1 text-status-paying">{boardData?.payingNow || 0}</p>
            <p className="text-text-secondary text-tiny">Active payments</p>
          </Card>

          <Card variant="status" padding="md" shadow="md" className="border-l-4 border-status-paid">
            <div className="flex items-center justify-between mb-3">
              <div className="w-10 h-10 bg-status-paid/20 rounded-sm flex items-center justify-center">
                <svg className="w-5 h-5 text-status-paid" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
                </svg>
              </div>
              <span className="text-text-secondary text-small">Paid (10 min)</span>
            </div>
            <p className="text-5xl font-bold mb-1 text-status-paid">{boardData?.paidLast10Min || 0}</p>
            <p className="text-text-secondary text-tiny">Completed payments</p>
          </Card>
        </div>

        {/* Quick Actions */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mb-6">
          <Card variant="default" padding="md" shadow="md">
            <h2 className="text-h3 font-bold text-text-primary mb-4">Quick check</h2>
            <form onSubmit={handleQuickCheck} className="flex gap-3">
              <input
                type="text"
                value={quickCheckToken}
                onChange={(e) => setQuickCheckToken(e.target.value.toUpperCase())}
                placeholder="Enter token (e.g., K72)"
                className="flex-1 px-4 py-3 border-2 border-gray-200 rounded-sm focus:outline-none focus:ring-2 focus:ring-brand focus:border-transparent uppercase font-bold text-lg"
                maxLength={4}
              />
              <Button
                type="submit"
                variant="primary"
                size="md"
              >
                Check
              </Button>
            </form>

            {quickCheckResult && (
              <div className={`mt-4 p-4 rounded-sm border-2 ${
                quickCheckResult.found
                  ? quickCheckResult.status === 'PAID'
                    ? 'bg-status-paid/10 border-status-paid'
                    : quickCheckResult.status === 'EXPIRED'
                    ? 'bg-status-expired/10 border-status-expired'
                    : 'bg-status-paying/10 border-status-paying'
                  : 'bg-gray-50 border-gray-200'
              }`}>
                {quickCheckResult.found ? (
                  <div className="text-center">
                    <p className="text-5xl font-bold mb-2 tracking-widest">
                      <span className={
                        quickCheckResult.status === 'PAID' ? 'text-status-paid' :
                        quickCheckResult.status === 'EXPIRED' ? 'text-status-expired' :
                        'text-status-paying'
                      }>
                        {quickCheckResult.token}
                      </span>
                    </p>
                    <p className="text-h2 font-bold mb-1">
                      <span className={
                        quickCheckResult.status === 'PAID' ? 'text-status-paid' :
                        quickCheckResult.status === 'EXPIRED' ? 'text-status-expired' :
                        'text-status-paying'
                      }>
                        {quickCheckResult.status}
                      </span>
                    </p>
                    <p className="text-h3 text-text-primary">₹{quickCheckResult.amount}</p>
                  </div>
                ) : (
                  <p className="text-status-expired font-semibold text-center">{quickCheckResult.message}</p>
                )}
              </div>
            )}
          </Card>

          <Card variant="default" padding="md" shadow="md">
            <h2 className="text-h3 font-bold text-text-primary mb-4">Quick actions</h2>
            <div className="grid grid-cols-2 gap-3">
              <button
                onClick={() => navigate('/vendor/qr')}
                className="flex flex-col items-center gap-2 p-4 bg-status-paying/10 border-2 border-status-paying rounded-sm hover:bg-status-paying/20 transition-all"
              >
                <svg className="w-8 h-8 text-status-paying" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v1m6 11h2m-6 0h-2v4m0-11v3m0 0h.01M12 12h4.01M16 20h4M4 12h4m12 0h.01M5 8h2a1 1 0 001-1V5a1 1 0 00-1-1H5a1 1 0 00-1 1v2a1 1 0 001 1zm12 0h2a1 1 0 001-1V5a1 1 0 00-1-1h-2a1 1 0 00-1 1v2a1 1 0 001 1zM5 20h2a1 1 0 001-1v-2a1 1 0 00-1-1H5a1 1 0 00-1 1v2a1 1 0 001 1z" />
                </svg>
                <span className="font-semibold text-text-primary text-small">Print QR card</span>
              </button>
              <button
                onClick={() => setGlanceMode(!glanceMode)}
                className={`flex flex-col items-center gap-2 p-4 border-2 rounded-sm transition-all ${
                  glanceMode 
                    ? 'bg-brand text-white border-brand' 
                    : 'bg-gray-50 border-gray-200 hover:bg-gray-100'
                }`}
              >
                <svg className="w-8 h-8" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
                </svg>
                <span className="font-semibold text-small">{glanceMode ? 'Exit glance' : 'Glance mode'}</span>
              </button>
            </div>
          </Card>
        </div>

        {/* Orders Section */}
        {!glanceMode && (
          <>
            {/* Paying Now */}
            <Card variant="default" padding="md" shadow="md" className="mb-4">
              <h2 className="text-h3 font-bold text-text-primary mb-4 flex items-center gap-2">
                <svg className="w-5 h-5 text-status-paying" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
                </svg>
                Paying now ({boardData?.waitingOrders?.length || 0})
              </h2>
              {boardData?.waitingOrders?.length === 0 ? (
                <div className="text-center py-8 text-text-secondary">
                  <svg className="w-12 h-12 mx-auto mb-3 opacity-50" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
                  </svg>
                  <p className="text-body">No active payments</p>
                </div>
              ) : (
                <div className="space-y-3">
                  {boardData.waitingOrders.map((order) => (
                    <OrderRow key={order.id} order={order} type="pending" />
                  ))}
                </div>
              )}
            </Card>

            {/* Recent Payments */}
            <Card variant="default" padding="md" shadow="md" className="mb-4">
              <h2 className="text-h3 font-bold text-text-primary mb-4 flex items-center gap-2">
                <svg className="w-5 h-5 text-status-paid" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
                </svg>
                Paid (10 min) ({boardData?.paidOrders?.length || 0})
              </h2>
              {boardData?.paidOrders?.length === 0 ? (
                <div className="text-center py-8 text-text-secondary">
                  <svg className="w-12 h-12 mx-auto mb-3 opacity-50" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
                  </svg>
                  <p className="text-body">No recent payments</p>
                </div>
              ) : (
                <div className="space-y-3">
                  {boardData.paidOrders.map((order, index) => (
                    <div key={order.id} className={index < 3 ? 'ring-2 ring-status-paid rounded-sm' : ''}>
                      <OrderRow order={order} type="paid" />
                    </div>
                  ))}
                </div>
              )}
            </Card>

            {/* All Orders */}
            <Card variant="default" padding="md" shadow="md">
              <h2 className="text-h3 font-bold text-text-primary mb-4 flex items-center gap-2">
                <svg className="w-5 h-5 text-accent" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2" />
                </svg>
                All orders ({boardData?.allOrders?.length || 0})
              </h2>
              {boardData?.allOrders?.length === 0 ? (
                <div className="text-center py-8 text-text-secondary">
                  <svg className="w-12 h-12 mx-auto mb-3 opacity-50" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2" />
                  </svg>
                  <p className="text-body">No orders yet</p>
                </div>
              ) : (
                <div className="space-y-3">
                  {boardData.allOrders.map((order) => (
                    <OrderRow key={order.id} order={order} type="all" />
                  ))}
                </div>
              )}
            </Card>
          </>
        )}

        {/* Glance Mode - Simplified View */}
        {glanceMode && (
          <div className="bg-bg-dark text-white rounded-sm shadow-xl p-8 min-h-screen">
            <h1 className="text-h2 font-bold mb-6 text-center opacity-80">Glance mode</h1>
            <div className="grid grid-cols-2 gap-6 mb-8">
              <div className="text-center">
                <p className="text-6xl font-bold text-status-paying">{boardData?.payingNow || 0}</p>
                <p className="text-h3 opacity-80">Paying now</p>
              </div>
              <div className="text-center">
                <p className="text-6xl font-bold text-status-paid">{boardData?.paidLast10Min || 0}</p>
                <p className="text-h3 opacity-80">Paid (10 min)</p>
              </div>
            </div>
            <div className="space-y-4">
              <h2 className="text-h3 font-bold mb-4 opacity-80">Recent paid</h2>
              {boardData?.paidOrders?.slice(0, 3).map((order) => (
                <div key={order.id} className="flex justify-between items-center text-5xl py-3 border-b border-white/10">
                  <span className="text-status-paid font-bold tracking-widest">{order.token}</span>
                  <span className="opacity-80">₹{order.amount}</span>
                </div>
              ))}
              {(!boardData?.paidOrders || boardData.paidOrders.length === 0) && (
                <p className="text-center text-h3 opacity-50 py-8">No recent payments</p>
              )}
            </div>
            <div className="mt-8 text-center">
              <p className="text-tiny opacity-50">Tap anywhere to exit</p>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default VendorDashboard;
