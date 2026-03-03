import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AnimatePresence } from 'framer-motion';

import { AuthProvider } from '../context/AuthContext';
import { SessionProvider } from '../context/SessionContext';
import PublicLayout from '../layouts/PublicLayout';
import ProtectedLayout from '../layouts/ProtectedLayout';
import AtcLayout from '../layouts/AtcLayout';

import LandingPage from '../pages/LandingPage';
import LoginPage from '../pages/LoginPage';
import RegisterPage from '../pages/RegisterPage';
import HomePage from '../pages/HomePage';
import DashboardPage from '../pages/DashboardPage';
import AnalyticsPage from '../pages/AnalyticsPage';
import AtcRadarPage from '../pages/AtcRadarPage';
import AtcFlightDetailPage from '../pages/AtcFlightDetailPage';

export default function AppRouter() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <SessionProvider>
          <AnimatePresence mode="wait">
            <Routes>
              {/* ─── Public Routes ─── */}
              <Route element={<PublicLayout />}>
                <Route path="/" element={<LandingPage />} />
                <Route path="/login" element={<LoginPage />} />
                <Route path="/register" element={<RegisterPage />} />
              </Route>

              {/* ─── Pilot Protected Routes ─── */}
              <Route element={<ProtectedLayout requiredRole="PILOT" />}>
                <Route path="/home" element={<HomePage />} />
                <Route path="/dashboard/:sessionId" element={<DashboardPage />} />
                <Route path="/analytics/:sessionId" element={<AnalyticsPage />} />
              </Route>

              {/* ─── ATC Protected Routes ─── */}
              <Route element={<AtcLayout />}>
                <Route path="/atc" element={<AtcRadarPage />} />
                <Route path="/atc/flight/:sessionId" element={<AtcFlightDetailPage />} />
              </Route>

              {/* Catch-all */}
              <Route path="*" element={<Navigate to="/" replace />} />
            </Routes>
          </AnimatePresence>
        </SessionProvider>
      </AuthProvider>
    </BrowserRouter>
  );
}
