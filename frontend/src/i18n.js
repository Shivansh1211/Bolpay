import { createContext, createElement, useContext, useEffect, useState } from 'react';

const messages = {
  en: {
    language: 'Language', english: 'English', hindi: 'हिन्दी', brand: 'BolPay',
    securePayment: 'Payment', vendorBoard: 'Vendor board', logout: 'Log out', processing: 'Processing…',
    voice: 'Voice announcements', on: 'On', off: 'Off', scanned: 'Scanned', customerJustScanned: 'A customer just scanned this QR', customersToday: 'Customers today',
    payingNow: 'Paying now', paidTenMinutes: 'Paid in the last 10 minutes', activePayments: 'Active payments', completedPayments: 'Completed payments',
    quickCheck: 'Quick check', enterToken: 'Enter token or order ID', check: 'Check', notFound: 'Order not found',
    quickActions: 'Quick actions', printQr: 'Print QR card', glanceMode: 'Glance mode', exitGlance: 'Exit glance mode',
    noActivePayments: 'No active payments', noRecentPayments: 'No recent payments', recentPaid: 'Recent paid orders',
    allOrders: 'All orders', orders: 'Orders', waiting: 'Waiting', paid: 'Paid', expired: 'Expired', latePayment: 'Late payment',
    reviewFlags: 'Orders to look at', orderToReview: 'Look at this order',
    timeLeft: '{time} left', paidAt: 'Paid at {time}',
    pay: 'Pay', payVendor: 'Pay {vendor}', amount: 'Amount', enterAmount: 'Enter amount', upiId: 'UPI ID', notSet: 'Not set', payNow: 'Pay now', creatingOrder: 'Creating order…',
    paymentStatus: 'Payment status', paymentVerified: 'Verified', paymentConfirmed: 'Your payment is confirmed.',
    token: 'Token', customerToken: 'Your queue token', tellVendorToken: 'Tell the vendor this token.', orderId: 'Order ID', showOrderId: 'Show this order ID to the vendor. Do not rely on a screenshot.', orderIdUse: 'Use this Order ID if the vendor needs to look up the payment on their board. A screenshot is not proof of payment.', reference: 'Reference', verifiedAt: 'Time', done: 'Done',
    waitingForPayment: 'Waiting for payment', finishPayment: 'Complete payment in your UPI app.',
    payBefore: 'Pay before', timeRemaining: 'Time remaining', paymentWindowExpired: 'This token has expired.',
    pageUpdates: 'This page updates automatically. You can leave it open.', openUpiApp: 'Open UPI app', checkStatus: 'Check payment status',
    expiredTitle: 'Expired', askNewToken: 'Ask the vendor for a new token.', tryAgain: 'Try again',
    notConfirmedYet: 'Payment not confirmed yet.', waitOrAsk: 'Wait a moment or ask the vendor to check.',
    loading: 'Loading…', vendorNotFound: 'Vendor not found', orderNotFound: 'Order not found',
    paymentError: 'We could not create the order. Check your connection and try again.',
    checkoutUnavailable: 'The demo checkout is not configured. Contact the vendor.',
    statusError: 'We could not load this order. Check the link and try again.',
    previewTitle: 'BolPay', scanToPay: 'Scan to pay BolPay', scanHindi: 'बोलपे से भुगतान करें',
    saveAmount: 'Enter the amount you agreed with the vendor.',
    vendorLogin: 'Vendor sign in', verifyPhone: 'Verify your phone', loginIntro: 'Sign in to open your vendor board.',
    phoneNumber: 'Phone number', phonePlaceholder: '10-digit number', otpCode: 'One-time code', localOtp: 'Demo login code',
    otpSendError: 'We could not send a code. Check the phone number and try again.', otpVerifyError: 'That code could not be verified. Check it and try again.', otpRateLimit: 'Too many incorrect codes. Request a new code before trying again.', securityBadge: 'Your sign-in uses a one-time code.',
    sendOtp: 'Send code', verifyOtp: 'Verify code', newVendor: 'New vendor?', register: 'Register',
    registerTitle: 'Set up your business', registerIntro: 'Add your details to create a vendor profile.',
    businessName: 'Business name', businessNamePlaceholder: 'For example, Sharma Paratha Stall',
    upiPlaceholder: 'name@upi', paymentDestination: 'Payment destination',
    customSlug: 'Custom link ending (optional)', slugPlaceholder: 'sharma-paratha', autoSlug: 'Leave blank to create one automatically',
    description: 'Description', descriptionPlaceholder: 'Describe your business', bannerUrl: 'Banner image URL (optional)', menuItems: 'Menu items (JSON, optional)',
    menuHelp: 'Use a JSON list with a name and price for each item.', registerSend: 'Send verification code', registerVerify: 'Verify and create profile',
    registerError: 'We could not complete registration. Check your details and try again.',
    registerSlugTaken: 'That shop link is already in use. Choose a different link, then request a new code to continue.',
    registerPhoneExists: 'This phone number already has a vendor profile. Sign in to open it.',
    inviteCode: 'Invite code', inviteCodeHelp: 'Enter the invite code from the person who invited you, if required.',
    inviteCodeError: 'This registration needs a valid invite code. Check it with the person who invited you and try again.',
  },
  hi: {
    language: 'भाषा', english: 'English', hindi: 'हिन्दी', brand: 'बोलपे',
    securePayment: 'भुगतान', vendorBoard: 'विक्रेता बोर्ड', logout: 'लॉग आउट', processing: 'काम हो रहा है…',
    voice: 'आवाज़ की सूचना', on: 'चालू', off: 'बंद', scanned: 'स्कैन', customerJustScanned: 'ग्राहक ने अभी यह QR स्कैन किया', customersToday: 'आज के ग्राहक',
    payingNow: 'भुगतान जारी', paidTenMinutes: 'पिछले 10 मिनट में भुगतान', activePayments: 'जारी भुगतान', completedPayments: 'पूरे हुए भुगतान',
    quickCheck: 'टोकन जाँचें', enterToken: 'टोकन या ऑर्डर आईडी लिखें', check: 'जाँचें', notFound: 'ऑर्डर नहीं मिला',
    quickActions: 'झटपट काम', printQr: 'QR कार्ड प्रिंट करें', glanceMode: 'एक नज़र मोड', exitGlance: 'एक नज़र मोड बंद करें',
    noActivePayments: 'अभी कोई भुगतान जारी नहीं है', noRecentPayments: 'हाल में कोई भुगतान नहीं', recentPaid: 'हाल के भुगतान',
    allOrders: 'सभी ऑर्डर', orders: 'ऑर्डर', waiting: 'इंतज़ार', paid: 'भुगतान हुआ', expired: 'समय समाप्त', latePayment: 'देर से भुगतान',
    reviewFlags: 'जाँचने वाले ऑर्डर', orderToReview: 'इस ऑर्डर को देखें',
    timeLeft: '{time} बाकी', paidAt: '{time} पर भुगतान हुआ',
    pay: 'भुगतान करें', payVendor: '{vendor} को भुगतान करें', amount: 'राशि', enterAmount: 'राशि लिखें', upiId: 'UPI आईडी', notSet: 'सेट नहीं है', payNow: 'अभी भुगतान करें', creatingOrder: 'ऑर्डर बन रहा है…',
    paymentStatus: 'भुगतान की स्थिति', paymentVerified: 'पुष्टि हुई', paymentConfirmed: 'आपके भुगतान की पुष्टि हुई है।',
    token: 'टोकन', customerToken: 'आपका कतार टोकन', tellVendorToken: 'विक्रेता को यह टोकन बताएँ।', orderId: 'ऑर्डर आईडी', showOrderId: 'यह ऑर्डर आईडी विक्रेता को दिखाएँ। स्क्रीनशॉट पर भरोसा न करें।', orderIdUse: 'अगर विक्रेता बोर्ड पर भुगतान खोजना हो, तो यह ऑर्डर आईडी दें। स्क्रीनशॉट भुगतान का प्रमाण नहीं है।', reference: 'संदर्भ', verifiedAt: 'समय', done: 'हो गया',
    waitingForPayment: 'भुगतान का इंतज़ार है', finishPayment: 'अपने UPI ऐप में भुगतान पूरा करें।',
    payBefore: 'इस समय तक भुगतान करें', timeRemaining: 'बाकी समय', paymentWindowExpired: 'इस टोकन की अवधि समाप्त हो गई है।',
    pageUpdates: 'यह पेज अपने आप अपडेट होता है। इसे खुला रख सकते हैं।', openUpiApp: 'UPI ऐप खोलें', checkStatus: 'भुगतान की स्थिति देखें',
    expiredTitle: 'समय समाप्त', askNewToken: 'विक्रेता से नया टोकन माँगें।', tryAgain: 'फिर कोशिश करें',
    notConfirmedYet: 'भुगतान की पुष्टि अभी नहीं हुई है।', waitOrAsk: 'थोड़ा इंतज़ार करें या विक्रेता से जाँचने को कहें।',
    loading: 'लोड हो रहा है…', vendorNotFound: 'विक्रेता नहीं मिला', orderNotFound: 'ऑर्डर नहीं मिला',
    paymentError: 'ऑर्डर नहीं बन सका। कनेक्शन जाँचकर फिर कोशिश करें।',
    checkoutUnavailable: 'डेमो चेकआउट उपलब्ध नहीं है। विक्रेता से संपर्क करें।',
    statusError: 'यह ऑर्डर नहीं खुल सका। लिंक जाँचकर फिर कोशिश करें।',
    previewTitle: 'बोलपे', scanToPay: 'भुगतान के लिए स्कैन करें', scanHindi: 'बोलपे से भुगतान करें',
    saveAmount: 'विक्रेता से तय की गई राशि लिखें।',
    vendorLogin: 'विक्रेता साइन इन', verifyPhone: 'फ़ोन की पुष्टि करें', loginIntro: 'विक्रेता बोर्ड खोलने के लिए साइन इन करें।',
    phoneNumber: 'फ़ोन नंबर', phonePlaceholder: '10 अंकों का नंबर', otpCode: 'एक बार का कोड', localOtp: 'डेमो लॉगिन कोड',
    otpSendError: 'कोड नहीं भेज सके। फ़ोन नंबर जाँचकर फिर कोशिश करें।', otpVerifyError: 'कोड की पुष्टि नहीं हुई। जाँचकर फिर कोशिश करें।', otpRateLimit: 'कई गलत कोड डाले गए। फिर कोशिश करने से पहले नया कोड लें।', securityBadge: 'साइन इन के लिए एक बार का कोड इस्तेमाल होता है।',
    sendOtp: 'कोड भेजें', verifyOtp: 'कोड की पुष्टि करें', newVendor: 'नए विक्रेता हैं?', register: 'रजिस्टर करें',
    registerTitle: 'अपना कारोबार जोड़ें', registerIntro: 'विक्रेता प्रोफ़ाइल बनाने के लिए जानकारी भरें।',
    businessName: 'कारोबार का नाम', businessNamePlaceholder: 'उदाहरण: शर्मा पराठा स्टॉल',
    upiPlaceholder: 'नाम@upi', paymentDestination: 'भुगतान का पता',
    customSlug: 'लिंक का नाम (वैकल्पिक)', slugPlaceholder: 'sharma-paratha', autoSlug: 'खाली छोड़ें, नाम अपने आप बनेगा',
    description: 'विवरण', descriptionPlaceholder: 'अपने कारोबार के बारे में बताएँ', bannerUrl: 'बैनर चित्र का URL (वैकल्पिक)', menuItems: 'मेन्यू आइटम (JSON, वैकल्पिक)',
    menuHelp: 'हर आइटम का नाम और कीमत JSON सूची में लिखें।', registerSend: 'पुष्टि कोड भेजें', registerVerify: 'पुष्टि करें और प्रोफ़ाइल बनाएँ',
    registerError: 'रजिस्ट्रेशन पूरा नहीं हुआ। जानकारी जाँचकर फिर कोशिश करें।',
    registerSlugTaken: 'यह दुकान लिंक पहले से इस्तेमाल हो रहा है। दूसरा लिंक चुनें, फिर आगे बढ़ने के लिए नया कोड लें।',
    registerPhoneExists: 'इस फ़ोन नंबर से विक्रेता प्रोफ़ाइल पहले से है। उसे खोलने के लिए साइन इन करें।',
    inviteCode: 'आमंत्रण कोड', inviteCodeHelp: 'ज़रूरत हो तो आमंत्रित करने वाले से मिला कोड लिखें।',
    inviteCodeError: 'रजिस्ट्रेशन के लिए सही आमंत्रण कोड चाहिए। आमंत्रित करने वाले से पूछकर फिर कोशिश करें।',
  },
};

const LanguageContext = createContext(null);

export const LanguageProvider = ({ children }) => {
  const [language, setLanguageState] = useState(() => {
    if (typeof window === 'undefined') return 'en';
    return window.localStorage.getItem('bolpay_language') === 'hi' ? 'hi' : 'en';
  });

  useEffect(() => {
    document.documentElement.lang = language;
  }, [language]);

  const setLanguage = (nextLanguage) => {
    const value = nextLanguage === 'hi' ? 'hi' : 'en';
    window.localStorage.setItem('bolpay_language', value);
    document.documentElement.lang = value;
    setLanguageState(value);
  };

  const t = (key, values = {}) => {
    let value = messages[language][key] ?? messages.en[key] ?? key;
    Object.entries(values).forEach(([name, replacement]) => {
      value = value.replaceAll(`{${name}}`, String(replacement));
    });
    return value;
  };

  return createElement(LanguageContext.Provider, { value: { language, setLanguage, t } }, children);
};

export const useTranslation = () => useContext(LanguageContext) ?? {
  language: 'en',
  setLanguage: () => {},
  t: (key) => messages.en[key] ?? key,
};

export const LanguageSelect = ({ className = '' }) => {
  const { language, setLanguage, t } = useTranslation();
  return createElement('label', { className: `language-select ${className}` },
    createElement('span', { className: 'sr-only' }, t('language')),
    createElement('select', {
      'aria-label': t('language'),
      value: language,
      onChange: (event) => setLanguage(event.target.value),
    },
    createElement('option', { value: 'en' }, t('english')),
    createElement('option', { value: 'hi' }, t('hindi'))),
  );
};
