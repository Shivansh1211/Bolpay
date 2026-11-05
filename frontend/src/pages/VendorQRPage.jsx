import { useEffect, useState } from 'react';
import { useAuth } from '../contexts/AuthContext';
import { useNavigate } from 'react-router-dom';
import QRCode from 'qrcode.react';
import { LanguageSelect, useTranslation } from '../i18n';

const VendorQRPage = () => {
  const { user } = useAuth();
  const navigate = useNavigate();
  const { language, setLanguage, t } = useTranslation();
  const [vendor, setVendor] = useState(null);
  const [publicBaseUrl, setPublicBaseUrl] = useState('');
  const [loadFailed, setLoadFailed] = useState(false);

  useEffect(() => {
    if (!user) {
      navigate('/login');
      return;
    }

    setPublicBaseUrl(import.meta.env.VITE_PUBLIC_BASE_URL || window.location.origin);
    const fetchVendor = async () => {
      try {
        const response = await fetch(`/api/vendors/${user.id}`, {
          headers: { Authorization: `Bearer ${localStorage.getItem('token')}` },
        });
        if (!response.ok) throw new Error('Could not load vendor.');
        setVendor(await response.json());
      } catch {
        setLoadFailed(true);
      }
    };
    fetchVendor();
  }, [user, navigate]);

  if (!vendor) {
    return (
      <div className="app-shell min-h-screen">
        <header className="app-header"><div className="app-header-inner"><img src="/brand/bolpay-mark.svg" alt="" aria-hidden="true" className="brand-mark" /><span className="text-lg font-bold">{t('brand')}</span><LanguageSelect language={language} setLanguage={setLanguage} /></div></header>
        <main className="customer-width py-8">{loadFailed ? <p role="alert">{t('vendorNotFound')}</p> : <p role="status">{t('loading')}</p>}</main>
      </div>
    );
  }

  const qrUrl = `${publicBaseUrl}/v/${vendor.slug}`;

  return (
    <div className="app-shell min-h-screen">
      <header className="app-header no-print">
        <div className="app-header-inner">
          <div className="flex items-center gap-3">
            <img src="/brand/bolpay-mark.svg" alt="" aria-hidden="true" className="brand-mark" />
            <span className="text-lg font-bold">{t('brand')}</span>
          </div>
          <LanguageSelect language={language} setLanguage={setLanguage} />
        </div>
      </header>

      <main className="customer-width py-6">
        <article className="qr-print-card">
          <img src="/brand/bolpay-mark.svg" alt="BolPay" className="brand-mark mx-auto" />
          <h1 className="section-heading mt-4">{vendor.businessName}</h1>
          <p className="text-small text-text-secondary">{t('upiId')}: {vendor.upiId || t('notSet')}</p>
          <QRCode
            className="qr-print-code"
            value={qrUrl}
            size={320}
            bgColor="#FFFFFF"
            fgColor="#4A1D1F"
            level="M"
            includeMargin
            title={`${t('scanToPay')} — ${vendor.businessName}`}
          />
          <p className="text-xl font-bold">{t('scanToPay')}</p>
          <p lang="hi" className="text-xl font-bold">{t('scanHindi')}</p>
          <p className="mt-4 break-all text-small text-text-secondary">{qrUrl}</p>
          <p className="mt-6 border-t border-gray-300 pt-4 text-small">{t('waitOrAsk')}</p>
        </article>

        <div className="no-print flex flex-col gap-3 sm:flex-row">
          <button type="button" onClick={() => window.print()} className="primary-action flex-1">{t('printQr')}</button>
          <button type="button" onClick={() => navigate('/vendor/dashboard')} className="secondary-action flex-1">{t('vendorBoard')}</button>
        </div>
      </main>
    </div>
  );
};

export default VendorQRPage;
