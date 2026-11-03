import { useEffect, useState } from 'react';
import { useAuth } from '../contexts/AuthContext';
import { useNavigate } from 'react-router-dom';
import QRCode from 'qrcode.react';

const VendorQRPage = () => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [vendor, setVendor] = useState(null);
  const [publicBaseUrl, setPublicBaseUrl] = useState('');

  useEffect(() => {
    if (!user) {
      navigate('/login');
      return;
    }

    // Get PUBLIC_BASE_URL from environment or default to current origin
    const baseUrl = import.meta.env.VITE_PUBLIC_BASE_URL || window.location.origin;
    setPublicBaseUrl(baseUrl);

    // Fetch vendor data
    const fetchVendor = async () => {
      try {
        const response = await fetch(`/api/vendors/${user.id}`, {
          headers: {
            'Authorization': `Bearer ${localStorage.getItem('token')}`
          }
        });
        const data = await response.json();
        setVendor(data);
      } catch (err) {
        console.error('Failed to fetch vendor:', err);
      }
    };

    fetchVendor();
  }, [user, navigate]);

  const handlePrint = () => {
    window.print();
  };

  if (!vendor) {
    return (
      <div className="min-h-screen bg-gradient-to-br from-purple-600 via-purple-700 to-indigo-800 flex items-center justify-center">
        <div className="text-white text-xl">Loading...</div>
      </div>
    );
  }

  const qrUrl = `${publicBaseUrl}/v/${vendor.slug}`;

  return (
    <div className="min-h-screen bg-gradient-to-br from-purple-600 via-purple-700 to-indigo-800 p-8">
      <div className="max-w-md mx-auto">
        {/* Header */}
        <div className="text-center mb-8">
          <div className="w-16 h-16 bg-white rounded-2xl flex items-center justify-center mx-auto mb-4 shadow-lg">
            <span className="text-purple-600 font-bold text-3xl">B</span>
          </div>
          <h1 className="text-3xl font-bold text-white mb-2">{vendor.businessName}</h1>
          <p className="text-white/80">Scan to Pay</p>
        </div>

        {/* QR Card */}
        <div className="bg-white rounded-3xl shadow-2xl p-8 mb-8 print-card">
          <div className="flex justify-center mb-6">
            <div className="p-4 bg-gradient-to-br from-purple-100 to-indigo-100 rounded-2xl">
              <QRCode value={qrUrl} size={200} />
            </div>
          </div>
          
          <div className="text-center mb-6">
            <h2 className="text-2xl font-bold text-gray-900 mb-2">{vendor.businessName}</h2>
            <p className="text-gray-600 mb-4">UPI ID: {vendor.upiId || 'Not set'}</p>
            <p className="text-xs text-gray-400 font-mono break-all">{qrUrl}</p>
          </div>

          {/* Warning Banner */}
          <div className="bg-gradient-to-r from-red-50 to-orange-50 border-2 border-red-200 rounded-xl p-4 mb-4">
            <div className="flex items-start gap-3">
              <svg className="w-6 h-6 text-red-600 flex-shrink-0 mt-0.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
              </svg>
              <div>
                <p className="font-bold text-red-700 text-sm mb-1">Security Warning</p>
                <p className="text-red-600 text-xs">Pay only via this QR. Verify green row on vendor screen before leaving.</p>
              </div>
            </div>
          </div>

          {/* Security Tips */}
          <div className="bg-gray-50 rounded-xl p-4">
            <p className="text-xs text-gray-500 text-center">
              <span className="font-semibold">Tip:</span> Wait for vendor to show green confirmation before leaving
            </p>
          </div>
        </div>

        {/* Action Buttons */}
        <div className="space-y-4 no-print">
          <button
            onClick={handlePrint}
            className="w-full bg-white text-purple-600 py-4 px-6 rounded-xl hover:bg-white/90 font-bold text-xl shadow-lg transition-all flex items-center justify-center gap-2"
          >
            <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17 17h2a2 2 0 002-2v-4a2 2 0 00-2-2H5a2 2 0 00-2 2v4a2 2 0 002 2h2m2 4h6a2 2 0 002-2v-4a2 2 0 00-2-2H9a2 2 0 00-2 2v4a2 2 0 002 2zm8-12V5a2 2 0 00-2-2H9a2 2 0 00-2 2v4h10z" />
            </svg>
            Print QR Card
          </button>
          
          <button
            onClick={() => navigate('/vendor/dashboard')}
            className="w-full bg-white/20 backdrop-blur-sm text-white py-3 px-6 rounded-xl hover:bg-white/30 font-semibold transition-all"
          >
            Back to Dashboard
          </button>
        </div>
      </div>

      <style>{`
        @media print {
          body {
            background: white !important;
          }
          .no-print {
            display: none !important;
          }
          .print-card {
            box-shadow: none !important;
            border: 2px solid #000;
          }
        }
      `}</style>
    </div>
  );
};

export default VendorQRPage;
