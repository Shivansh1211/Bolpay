import { useEffect, useState } from 'react';
import { useAuth } from '../contexts/useAuth';
import { useNavigate } from 'react-router-dom';
import { connectWebSocket, disconnectWebSocket } from '../services/websocket';
import Button from '../components/Button';
import Card from '../components/Card';
import { LanguageSelect, useTranslation } from '../i18n';

const speakVendorAnnouncement = (message, language) => {
  if (!('speechSynthesis' in window) || !window.speechSynthesis) return;
  const locale = language === 'hi' ? 'hi-IN' : 'en-IN';
  const utterance = new SpeechSynthesisUtterance(message);
  utterance.lang = locale;
  utterance.rate = 0.94;
  utterance.pitch = 1.04;
  utterance.volume = 1;

  const voices = window.speechSynthesis.getVoices();
  const matchingVoice = voices.find((voice) => voice.lang.toLowerCase() === locale.toLowerCase())
    || voices.find((voice) => voice.lang.toLowerCase().startsWith(language === 'hi' ? 'hi' : 'en-in'));
  if (matchingVoice) utterance.voice = matchingVoice;
  window.speechSynthesis.speak(utterance);
};

// OrderRow component for color-coded order rows
const OrderRow = ({ order, type, t }) => {
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

  const status = type === 'pending' ? 'PENDING' : order.status;
  const state = status === 'PAID' ? 'paid' : status === 'EXPIRED' ? 'expired' : status === 'LATE_PAID' || status === 'FLAGGED' ? 'late' : 'waiting';
  const icon = state === 'paid' ? '✓' : state === 'expired' ? '×' : state === 'late' ? '△' : '◷';
  const label = state === 'paid' ? t('paid') : state === 'expired' ? t('expired') : state === 'late' ? t('latePayment') : t('waiting');
  const time = type === 'pending' ? t('timeLeft', { time: `${timeLeft}s` })
    : order.paidAt ? t('paidAt', { time: new Date(order.paidAt).toLocaleTimeString() })
      : order.createdAt ? new Date(order.createdAt).toLocaleTimeString() : '';

  return (
    <div className={`order-row order-row--${state}`} role="listitem" aria-label={`${t('token')} ${order.token}, ₹${order.amount}, ${label}`}>
      <span className="order-token">{order.token}</span>
      <span className="order-details"><span className="order-amount">₹{order.amount}</span><br /><span className={`order-state order-state--${state}`} aria-live="polite"><span aria-hidden="true">{icon}</span>{label}</span></span>
      <span className="order-time">{time}</span>
    </div>
  );
};

const VendorDashboard = () => {
  const { user, loading: authLoading, logout } = useAuth();
  const navigate = useNavigate();
  const [boardData, setBoardData] = useState({
    payingNow: 0,
    paidLast10Min: 0,
    waitingOrders: [],
    paidOrders: [],
    allOrders: [],
  });
  const [recentPayment, setRecentPayment] = useState(null);
  const [showPaymentAlert, setShowPaymentAlert] = useState(false);
  const [quickCheckToken, setQuickCheckToken] = useState('');
  const [quickCheckResult, setQuickCheckResult] = useState(null);
  const { language, setLanguage, t } = useTranslation();
  const [voiceEnabled, setVoiceEnabled] = useState(() => localStorage.getItem('voiceAnnouncements') === 'true');
  const [glanceMode, setGlanceMode] = useState(false);
  const [scanCount, setScanCount] = useState(0);
  const [recentScan, setRecentScan] = useState(false);

  useEffect(() => {
    if (!authLoading && !user?.id) navigate('/login', { replace: true });
  }, [authLoading, user, navigate]);

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
        if (orderData.eventType === 'FRAUD_FLAG') {
          fetchBoardData();
          return;
        }
        if (orderData.eventType === 'SCAN') {
          fetchBoardData();
          setRecentScan(true);
          window.setTimeout(() => setRecentScan(false), 8000);
          return;
        }
        const isException = orderData.status === 'LATE_PAID' || orderData.status === 'FLAGGED';
        if (isException) {
          setRecentPayment(orderData);
          setShowPaymentAlert(true);
          if ('vibrate' in navigator) {
            navigator.vibrate([200, 100, 200]);
          }
        }

        if (voiceEnabled) {
          const message = orderData.status === 'LATE_PAID'
            ? (language === 'hi'
              ? `यह भुगतान टोकन की अवधि खत्म होने के बाद आया है। कृपया सामान देने से पहले ऑर्डर जाँच लें।`
              : `This payment arrived after the token expired. Please check the order before handing over the item.`)
            : orderData.status === 'FLAGGED'
              ? (language === 'hi'
                ? `इस भुगतान की पुष्टि अभी नहीं हुई है। कृपया सामान देने से पहले ऑर्डर जाँच लें।`
                : `This payment is not confirmed yet. Please check the order before handing over the item.`)
              : orderData.status === 'PAID'
                ? (language === 'hi'
                  ? `अच्छी खबर। ${orderData.amount} रुपये मिल गए हैं। टोकन ${orderData.token} है।`
                  : `Good news. ₹${orderData.amount} has been received. The token is ${orderData.token}.`)
                : null;
          if (message) speakVendorAnnouncement(message, language);
        }
        
        // Refresh board data
        fetchBoardData();
        
        // Hide alert after 5 seconds
        setTimeout(() => {
          setShowPaymentAlert(false);
        }, 5000);
      }, user.id, localStorage.getItem('token'));
    }

    return () => {
      disconnectWebSocket();
    };
  }, [user?.id, language, voiceEnabled]);

  const toggleVoiceAnnouncements = () => {
    const nextEnabled = !voiceEnabled;
    setVoiceEnabled(nextEnabled);
    localStorage.setItem('voiceAnnouncements', String(nextEnabled));
    if (nextEnabled) {
      window.speechSynthesis.cancel();
      speakVendorAnnouncement(
        language === 'hi' ? 'भुगतान की पुष्टि होते ही मैं आपको बता दूँगा।' : `I'll let you know when a payment is confirmed.`,
        language,
      );
    }
  };

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
      setQuickCheckResult({ found: false, message: t('notFound') });
    }
  };

  const lateCount = (boardData?.allOrders || []).filter((order) => order.status === 'LATE_PAID' || order.status === 'FLAGGED').length;
  const quickCheckLabel = quickCheckResult?.status === 'PAID' ? t('paid')
    : quickCheckResult?.status === 'EXPIRED' ? t('expired')
      : quickCheckResult?.status === 'LATE_PAID' ? t('latePayment') : t('waiting');

  if (authLoading || !user?.id) {
    return <main className="app-shell min-h-screen p-6" role="status">Checking your sign-in…</main>;
  }

  return (
    <div className="app-shell">
      {/* Payment Alert Overlay */}
      {showPaymentAlert && recentPayment && (
        <div className="fixed inset-0 bg-brand flex items-center justify-center z-50" role="status" aria-live="assertive">
          <div className="text-center text-white">
            <div className="w-24 h-24 bg-white/20 rounded-sm flex items-center justify-center mx-auto mb-4">
              <svg className="w-16 h-16" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={3} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
              </svg>
            </div>
            <h1 className="text-6xl font-bold mb-2">₹{recentPayment.amount}</h1>
            <p className="text-h2 font-semibold mb-2">{t('latePayment')}</p>
            <p className="text-body opacity-80">{t('notConfirmedYet')}</p>
          </div>
        </div>
      )}

      {/* Header */}
      <header className="app-header">
        <div className="app-header-inner">
          <div className="flex items-center gap-3">
            <img src="/brand/bolpay-mark.svg" alt="" aria-hidden="true" className="h-10 w-10" />
            <div>
              <h1 className="m-0 text-base font-bold text-white">{t('brand')}</h1>
              <p className="text-white/80 text-small">{t('vendorBoard')}</p>
            </div>
          </div>
          <div className="flex items-center gap-2">
            <LanguageSelect language={language} setLanguage={setLanguage} />
            <Button variant="secondary" size="sm" onClick={toggleVoiceAnnouncements} aria-pressed={voiceEnabled}>
              Voice {voiceEnabled ? t('on') : t('off')}
            </Button>
            <Button variant="secondary" size="sm" onClick={handleLogout}>{t('logout')}</Button>
          </div>
        </div>
      </header>

      <main className="page-width py-6">
        <div className="board-counters" aria-label={t('orders')}>
          {recentScan && <p className="col-span-full text-status-paying font-semibold" role="status" aria-live="polite">{t('customerJustScanned')}</p>}
          <div className="board-counter"><span className="board-counter-label"><span aria-hidden="true">◉</span>{t('scanned')}</span><span data-testid="scan-count" className="board-counter-value">{scanCount}</span><span className="text-tiny text-text-secondary">{t('customersToday')}</span></div>
          <div className="board-counter"><span className="board-counter-label"><span aria-hidden="true">◷</span>{t('payingNow')}</span><span className="board-counter-value">{boardData?.waitingOrders?.length || 0}</span><span className="text-tiny text-text-secondary">{t('activePayments')}</span></div>
          <div className="board-counter"><span className="board-counter-label"><span aria-hidden="true">✓</span>{t('paidTenMinutes')}</span><span className="board-counter-value">{boardData?.paidOrders?.length || 0}</span><span className="text-tiny text-text-secondary">{t('completedPayments')}</span></div>
          <div className="board-counter"><span className="board-counter-label"><span aria-hidden="true">△</span>{t('latePayment')}</span><span className="board-counter-value">{lateCount}</span><span className="text-tiny text-text-secondary">{t('orders')}</span></div>
        </div>

        {(boardData?.fraudFlags || []).length > 0 && (
          <section className="mb-6 border-2 border-status-paying bg-white p-4" aria-labelledby="review-flags-heading">
            <h2 id="review-flags-heading" className="section-heading">{t('reviewFlags')}</h2>
            <ul className="space-y-3" aria-live="polite">
              {boardData.fraudFlags.map((flag) => (
                <li key={`${flag.orderCode}-${flag.createdAt}`} className="border-l-4 border-status-paying bg-amber-50 p-3">
                  <p className="font-semibold">{t('orderToReview')}: {flag.token} · {t('orderId')} {flag.orderCode} · ₹{flag.amount}</p>
                  {(flag.reasons || []).map((reason, index) => <p key={index}>{reason}</p>)}
                </li>
              ))}
            </ul>
          </section>
        )}

        {/* Quick Actions */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mb-6">
          <Card variant="default" padding="md" shadow="md">
            <h2 className="section-heading">{t('quickCheck')}</h2>
            <form onSubmit={handleQuickCheck} className="flex gap-3">
              <input
                type="text"
                aria-label={t('enterToken')}
                value={quickCheckToken}
                onChange={(e) => setQuickCheckToken(e.target.value.toUpperCase())}
                placeholder={t('enterToken')}
                className="field-input flex-1 uppercase font-bold"
                maxLength={12}
              />
              <Button
                type="submit"
                variant="primary"
                size="md"
              >
                {t('check')}
              </Button>
            </form>

            {quickCheckResult && (
              <div data-testid="quick-check-result" className={`mt-4 border-l-4 p-4 ${quickCheckResult.found ? `order-row--${quickCheckResult.status === 'PAID' ? 'paid' : quickCheckResult.status === 'EXPIRED' ? 'expired' : quickCheckResult.status === 'LATE_PAID' ? 'late' : 'waiting'}` : 'border-status-expired'}`} role="status" aria-live="polite">
                {quickCheckResult.found ? (
                  <div className="text-center">
                    <p className="token-value">{quickCheckResult.token}</p>
                    <p className="text-small">{t('orderId')}: {quickCheckResult.orderCode}</p>
                    <p className="section-heading">{quickCheckLabel}: ₹{quickCheckResult.amount}</p>
                  </div>
                ) : (
                  <p className="text-status-expired font-semibold text-center">{quickCheckResult.message}</p>
                )}
              </div>
            )}
          </Card>

          <Card variant="default" padding="md" shadow="md">
            <h2 className="section-heading">{t('quickActions')}</h2>
            <div className="grid grid-cols-2 gap-3">
              <button
                onClick={() => navigate('/vendor/qr')}
                className="flex min-h-target flex-col items-center gap-2 border border-brand bg-secondary p-4 text-brand transition-colors hover:bg-brand-light"
              >
                <svg className="w-8 h-8 text-status-paying" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v1m6 11h2m-6 0h-2v4m0-11v3m0 0h.01M12 12h4.01M16 20h4M4 12h4m12 0h.01M5 8h2a1 1 0 001-1V5a1 1 0 00-1-1H5a1 1 0 00-1 1v2a1 1 0 001 1zm12 0h2a1 1 0 001-1V5a1 1 0 00-1-1h-2a1 1 0 00-1 1v2a1 1 0 001 1zM5 20h2a1 1 0 001-1v-2a1 1 0 00-1-1H5a1 1 0 00-1 1v2a1 1 0 001 1z" />
                </svg>
                <span className="font-semibold text-text-primary text-small">{t('printQr')}</span>
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
                <span className="font-semibold text-small">{glanceMode ? t('exitGlance') : t('glanceMode')}</span>
              </button>
            </div>
          </Card>
        </div>

        {/* Orders Section */}
        {!glanceMode && (
          <>
            {/* Paying Now */}
            <Card data-testid="recent-paid-orders" variant="default" padding="md" shadow="md" className="mb-4">
              <h2 className="section-heading flex items-center gap-2">
                <svg className="w-5 h-5 text-status-paying" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
                </svg>
                {t('waiting')} ({boardData?.waitingOrders?.length || 0})
              </h2>
              {boardData?.waitingOrders?.length === 0 ? (
                <div className="text-center py-8 text-text-secondary">
                  <svg className="w-12 h-12 mx-auto mb-3 opacity-50" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
                  </svg>
                  <p className="text-body">{t('noActivePayments')}</p>
                </div>
              ) : (
                <div className="order-list" role="list">
                  {(boardData?.waitingOrders || []).map((order) => (
                    <div key={order.id} data-testid={`pending-order-${order.id}`}>
                      <OrderRow order={order} type="pending" t={t} />
                    </div>
                  ))}
                </div>
              )}
            </Card>

            {/* Recent Payments */}
            <Card variant="default" padding="md" shadow="md" className="mb-4">
              <h2 className="section-heading flex items-center gap-2">
                <svg className="w-5 h-5 text-status-paid" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
                </svg>
                {t('paidTenMinutes')} ({boardData?.paidOrders?.length || 0})
              </h2>
              {boardData?.paidOrders?.length === 0 ? (
                <div className="text-center py-8 text-text-secondary">
                  <svg className="w-12 h-12 mx-auto mb-3 opacity-50" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
                  </svg>
                  <p className="text-body">{t('noRecentPayments')}</p>
                </div>
              ) : (
                <div className="order-list" role="list">
                  {(boardData?.paidOrders || []).map((order, index) => (
                    <div key={order.id} data-testid={`paid-order-${order.id}`} className={index < 3 ? 'ring-2 ring-status-paid rounded-sm' : ''}>
                      <OrderRow order={order} type="paid" t={t} />
                    </div>
                  ))}
                </div>
              )}
            </Card>

            {/* All Orders */}
            <Card variant="default" padding="md" shadow="md">
              <h2 className="section-heading flex items-center gap-2">
                <svg className="w-5 h-5 text-accent" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2" />
                </svg>
                {t('allOrders')} ({boardData?.allOrders?.length || 0})
              </h2>
              {boardData?.allOrders?.length === 0 ? (
                <div className="text-center py-8 text-text-secondary">
                  <svg className="w-12 h-12 mx-auto mb-3 opacity-50" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2" />
                  </svg>
                  <p className="text-body">{t('noRecentPayments')}</p>
                </div>
              ) : (
                <div className="order-list" role="list">
                  {(boardData?.allOrders || []).map((order) => (
                    <div key={order.id} data-testid={`all-order-${order.id}`}>
                      <OrderRow order={order} type="all" t={t} />
                    </div>
                  ))}
                </div>
              )}
            </Card>
          </>
        )}

        {/* Glance Mode - Simplified View */}
        {glanceMode && (
          <section className="glance-board" aria-label={t('glanceMode')}>
            <h2 className="section-heading">{t('glanceMode')}</h2>
            <div className="board-counters">
              <div className="board-counter"><span className="board-counter-label">◷ {t('waiting')}</span><span className="board-counter-value">{boardData?.waitingOrders?.length || 0}</span></div>
              <div className="board-counter"><span className="board-counter-label">✓ {t('paid')}</span><span className="board-counter-value">{boardData?.paidOrders?.length || 0}</span></div>
              <div className="board-counter"><span className="board-counter-label">× {t('expired')}</span><span className="board-counter-value">{(boardData?.allOrders || []).filter((order) => order.status === 'EXPIRED').length}</span></div>
              <div className="board-counter"><span className="board-counter-label">△ {t('latePayment')}</span><span className="board-counter-value">{lateCount}</span></div>
            </div>
            <div className="order-list" role="list">
              {(boardData?.allOrders || []).map((order) => <OrderRow key={order.id} order={order} type="all" t={t} />)}
              {boardData?.allOrders?.length === 0 && <p className="py-8 text-center">{t('noRecentPayments')}</p>}
            </div>
          </section>
        )}
      </main>
    </div>
  );
};

export default VendorDashboard;
