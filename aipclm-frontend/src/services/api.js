import axios from 'axios';

const api = axios.create({
  baseURL: 'http://localhost:8080',
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
  },
});

/* ─── JWT Interceptor ─── */
api.interceptors.request.use((config) => {
  try {
    const stored = localStorage.getItem('aipclm_auth');
    if (stored) {
      const { token } = JSON.parse(stored);
      if (token) config.headers.Authorization = `Bearer ${token}`;
    }
  } catch { /* silent */ }
  return config;
});

/* ─── Auth Endpoints ─── */

export const registerUser = (data) =>
  api.post('/api/auth/register', data).then((res) => res.data);

export const loginUser = (data) =>
  api.post('/api/auth/login', data).then((res) => res.data);

export const getMe = () =>
  api.get('/api/auth/me').then((res) => res.data);

/* ─── Session Monitoring Endpoints ─── */

export const getLatestState = (sessionId) =>
  api.get(`/api/session/${sessionId}/latest-state`).then((res) => res.data);

export const getRiskHistory = (sessionId) =>
  api.get(`/api/session/${sessionId}/risk-history`).then((res) => res.data);

export const getCognitiveHistory = (sessionId) =>
  api.get(`/api/session/${sessionId}/cognitive-history`).then((res) => res.data);

export const listSessions = () =>
  api.get('/api/session/list').then((res) => res.data);

export const deleteSession = (sessionId) =>
  api.delete(`/api/session/${sessionId}`).then((res) => res.data);

export const healthCheck = () =>
  api.get('/api/session/health').then((res) => res.data);

export const purgeAllSessions = () =>
  api.delete('/api/session/purge-all').then((res) => res.data);

/* ─── Scenario Endpoints ─── */

export const createScenario = (sessionId, data) =>
  api.post(`/api/scenario/${sessionId}`, data).then((res) => res.data);

export const getScenario = (sessionId) =>
  api.get(`/api/scenario/${sessionId}`).then((res) => res.data);

export const updateScenario = (sessionId, data) =>
  api.put(`/api/scenario/${sessionId}`, data).then((res) => res.data);

/* ─── Explainability Endpoint ─── */

export const getExplainability = (sessionId) =>
  api.get(`/api/session/${sessionId}/explainability`).then((res) => res.data);

/* ─── CRM History Endpoint ─── */

export const getCrmHistory = (sessionId) =>
  api.get(`/api/session/${sessionId}/crm-history`).then((res) => res.data);

/* ─── Simulation Endpoints ─── */

export const startSession = (profileType = 'NOVICE') =>
  api.post(`/api/test/simulation/start?profileType=${profileType}`).then((res) => res.data);

/** Starts a crew-mode session with Captain + First Officer. */
export const startCrewSession = (captainProfile = 'EXPERIENCED', foProfile = 'NOVICE') =>
  api.post(`/api/test/simulation/start-crew?captainProfile=${captainProfile}&foProfile=${foProfile}`).then((res) => res.data);

export const startSchedule = (sessionId) =>
  api.post(`/api/test/simulation/${sessionId}/start-schedule`).then((res) => res.data);

export const stopSession = (sessionId) =>
  api.post(`/api/test/simulation/${sessionId}/stop-schedule`).then((res) => res.data);

export default api;
