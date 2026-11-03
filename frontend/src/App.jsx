import { BrowserRouter as Router, Routes, Route } from 'react-router-dom'
import { AuthProvider } from './contexts/AuthContext'
import LoginPage from './pages/LoginPage'
import RegisterPage from './pages/RegisterPage'
import VendorPage from './pages/VendorPage'
import VendorDashboard from './pages/VendorDashboard'
import PaymentPage from './pages/PaymentPage'
import OrderStatusPage from './pages/OrderStatusPage'
import VendorQRPage from './pages/VendorQRPage'

function App() {
  return (
    <AuthProvider>
      <Router>
        <Routes>
          <Route path="/" element={<LoginPage />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/v/:slug" element={<VendorPage />} />
          <Route path="/o/:orderCode" element={<OrderStatusPage />} />
          <Route path="/vendor/dashboard" element={<VendorDashboard />} />
          <Route path="/vendor/qr" element={<VendorQRPage />} />
        </Routes>
      </Router>
    </AuthProvider>
  )
}

export default App
