import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { authAPI, vendorAPI } from '../services/api';
import { useAuth } from '../contexts/AuthContext';
import { LanguageSelect, useTranslation } from '../i18n';

const RegisterPage = () => {
  const [formData, setFormData] = useState({ phoneNumber: '', upiId: '', businessName: '', slug: '', description: '', bannerUrl: '', menuItems: '' });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [otpRequested, setOtpRequested] = useState(false);
  const [otpCode, setOtpCode] = useState('');
  const [receivedOtp, setReceivedOtp] = useState('');
  const [inviteCode, setInviteCode] = useState('');
  const { user, login } = useAuth();
  const { language, setLanguage, t } = useTranslation();
  const navigate = useNavigate();

  useEffect(() => {
    if (user?.phoneNumber) setFormData((previous) => ({ ...previous, phoneNumber: user.phoneNumber }));
  }, [user]);

  const handleChange = (event) => setFormData((previous) => ({ ...previous, [event.target.name]: event.target.value }));

  const handleSubmit = async (event) => {
    event.preventDefault();
    setError('');
    setLoading(true);
    try {
      if (!otpRequested) {
        const response = await authAPI.sendOtp(formData.phoneNumber);
        setReceivedOtp(response.data.otp || '');
        setOtpRequested(true);
        return;
      }
      const verified = await authAPI.verifyOtp(formData.phoneNumber, otpCode);
      const response = await vendorAPI.register({ ...formData, slug: formData.slug || null }, verified.data.token, inviteCode.trim());
      login({ token: verified.data.token, vendor: response.data });
      navigate('/vendor/dashboard');
    } catch (requestError) {
      if (requestError.response?.status === 403) setError('inviteCodeError');
      else if (requestError.response?.status === 429) setError('otpRateLimit');
      else if (requestError.response?.status === 400) setError('otpVerifyError');
      else setError('registerError');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="app-shell min-h-screen">
      <header className="app-header"><div className="app-header-inner"><div className="flex items-center gap-3"><img src="/brand/bolpay-logo.svg" alt="" aria-hidden="true" className="h-11 w-auto" /><h1 className="sr-only">BolPay</h1></div><LanguageSelect language={language} setLanguage={setLanguage} /></div></header>
      <main className="customer-width py-8">
        <section className="panel" aria-labelledby="register-title">
          <h2 id="register-title" className="section-heading">{t('registerTitle')}</h2>
          <p className="mb-6 text-text-secondary">{t('registerIntro')}</p>
          <form className="space-y-5" onSubmit={handleSubmit}>
            {error && <p role="alert" className="border-l-4 border-status-expired px-3 py-2">{t(error)}</p>}
            <div>
              <label htmlFor="phoneNumber" className="field-label">{t('phoneNumber')}</label>
              <div className="flex gap-2"><span className="inline-flex min-h-target items-center border border-gray-500 px-3">+91</span><input id="phoneNumber" name="phoneNumber" type="tel" inputMode="numeric" autoComplete="tel-national" required value={formData.phoneNumber} onChange={handleChange} className="field-input flex-1" placeholder={t('phonePlaceholder')} pattern="[6-9][0-9]{9}" maxLength={10} /></div>
            </div>
            {otpRequested && (
              <div>
                <label htmlFor="registrationOtp" className="field-label">{t('otpCode')}</label>
                <input id="registrationOtp" name="registrationOtp" inputMode="numeric" autoComplete="one-time-code" maxLength={6} required value={otpCode} onChange={(event) => setOtpCode(event.target.value)} className="field-input text-center text-2xl font-bold tracking-widest" />
                {receivedOtp && <p data-testid="local-otp-demo" className="mt-2 border-l-4 border-status-waiting px-3 py-2 text-small">{t('localOtp')}: {receivedOtp}</p>}
              </div>
            )}
            <div>
              <label htmlFor="businessName" className="field-label">{t('businessName')}</label>
              <input id="businessName" name="businessName" required value={formData.businessName} onChange={handleChange} className="field-input" placeholder={t('businessNamePlaceholder')} />
            </div>
            <div>
              <label htmlFor="upiId" className="field-label">{t('upiId')}</label>
              <input id="upiId" name="upiId" required value={formData.upiId} onChange={handleChange} className="field-input" placeholder={t('upiPlaceholder')} />
              <p className="mt-1 text-small text-text-secondary">{t('paymentDestination')}</p>
            </div>
            <div>
              <label htmlFor="inviteCode" className="field-label">{t('inviteCode')}</label>
              <input id="inviteCode" name="inviteCode" autoComplete="off" value={inviteCode} onChange={(event) => setInviteCode(event.target.value)} className="field-input" />
              <p className="mt-1 text-small text-text-secondary">{t('inviteCodeHelp')}</p>
            </div>
            <div>
              <label htmlFor="slug" className="field-label">{t('customSlug')}</label>
              <input id="slug" name="slug" value={formData.slug} onChange={handleChange} className="field-input" placeholder={t('slugPlaceholder')} />
              <p className="mt-1 text-small text-text-secondary">{t('autoSlug')}</p>
            </div>
            <div>
              <label htmlFor="description" className="field-label">{t('description')}</label>
              <textarea id="description" name="description" rows={3} value={formData.description} onChange={handleChange} className="field-input h-auto" placeholder={t('descriptionPlaceholder')} />
            </div>
            <div>
              <label htmlFor="bannerUrl" className="field-label">{t('bannerUrl')}</label>
              <input id="bannerUrl" name="bannerUrl" type="url" value={formData.bannerUrl} onChange={handleChange} className="field-input" />
            </div>
            <div>
              <label htmlFor="menuItems" className="field-label">{t('menuItems')}</label>
              <textarea id="menuItems" name="menuItems" rows={4} value={formData.menuItems} onChange={handleChange} className="field-input h-auto font-mono text-sm" placeholder="[{&quot;name&quot;:&quot;Paratha&quot;,&quot;price&quot;:30}]" />
              <p className="mt-1 text-small text-text-secondary">{t('menuHelp')}</p>
            </div>
            <button type="submit" disabled={loading} className="primary-action w-full">{loading ? t('processing') : otpRequested ? t('registerVerify') : t('registerSend')}</button>
          </form>
        </section>
        <p className="mt-6 text-center text-small text-text-secondary">{t('securityBadge')}</p>
      </main>
    </div>
  );
};

export default RegisterPage;
