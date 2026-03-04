# AI-PCLM — Comprehensive Testing Guide

> Step-by-step manual testing instructions covering **all 6 phases** (0–5) of the AI-Pilot Cognitive Load Monitor System.

---

## Table of Contents

- [Prerequisites](#prerequisites)
- [Phase 0 — Authentication & Authorization](#phase-0--authentication--authorization)
- [Phase 1 — Scenario Engine & Flight Simulation](#phase-1--scenario-engine--flight-simulation)
- [Phase 2 — WebSocket Real-Time Streaming](#phase-2--websocket-real-time-streaming)
- [Phase 3 — Advanced ML Pipeline & SHAP Explainability](#phase-3--advanced-ml-pipeline--shap-explainability)
- [Phase 4 — Multi-Pilot Crew Resource Management (CRM)](#phase-4--multi-pilot-crew-resource-management-crm)
- [Phase 5 — Wearable & Sensor Integration](#phase-5--wearable--sensor-integration)
- [Automated Unit Tests](#automated-unit-tests)
- [Troubleshooting](#troubleshooting)

---

## Prerequisites

### 1. Start the Database

Ensure PostgreSQL is running on `localhost:5432` with a database named `aipclm_db`:

```sql
CREATE DATABASE aipclm_db;
```

Default credentials: `postgres:postgres`. Update `aipclm-backend/src/main/resources/application.yml` if different.

### 2. Start the ML Service (required for Phase 3 ML tests)

```bash
cd aipclm-ml-service
pip install -r requirements.txt
python main.py
```

Verify: `http://localhost:8001/health` → `{"status":"healthy"}`

> **Note:** The system works without the ML service — it falls back to expert-only mode with `confidence=0.5`.

### 3. Start the Backend

```bash
cd aipclm-backend
mvn spring-boot:run
```

Verify:
```bash
curl http://localhost:8080/api/auth/health
# → {"status":"UP"}
```

### 4. Start the Frontend

```bash
cd aipclm-frontend
npm install
npx vite --port 5174
```

Open `http://localhost:5174` in a browser.

### 5. Seed Accounts

The backend auto-seeds two demo accounts on startup:

| Role | Email | Password |
|------|-------|----------|
| **PILOT** | `pilot@aipclm.com` | `pilot123` |
| **ATC** | `tower@aipclm.com` | `tower123` |

---

## Phase 0 — Authentication & Authorization

### Test 0.1: Health Check

```bash
curl http://localhost:8080/api/auth/health
```

**Expected:** `{"status":"UP"}`

---

### Test 0.2: Register a New Pilot

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "testpilot@test.com",
    "password": "test123",
    "fullName": "Test Pilot",
    "role": "PILOT",
    "pilotProfileType": "EXPERIENCED"
  }'
```

**Expected:** `201 Created` with JWT token in `token` field.

---

### Test 0.3: Register a New ATC

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "testatc@test.com",
    "password": "test123",
    "fullName": "Test ATC",
    "role": "ATC"
  }'
```

**Expected:** `201 Created` with JWT token.

---

### Test 0.4: Login with Seeded Pilot Account

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "pilot@aipclm.com", "password": "pilot123"}'
```

**Expected:** `200 OK` with `token` (JWT). Save this token as `$TOKEN` for subsequent requests.

---

### Test 0.5: Get Current User Info

```bash
curl http://localhost:8080/api/auth/me \
  -H "Authorization: Bearer $TOKEN"
```

**Expected:** User object with `email`, `role`, `fullName`, `callSign`.

---

### Test 0.6: Unauthorized Access (No Token)

```bash
curl http://localhost:8080/api/session/list
```

**Expected:** `403 Forbidden`

---

### Test 0.7: Frontend Login Flow

1. Open `http://localhost:5174`
2. Click **"Access Cockpit"** on landing page
3. Login with `pilot@aipclm.com` / `pilot123`
4. **Expected:** Redirected to Home page with session management UI

---

### Test 0.8: ATC Role Routing

1. Login with `tower@aipclm.com` / `tower123`
2. **Expected:** Redirected to ATC Radar page (not pilot Home page)

---

## Phase 1 — Scenario Engine & Flight Simulation

### Test 1.1: Create a Session with Default Scenario (via UI)

1. Login as pilot
2. On Home page, click **"START NEW SESSION"** (no toggles enabled)
3. **Expected:** A new session appears in the list with status `RUNNING`, scenario defaults (CLEAR weather, no emergency, FLAT terrain)

---

### Test 1.2: Create a Session with Custom Scenario

1. Click the **"SCENARIO"** accordion on the Home page
2. Set:
   - Weather: **THUNDERSTORM**
   - Emergency: **ENGINE_FAILURE**
   - Terrain: **MOUNTAINOUS**
   - Visibility: **LOW**
3. Click **"START NEW SESSION"**
4. **Expected:** Session starts with the custom scenario; dashboard shows increased turbulence and stress

---

### Test 1.3: Quick Presets

1. In the scenario configurator, click **"EXTREME"** preset button
2. **Expected:** All 9 axes auto-fill with extreme values (THUNDERSTORM, ENGINE_FAILURE, MOUNTAINOUS, ZERO visibility, etc.)
3. Start the session
4. **Expected:** Cognitive load rises quickly, risk escalates to HIGH or CRITICAL

---

### Test 1.4: Verify Scenario via API

```bash
# Get scenario for a session
curl http://localhost:8080/api/scenario/{sessionId} \
  -H "Authorization: Bearer $TOKEN"
```

**Expected:** JSON with `weatherCondition`, `emergencyType`, `terrainType`, etc.

---

### Test 1.5: Mid-Flight Scenario Update

```bash
curl -X PUT http://localhost:8080/api/scenario/{sessionId} \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"weatherCondition": "ICE", "emergencyType": "FIRE"}'
```

**Expected:** `200 OK`. The dashboard should reflect changed conditions on subsequent frames.

---

### Test 1.6: Phase Transitions

1. Start a session and watch the dashboard
2. **Expected Phase Sequence:** The `phaseOfFlight` transitions through:
   - `TAKEOFF` (frames 1–50)
   - `CLIMB` (51–150)
   - `CRUISE` (151–600)
   - `DESCENT` (601–900)
   - `APPROACH` (901–1200)
   - `LANDING` (1201–1350)
3. At frame 1350, the session auto-completes with status `COMPLETED`

---

## Phase 2 — WebSocket Real-Time Streaming

### Test 2.1: Live Dashboard via WebSocket

1. Start a session from the Home page
2. Click on the session row to open the Dashboard
3. **Expected:** Dashboard updates every ~2 seconds with:
   - **Left Panel:** Telemetry gauges (altitude, airspeed, heading, heart rate, etc.)
   - **Right Panel Top:** Cognitive load radial gauge (0–100)
   - **Right Panel Bottom:** Risk level badge + AI recommendations

---

### Test 2.2: Session List Auto-Refresh

1. Open the Home page
2. Start a session from another browser tab (or via API)
3. **Expected:** The session list on the first tab updates automatically (WebSocket-driven)

---

### Test 2.3: Multiple Sessions Streaming

1. Start 2–3 sessions simultaneously
2. Open Dashboard for each in separate tabs
3. **Expected:** Each dashboard streams independently with its own telemetry/cognitive/risk data

---

### Test 2.4: Analytics Page Live Streaming

1. Start a session, then navigate to Analytics (click analytics icon in session row)
2. **Expected:** Sparkline charts update in real time:
   - Cognitive load trend
   - Risk distribution bar
   - ML performance metrics (if ML service running)
   - Swiss Cheese barrier sparkline

---

### Test 2.5: ATC Radar (WebSocket)

1. Login as ATC (`tower@aipclm.com`)
2. Start a session from another tab (as pilot)
3. **Expected:** ATC Radar shows animated blip for the active session, color-coded by risk level
4. Click the blip → **Expected:** ATC flight detail page with telemetry/cognitive/risk data

---

### Test 2.6: Verify REST Fallback (Initial Hydration)

When you first open a dashboard URL directly, before WebSocket connects, the page loads initial data via REST:

```bash
curl http://localhost:8080/api/session/{id}/latest-state \
  -H "Authorization: Bearer $TOKEN"
```

**Expected:** Returns latest telemetry + cognitive + risk + recommendations JSON.

---

## Phase 3 — Advanced ML Pipeline & SHAP Explainability

> **Prerequisite:** ML service running on port 8001.

### Test 3.1: ML Service Health

```bash
curl http://localhost:8001/health
```

**Expected:** `{"status":"healthy","model_loaded":true}`

---

### Test 3.2: Model Info

```bash
curl http://localhost:8001/model/info
```

**Expected:** JSON with `version: "1.0.0"`, `algorithm: "GradientBoosting"`, `r2_score`, `mae`, `feature_names` array.

---

### Test 3.3: Direct ML Prediction

```bash
curl -X POST http://localhost:8001/predict \
  -H "Content-Type: application/json" \
  -d '{
    "heart_rate": 95,
    "stress_index": 55,
    "fatigue_index": 40,
    "reaction_time_ms": 300,
    "control_input_frequency": 4.5,
    "blink_rate": 18,
    "instrument_scan_variance": 0.3,
    "error_count": 1,
    "turbulence_level": 0.4,
    "autopilot_engaged": 0,
    "weather_severity": 3,
    "task_switch_rate": 2.5
  }'
```

**Expected:** `{"predicted_load": <number 0-100>, "confidence": <number 0-1>}`

---

### Test 3.4: SHAP Explainability

```bash
curl -X POST http://localhost:8001/explain \
  -H "Content-Type: application/json" \
  -d '{"heart_rate": 95, "stress_index": 55, "fatigue_index": 40, "reaction_time_ms": 300, "control_input_frequency": 4.5, "blink_rate": 18, "instrument_scan_variance": 0.3, "error_count": 1, "turbulence_level": 0.4, "autopilot_engaged": 0, "weather_severity": 3, "task_switch_rate": 2.5}'
```

**Expected:** JSON with `feature_contributions` array (12 entries: `{feature, contribution}`) + `base_value`, `predicted_value`.

---

### Test 3.5: Confidence-Weighted Fusion (Dashboard)

1. Start a session with the ML service **running**
2. Open Analytics page
3. **Expected:** ML confidence badge shows ~0.85+. The fused load blends expert and ML:
   - `fusedLoad = confidence × mlLoad + (1 − confidence) × expertLoad`

---

### Test 3.6: ML Fallback (No ML Service)

1. Stop the ML service (`Ctrl+C` on the Python process)
2. Start a new session
3. **Expected:** The pipeline continues using expert-only mode with `confidence=0.5`. No errors in backend logs — just a warning about ML service unavailability.

---

### Test 3.7: SHAP on Analytics Page

1. With ML service running, open Analytics for an active session
2. **Expected:** SHAP driver bars show feature contributions (e.g., `heart_rate: +5.2`, `fatigue_index: -3.1`)

---

### Test 3.8: Explainability API

```bash
curl http://localhost:8080/api/session/{id}/explainability \
  -H "Authorization: Bearer $TOKEN"
```

**Expected:** JSON with `featureContributions` map and `baseValue`.

---

## Phase 4 — Multi-Pilot Crew Resource Management (CRM)

### Test 4.1: Start Crew-Mode Session (via UI)

1. Login as pilot, go to Home page
2. Toggle **"CREW MODE"** on
3. Select Captain profile (e.g., EXPERIENCED) and FO profile (e.g., NOVICE)
4. Click **"START NEW SESSION"**
5. **Expected:** Session appears with **CREW** badge (orange) in the session list

---

### Test 4.2: Start Crew-Mode Session (via API)

```bash
curl -X POST "http://localhost:8080/api/test/simulation/start-crew?captainProfile=EXPERIENCED&foProfile=NOVICE" \
  -H "Authorization: Bearer $TOKEN"
```

**Expected:** `200 OK` with session details. `crewMode: true`.

---

### Test 4.3: Dual Cockpit Dashboard

1. Open the Dashboard for a crew-mode session
2. **Expected:**
   - **Left Panel:** Captain biometrics (PF — Pilot Flying)
   - **Right Panel:** First Officer biometrics (PM — Pilot Monitoring)
   - Dual cognitive load gauges at the top
   - CRM HUD overlay between the panels showing:
     - Communication score
     - Workload distribution
     - CRM effectiveness
     - Fatigue symmetry

---

### Test 4.4: CRM History API

```bash
curl http://localhost:8080/api/session/{id}/crm-history \
  -H "Authorization: Bearer $TOKEN"
```

**Expected:** Array of CRM assessment objects with fields: `communicationScore`, `workloadDistribution`, `authorityGradient`, `situationalAwareness`, `fatigueSymmetry`, `stressContagion`, `crmEffectiveness`.

---

### Test 4.5: Cross-Crew Fatigue Propagation

1. Start a crew session with **Captain=EXPERIENCED, FO=FATIGUE_PRONE**
2. Watch the dashboard over 20+ frames
3. **Expected:** The FO's higher fatigue gradually "infects" the Captain's fatigue index (stress contagion factor 0.15). Both crew members' fatigue values should slowly converge (convergence factor 0.10).

---

### Test 4.6: CRM Analytics

1. Open Analytics page for a crew-mode session
2. **Expected:** CRM sparklines appear:
   - CRM effectiveness trend
   - Communication score trend
   - Fatigue symmetry trend
   - Captain vs FO load overlay

---

### Test 4.7: ATC View of Crew Sessions

1. Login as ATC
2. Start a crew session from a pilot tab
3. **Expected:** ATC radar shows the crew session. Clicking it shows detail with both crew members' data.

---

## Phase 5 — Wearable & Sensor Integration

### Test 5.1: Start Sensor-Mode Session (via UI)

1. Login as pilot, go to Home page
2. Toggle **"SENSOR MODE"** on (note: sensor mode and crew mode are mutually exclusive)
3. Click **"START NEW SESSION"**
4. **Expected:**
   - Session appears with **SENSOR** badge (purple) in the session list
   - Console log (F12) shows 6 devices registered and connected automatically

---

### Test 5.2: Start Sensor-Mode Session (via API)

```bash
curl -X POST "http://localhost:8080/api/test/simulation/start-sensor?profile=EXPERIENCED" \
  -H "Authorization: Bearer $TOKEN"
```

**Expected:** `200 OK` with `sensorMode: true`.

---

### Test 5.3: Quick-Register All Preset Devices

```bash
curl -X POST http://localhost:8080/api/sensor/quick-register
```

**Expected:** `200 OK` with array of 6 registered devices:
- Garmin HRM-Pro+ (HEART_RATE_MONITOR)
- Muse 2 (EEG_HEADBAND)
- Tobii Pro Nano (EYE_TRACKER)
- Shimmer3 GSR+ (GSR_SENSOR)
- Masimo MightySat Rx (PULSE_OXIMETER)
- Empatica E4 (SKIN_TEMPERATURE_SENSOR)

---

### Test 5.4: List Sensor Devices

```bash
curl http://localhost:8080/api/sensor/device/list
```

**Expected:** Array of all registered devices with `id`, `deviceName`, `sensorType`, `connectionStatus`.

---

### Test 5.5: Connect a Device to a Session

```bash
curl -X PUT http://localhost:8080/api/sensor/device/{deviceId}/connect/{sessionId}
```

**Expected:** Device status changes to `CONNECTED`. Connection goes through `CALIBRATING` → `CONNECTED` automatically.

---

### Test 5.6: Disconnect a Device

```bash
curl -X PUT http://localhost:8080/api/sensor/device/{deviceId}/disconnect
```

**Expected:** Device status changes to `DISCONNECTED`.

---

### Test 5.7: Ingest a Single Sensor Reading

```bash
curl -X POST http://localhost:8080/api/sensor/reading \
  -H "Content-Type: application/json" \
  -d '{
    "sensorDeviceId": "{deviceId}",
    "flightSessionId": "{sessionId}",
    "frameNumber": 1,
    "rawValue": 85.0,
    "unit": "BPM"
  }'
```

**Expected:** `200 OK` with reading object containing `rawValue`, `normalizedValue` (clamped to physiological range), `signalQuality`.

---

### Test 5.8: Ingest Batch Readings

```bash
curl -X POST http://localhost:8080/api/sensor/reading/batch \
  -H "Content-Type: application/json" \
  -d '{
    "readings": [
      {"sensorDeviceId": "{hrmId}", "flightSessionId": "{sessionId}", "frameNumber": 2, "rawValue": 92.0, "unit": "BPM"},
      {"sensorDeviceId": "{eegId}", "flightSessionId": "{sessionId}", "frameNumber": 2, "rawValue": 45.0, "unit": "µV²"},
      {"sensorDeviceId": "{eyeId}", "flightSessionId": "{sessionId}", "frameNumber": 2, "rawValue": 4.2, "unit": "mm"}
    ]
  }'
```

**Expected:** `200 OK` with array of 3 normalized readings.

---

### Test 5.9: Get Sensor Status for Session

```bash
curl http://localhost:8080/api/sensor/session/{sessionId}/status
```

**Expected:** JSON map with sensor types as keys and objects containing `deviceId`, `deviceName`, `connectionStatus`, `latestReading`, `lastDataReceivedAt`.

---

### Test 5.10: Get Latest Sensor Values

```bash
curl http://localhost:8080/api/sensor/session/{sessionId}/latest-values
```

**Expected:** JSON map like:

```json
{
  "HEART_RATE_MONITOR": 85.0,
  "EEG_HEADBAND": 45.0,
  "EYE_TRACKER": 4.2,
  "GSR_SENSOR": 5.5,
  "PULSE_OXIMETER": 98.0,
  "SKIN_TEMPERATURE_SENSOR": 36.5
}
```

---

### Test 5.11: Sensor Override on Dashboard

1. Start a sensor-mode session (via UI with SENSOR MODE toggle)
2. Open the Dashboard
3. **Expected:**
   - Animated **"◉ LIVE SENSOR"** badge appears (pulsing green)
   - Dedicated sensor biometric rows appear below a purple separator line:
     - GSR (µS) — cyan
     - SpO₂ (%) — red
     - Skin Temp (°C) — amber
     - EEG α/β/θ (µV²) — purple
     - Pupil (mm) — blue
     - Gaze (ms) — teal

---

### Test 5.12: Verify Biometric Override via API

After running a sensor-mode session for a few frames:

```bash
curl http://localhost:8080/api/session/{id}/latest-state \
  -H "Authorization: Bearer $TOKEN"
```

**Expected:** The `telemetry` object has `sensorOverride: true` and sensor-specific fields populated (`gsrLevel`, `spO2Level`, `skinTemperature`, `eegAlphaPower`, `eegBetaPower`, `eegThetaPower`, `pupilDiameter`, `gazeFixationDurationMs`).

---

### Test 5.13: Sensor + Crew Mode Mutual Exclusion (UI)

1. Toggle **CREW MODE** on
2. Toggle **SENSOR MODE** on
3. **Expected:** CREW MODE turns off (only one active at a time)

---

### Test 5.14: Normalization Clamping

Ingest readings outside physiological range to test clamping:

```bash
# Heart rate above max range (>240)
curl -X POST http://localhost:8080/api/sensor/reading \
  -H "Content-Type: application/json" \
  -d '{"sensorDeviceId": "{hrmId}", "flightSessionId": "{sessionId}", "frameNumber": 5, "rawValue": 300.0, "unit": "BPM"}'
```

**Expected:** `normalizedValue` clamped to `240.0` (max for HEART_RATE_MONITOR).

---

### Test 5.15: Sessions List Sensor Badge

1. Start both a normal session and a sensor session
2. View the Home page session list
3. **Expected:** Normal session has no badge; sensor session shows purple **SENSOR** badge.

---

## Automated Unit Tests

### Run All 115 Tests

```bash
cd aipclm-backend
mvn test
```

**Expected Output:**

```
Tests run: 115, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS
```

### Test Suite Breakdown

| # | Test Class | Tests | Coverage |
|---|-----------|:-----:|----------|
| 1 | `SimulationEngineServiceTest` | 23 | Phase transitions, noise, scenario modifiers, auto-complete at 1350 frames |
| 2 | `RiskEngineServiceTest` | 20 | Hysteresis bands, Swiss Cheese, confidence gate, scenario floor |
| 3 | `SystemMustNotDoTest` | 14 | Safety invariants (no duplicates, no thread leaks, load bounded, no crash on ML down) |
| 4 | `RecommendationEngineServiceTest` | 13 | All 12 recommendation triggers + deduplication |
| 5 | `CognitiveLoadServiceTest` | 10 | Expert weights, ML fusion, EMA smoothing, clamping |
| 6 | `SimulationOrchestratorServiceTest` | 9 | Atomic pipeline integrity, rollback on failure |
| 7 | `MLInferenceServiceTest` | 8 | Timeout handling, fallback modes |
| 8 | `SessionMonitoringControllerTest` | 8 | DTO exposure, 404 handling |
| 9 | `SimulationSchedulerServiceTest` | 8 | Lifecycle, concurrency guards, duplicate prevention |
| 10 | `PilotRepositoryTest` | 1 | JPA integration |
| 11 | `TelemetryFrameRepositoryTest` | 1 | JPA integration with sensor fields |

### Frontend Build Verification

```bash
cd aipclm-frontend
npm run build
```

**Expected:** `✓ built in ~11s` with no errors (chunk size warning is cosmetic).

---

## Troubleshooting

### Backend won't start — "port 8080 already in use"

Kill existing process:
```bash
# Windows
netstat -ano | findstr :8080
taskkill /PID <pid> /F
```

### Database connection refused

Ensure PostgreSQL is running and `aipclm_db` exists:
```sql
SELECT 1;  -- basic connectivity test
\l         -- list databases
```

### ML service SSL errors (pip install)

```bash
# Windows PowerShell
$env:REQUESTS_CA_BUNDLE = ""
pip install --trusted-host pypi.org --trusted-host files.pythonhosted.org -r requirements.txt
```

### WebSocket not connecting

- Ensure the backend `WebSocketConfig` allows your origin (`http://localhost:5174`)
- Check browser console for CORS or handshake errors
- Try refreshing the page — WebSocket reconnects with exponential back-off

### Session stuck at "RUNNING" forever

Sessions auto-complete at frame 1350 (≈45 min at 2s intervals). For quicker testing, use the Stop Simulation API:

```bash
curl -X POST http://localhost:8080/api/simulation/{sessionId}/stop \
  -H "Authorization: Bearer $TOKEN"
```

### Sensor devices not overriding telemetry

1. Ensure the session was started in **sensor mode** (`sensorMode: true`)
2. Ensure devices are **connected** to the session (not just registered)
3. Ensure readings have been **ingested** for the current frame
4. Check backend logs for `[SENSOR]` tags confirming override application

### Purge all data for fresh testing

```bash
curl -X DELETE http://localhost:8080/api/session/purge-all \
  -H "Authorization: Bearer $TOKEN"
```

This deletes **all** sessions, telemetry, cognitive states, risks, recommendations, CRM assessments, sensor readings, and sensor devices.

---

<p align="center"><em>AI-PCLM Testing Guide v5.0 — Phases 0–5</em></p>
