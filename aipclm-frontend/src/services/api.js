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

/** Starts a sensor-enabled session. */
export const startSensorSession = (profileType = 'EXPERIENCED') =>
  api.post(`/api/test/simulation/start-sensor?profileType=${profileType}`).then((res) => res.data);

export const startSchedule = (sessionId) =>
  api.post(`/api/test/simulation/${sessionId}/start-schedule`).then((res) => res.data);

export const stopSession = (sessionId) =>
  api.post(`/api/test/simulation/${sessionId}/stop-schedule`).then((res) => res.data);

/* ─── Sensor Endpoints ─── */

/** Quick-register all 6 preset sensor devices. */
export const quickRegisterSensors = () =>
  api.post('/api/sensor/quick-register').then((res) => res.data);

/** List all registered sensor devices. */
export const listSensorDevices = () =>
  api.get('/api/sensor/device/list').then((res) => res.data);

/** Connect a sensor device to a session. */
export const connectSensorDevice = (deviceId, sessionId) =>
  api.put(`/api/sensor/device/${deviceId}/connect/${sessionId}`).then((res) => res.data);

/** Disconnect a sensor device. */
export const disconnectSensorDevice = (deviceId) =>
  api.put(`/api/sensor/device/${deviceId}/disconnect`).then((res) => res.data);

/** Get sensor status for a session. */
export const getSensorStatus = (sessionId) =>
  api.get(`/api/sensor/session/${sessionId}/status`).then((res) => res.data);

/** Get latest sensor values for a session. */
export const getLatestSensorValues = (sessionId) =>
  api.get(`/api/sensor/session/${sessionId}/latest-values`).then((res) => res.data);

/** Ingest a single sensor reading. */
export const ingestSensorReading = (deviceId, rawValue, unit, signalQuality = 1.0) =>
  api.post('/api/sensor/reading', { deviceId, rawValue, unit, signalQuality }).then((res) => res.data);

/** Ingest batch sensor readings. */
export const ingestSensorBatch = (deviceId, readings) =>
  api.post('/api/sensor/reading/batch', { deviceId, readings }).then((res) => res.data);

export default api;
