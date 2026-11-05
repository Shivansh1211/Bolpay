import { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { vendorAPI, orderAPI } from '../services/api';
import Button from '../components/Button';
import Input from '../components/Input';
import { LanguageSelect, useTranslation } from '../i18n';

const StateIcon = ({ state }) => {
  if (state === 'paid') return <span aria-hidden="true">✓</span>;
  if (state === 'expired') return <span aria-hidden="true">×</span>;
  if (state === 'late') return <span aria-hidden="true">!</span>;
  return <span aria-hidden="true">◷</span>;
};

const VendorPage = () => {
  const { slug } = useParams();
  const navigate = useNavigate();
  const { language, setLanguage, t } = useTranslation();
  const [vendor, setVendor] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [amount, setAmount] = useState('');
  const [order, setOrder] = useState(null);
  const [creating, setCreating] = useState(false);
  const [orderStatus, setOrderStatus] = useState(null);
  const [timeLeft, setTimeLeft] = useState(0);

  useEffect(() => {
    const fetchVendor = async () => {
      try {
        const response = await vendorAPI.getBySlug(slug);
        setVendor(response.data);
      } catch {
        setError('vendor');
      } finally {
        setLoading(false);
      }
    };

    let deviceId = localStorage.getItem('bolpay_device_id');
    if (!deviceId) {
      deviceId = `dev_${Math.random().toString(36).substring(2, 15)}`;
      localStorage.setItem('bolpay_device_id', deviceId);
    }

    const trackScan = async () => {
      try {
        await fetch(`/api/public/scan/${slug}`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ deviceId }),
        });
      } catch {
        // Scan metrics are best-effort and must not block payment.
      }
    };

    fetchVendor();
    trackScan();
  }, [slug]);

  useEffect(() => {
    if (!order) return undefined;

    const pollStatus = async () => {
      try {
        const response = await fetch(`/api/orders/${order.orderCode}/status`);
        if (!response.ok) return false;
        const data = await response.json();
        setOrderStatus(data);
        if (data.expiresAt) {
          setTimeLeft(Math.max(0, Math.floor((new Date(data.expiresAt) - Date.now()) / 1000)));
        }
        return data.status === 'PAID';
      } catch {
        return false;
      }
    };

    let interval;
    pollStatus().then((paid) => {
      if (!paid) {
        interval = setInterval(async () => {
          if (await pollStatus()) clearInterval(interval);
        }, 2000);
      }
    });
    const countdown = setInterval(() => setTimeLeft((value) => Math.max(0, value - 1)), 1000);

    return () => {
      clearInterval(interval);
      clearInterval(countdown);
    };
  }, [order]);

  const handleCreateOrder = async (event) => {
    event.preventDefault();
    if (!amount || creating) return;

    setError('');
    setCreating(true);
    try {
      const response = await orderAPI.create({ vendorSlug: slug, amount: parseFloat(amount) });
      setOrder(response.data);
      setTimeLeft(Math.max(0, Math.floor((new Date(response.data.expiresAt) - Date.now()) / 1000)));
      window.setTimeout(() => openCheckout(response.data), 500);
    } catch {
      setError('payment');
    } finally {
      setCreating(false);
    }
  };

  const openCheckout = (checkoutOrder = order) => {
    if (!checkoutOrder?.paymentSessionId) return;
    const publicBaseUrl = import.meta.env.VITE_PUBLIC_BASE_URL || window.location.origin;
    window.open(`${publicBaseUrl}/checkout/${checkoutOrder.paymentSessionId}`, '_blank', 'noopener');
  };

  if (loading) {
    return <div className="app-shell flex min-h-screen items-center justify-center"><p role="status">{t('loading')}</p></div>;
  }

  if (error === 'vendor' || !vendor) {
    return <div className="app-shell flex min-h-screen items-center justify-center"><p role="alert">{t('vendorNotFound')}</p></div>;
  }

  const state = orderStatus?.status === 'PAID' ? 'paid'
    : orderStatus?.status === 'EXPIRED' ? 'expired'
      : orderStatus?.status === 'LATE_PAID' ? 'late' : 'waiting';

  return (
    <div className="app-shell">
      <header className="app-header">
        <div className="app-header-inner">
          <div className="flex items-center gap-3">
            <img src="/brand/bolpay-logo.svg" alt="" aria-hidden="true" className="h-11 w-auto" />
            <h1 className="sr-only">BolPay</h1>
          </div>
          <div className="flex items-center gap-3">
            <span className="hidden text-small sm:inline">{t('securePayment')}</span>
            <LanguageSelect language={language} setLanguage={setLanguage} />
          </div>
        </div>
      </header>

      <main className="customer-width py-8">
        {!order ? (
          <section className="panel" aria-labelledby="payment-title">
            <p className="field-label">{t('pay')}</p>
            <h1 id="payment-title" className="section-heading">{vendor.businessName}</h1>
            <p className="mb-1 font-semibold">{vendor.businessName}</p>
            <p className="mb-6 text-small text-text-secondary">{t('upiId')}: {vendor.upiId || t('notSet')}</p>

            <form onSubmit={handleCreateOrder}>
              <div className="mb-3">
                <Input
                  id="amount"
                  label={t('enterAmount')}
                  type="number"
                  min="1"
                  step="0.01"
                  required
                  value={amount}
                  onChange={(event) => setAmount(event.target.value)}
                  prefix="₹"
                  placeholder="0"
                  inputMode="decimal"
                  autoFocus
                  className="amount-value pl-12"
                />
              </div>
              <p className="mb-5 text-small text-text-secondary">{t('saveAmount')}</p>
              {error === 'payment' && <p role="alert" className="mb-4 rounded-sm border-l-4 border-status-expired px-3 py-2">{t('paymentError')}</p>}
              <Button type="submit" variant="primary" size="lg" disabled={creating} loading={creating} className="w-full">
                {creating ? t('creatingOrder') : t('payNow')}
              </Button>
            </form>
          </section>
        ) : (
          <section className="panel" aria-labelledby="order-state-title">
            <p className="field-label">{t('paymentStatus')}</p>
            <div className={`live-state live-state--${state}`} role="status" aria-live="polite">
              <span className="live-state-icon"><StateIcon state={state} /></span>
              <h1 id="order-state-title" className="m-0 text-inherit">
                {state === 'paid' ? t('paymentVerified')
                  : state === 'expired' ? t('expiredTitle')
                    : state === 'late' ? t('latePayment') : t('waitingForPayment')}
              </h1>
            </div>

            {state === 'late' && (
              <div className="mb-5 border-l-4 border-brand px-3 py-2">
                <strong>{t('notConfirmedYet')}</strong>
                <p className="mt-1">{t('waitOrAsk')}</p>
              </div>
            )}

            <span className="field-label">{t('amount')}</span>
            <p className="amount-value mb-5">₹{order.amount}</p>
            <span className="field-label">{t('token')}</span>
            <p className="token-value">{order.token}</p>

            {state === 'paid' ? (
              <dl className="grid grid-cols-2 gap-4 border-t border-gray-300 pt-4">
                <div><dt className="field-label">{t('verifiedAt')}</dt><dd>{orderStatus?.paidAt ? new Date(orderStatus.paidAt).toLocaleTimeString() : '—'}</dd></div>
                <div><dt className="field-label">{t('reference')}</dt><dd className="break-all">{order.orderCode}</dd></div>
              </dl>
            ) : state === 'expired' ? (
              <p className="border-t border-gray-300 pt-4">{t('askNewToken')}</p>
            ) : (
              <div className="border-t border-gray-300 pt-4">
                <span className="field-label">{t('payBefore')}</span>
                <strong>{timeLeft > 0 ? `${Math.floor(timeLeft / 60)}:${String(timeLeft % 60).padStart(2, '0')}` : t('paymentWindowExpired')}</strong>
                <p className="mt-4 text-small text-text-secondary">{t('pageUpdates')}</p>
              </div>
            )}

            {state === 'waiting' && (
              <Button variant="primary" size="lg" onClick={() => openCheckout()} className="mt-6 w-full">
                {t('openUpiApp')}
              </Button>
            )}
            <button type="button" className="secondary-action mt-3 w-full" onClick={() => navigate(`/o/${order.orderCode}`)}>
              {t('checkStatus')}
            </button>
          </section>
        )}
      </main>
    </div>
  );
};

export default VendorPage;
