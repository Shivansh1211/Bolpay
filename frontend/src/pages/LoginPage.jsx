import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { authAPI } from '../services/api';
import { useAuth } from '../contexts/AuthContext';
import { LanguageSelect, useTranslation } from '../i18n';

const LoginPage = () => {
  const [phoneNumber, setPhoneNumber] = useState('');
  const [otpCode, setOtpCode] = useState('');
  const [otpSent, setOtpSent] = useState(false);
  const [receivedOtp, setReceivedOtp] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const { login } = useAuth();
  const { language, setLanguage, t } = useTranslation();
  const navigate = useNavigate();

  const handleSendOtp = async (event) => {
    event.preventDefault();
    setError('');
    setLoading(true);
    try {
      const response = await authAPI.sendOtp(phoneNumber);
      setReceivedOtp(response.data.otp || '');
      setOtpSent(true);
    } catch {
      setError('otpSendError');
    } finally {
      setLoading(false);
    }
  };

  const handleVerifyOtp = async (event) => {
    event.preventDefault();
    setError('');
    setLoading(true);
    try {
      const response = await authAPI.verifyOtp(phoneNumber, otpCode);
      login(response.data);
      navigate(response.data.isNewUser ? '/register' : '/vendor/dashboard');
    } catch (requestError) {
      setError(requestError.response?.status === 429 ? 'otpRateLimit' : 'otpVerifyError');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="app-shell min-h-screen">
      <header className="app-header">
        <div className="app-header-inner">
          <div className="flex items-center gap-3"><img src="/brand/bolpay-logo.svg" alt="" aria-hidden="true" className="h-11 w-auto" /><h1 className="sr-only">BolPay</h1></div>
          <LanguageSelect language={language} setLanguage={setLanguage} />
        </div>
      </header>
      <main className="customer-width py-10">
        <section className="panel" aria-labelledby="login-title">
          <h2 id="login-title" className="section-heading">{otpSent ? t('verifyPhone') : t('vendorLogin')}</h2>
          <p className="mb-6 text-text-secondary">{t('loginIntro')}</p>
          <form className="space-y-5" onSubmit={otpSent ? handleVerifyOtp : handleSendOtp}>
            {error && <p role="alert" className="border-l-4 border-status-expired px-3 py-2">{t(error)}</p>}
            <div>
              <label htmlFor="phone" className="field-label">{t('phoneNumber')}</label>
              <div className="flex gap-2">
                <span className="inline-flex min-h-target items-center border border-gray-500 px-3">+91</span>
                <input id="phone" name="phone" type="tel" inputMode="numeric" autoComplete="tel-national" required value={phoneNumber} onChange={(event) => setPhoneNumber(event.target.value)} disabled={otpSent} className="field-input flex-1 font-semibold" placeholder={t('phonePlaceholder')} pattern="[6-9][0-9]{9}" maxLength={10} />
              </div>
            </div>
            {otpSent && (
              <>
                <div>
                  <label htmlFor="otp" className="field-label">{t('otpCode')}</label>
                  <input id="otp" name="otp" inputMode="numeric" autoComplete="one-time-code" required value={otpCode} onChange={(event) => setOtpCode(event.target.value)} className="field-input text-center text-2xl font-bold tracking-widest" placeholder="••••••" maxLength={6} />
                </div>
                {receivedOtp && <p data-testid="local-otp-demo" className="border-l-4 border-status-waiting px-3 py-2 text-small">{t('localOtp')}: {receivedOtp}</p>}
              </>
            )}
            <button type="submit" disabled={loading} className="primary-action w-full">
              {loading ? t('processing') : otpSent ? t('verifyOtp') : t('sendOtp')}
            </button>
          </form>
          {!otpSent && <p className="mt-6 text-center text-small">{t('newVendor')} <button type="button" onClick={() => navigate('/register')} className="min-h-target px-2 font-bold text-brand underline underline-offset-2">{t('register')}</button></p>}
        </section>
        <p className="mt-6 text-center text-small text-text-secondary">{t('securityBadge')}</p>
      </main>
    </div>
  );
};

export default LoginPage;
