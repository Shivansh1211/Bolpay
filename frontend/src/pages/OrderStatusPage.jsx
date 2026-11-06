import { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { orderAPI } from '../services/api';
import { LanguageSelect, useTranslation } from '../i18n';

const OrderStatusPage = () => {
  const { orderCode } = useParams();
  const navigate = useNavigate();
  const { language, setLanguage, t } = useTranslation();
  const [order, setOrder] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const [timeLeft, setTimeLeft] = useState(0);

  useEffect(() => {
    let active = true;
    const fetchOrderStatus = async () => {
      try {
        const response = await orderAPI.getStatus(orderCode);
        if (!active) return;
        setOrder(response.data);
        if (response.data.expiresAt) {
          setTimeLeft(Math.max(0, Math.floor((new Date(response.data.expiresAt) - Date.now()) / 1000)));
        }
        setError(false);
      } catch {
        if (active) setError(true);
      } finally {
        if (active) setLoading(false);
      }
    };

    fetchOrderStatus();
    const interval = setInterval(fetchOrderStatus, 2000);
    return () => {
      active = false;
      clearInterval(interval);
    };
  }, [orderCode]);

  useEffect(() => {
    const timer = setInterval(() => setTimeLeft((value) => Math.max(0, value - 1)), 1000);
    return () => clearInterval(timer);
  }, []);

  if (loading) {
    return <div className="app-shell flex min-h-screen items-center justify-center"><p role="status">{t('loading')}</p></div>;
  }

  if (error || !order) {
    return (
      <div className="app-shell min-h-screen">
        <header className="app-header"><div className="app-header-inner"><img src="/brand/bolpay-logo.svg" alt="BolPay" className="h-11 w-auto" /><LanguageSelect language={language} setLanguage={setLanguage} /></div></header>
        <main className="customer-width py-8"><div className="panel" role="alert"><h1 className="section-heading">{t('orderNotFound')}</h1><p>{t('statusError')}</p><button className="secondary-action mt-5 w-full" onClick={() => navigate(-1)}>{t('done')}</button></div></main>
      </div>
    );
  }

  const state = order.status === 'PAID' ? 'paid'
    : order.status === 'EXPIRED' ? 'expired'
      : order.status === 'LATE_PAID' ? 'late' : 'waiting';

  return (
    <div className="app-shell min-h-screen">
      <header className="app-header">
        <div className="app-header-inner">
          <img src="/brand/bolpay-logo.svg" alt="BolPay" className="h-11 w-auto" />
          <LanguageSelect language={language} setLanguage={setLanguage} />
        </div>
      </header>
      <main className="customer-width py-8">
        <section className="panel" aria-labelledby="status-title">
          <p className="field-label">{t('paymentStatus')}</p>
          <div className={`live-state live-state--${state}`} role="status" aria-live="polite">
            <span className="live-state-icon" aria-hidden="true">{state === 'paid' ? '✓' : state === 'expired' ? '×' : state === 'late' ? '!' : '◷'}</span>
            <h1 id="status-title" className="m-0 text-inherit">{state === 'paid' ? t('paymentVerified') : state === 'expired' ? t('expiredTitle') : state === 'late' ? t('latePayment') : t('waitingForPayment')}</h1>
          </div>

          {state === 'late' && <div className="mb-5 border-l-4 border-brand px-3 py-2"><strong>{t('notConfirmedYet')}</strong><p className="mt-1">{t('waitOrAsk')}</p></div>}
          {state === 'paid' && <p className="mb-4">{t('paymentConfirmed')}</p>}

          <span className="field-label">{t('amount')}</span>
          <p className="amount-value mb-5">₹{order.amount}</p>
          {state === 'paid' ? (
            <div className="mb-5 border-2 border-status-paid p-4 text-center" aria-label={t('customerToken')}>
              <span className="field-label">{t('customerToken')}</span>
              <p className="token-value mb-2" data-testid="customer-token">{order.token}</p>
              <p className="text-small font-semibold">{t('tellVendorToken')}</p>
            </div>
          ) : (
            <><span className="field-label">{t('token')}</span><p className="token-value">{order.token}</p></>
          )}

          {state === 'paid' ? (
            <dl className="grid grid-cols-2 gap-4 border-t border-gray-300 pt-4">
              <div><dt className="field-label">{t('verifiedAt')}</dt><dd>{order.paidAt ? new Date(order.paidAt).toLocaleTimeString() : '—'}</dd></div>
              <div><dt className="field-label">{t('orderId')}</dt><dd className="break-all" data-testid="customer-order-id">{order.orderCode}</dd></div>
              <p className="col-span-2 text-small text-text-secondary">{t('orderIdUse')}</p>
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
          <button type="button" className="secondary-action mt-6 w-full" onClick={() => navigate(-1)}>{t('done')}</button>
        </section>
      </main>
    </div>
  );
};

export default OrderStatusPage;
