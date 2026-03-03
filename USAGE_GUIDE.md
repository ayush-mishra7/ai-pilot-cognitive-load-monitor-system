# AI Pilot Cognitive Load Monitor — Usage Guide

> **Version:** Phase 1 (Scenario Engine)  
> **Last Updated:** July 2025  
> **Stack:** React 19 + Vite 7 · Spring Boot 3.3 · PostgreSQL 18 · Python ML Service

---

## Table of Contents

1. [Prerequisites & Startup](#1-prerequisites--startup)
2. [Seed Accounts](#2-seed-accounts)
3. [Test Case 1 — Landing Page](#3-test-case-1--landing-page)
4. [Test Case 2 — Registration](#4-test-case-2--registration)
5. [Test Case 3 — Login (Pilot)](#5-test-case-3--login-pilot)
6. [Test Case 4 — Login (ATC)](#6-test-case-4--login-atc)
7. [Test Case 5 — Create a NORMAL Session](#7-test-case-5--create-a-normal-session)
8. [Test Case 6 — Create an EXTREME Session](#8-test-case-6--create-an-extreme-session)
9. [Test Case 7 — Create a MODERATE Session](#9-test-case-7--create-a-moderate-session)
10. [Test Case 8 — Create a Custom Scenario](#10-test-case-8--create-a-custom-scenario)
11. [Test Case 9 — Real-Time Dashboard (Cockpit View)](#11-test-case-9--real-time-dashboard-cockpit-view)
12. [Test Case 10 — Analytics View](#12-test-case-10--analytics-view)
13. [Test Case 11 — Session Management](#13-test-case-11--session-management)
14. [Test Case 12 — ATC Radar Dashboard](#14-test-case-12--atc-radar-dashboard)
15. [Test Case 13 — ATC Flight Detail Page](#15-test-case-13--atc-flight-detail-page)
16. [Test Case 14 — Mid-Flight Emergency Injection (API)](#16-test-case-14--mid-flight-emergency-injection-api)
17. [Test Case 15 — Auth Edge Cases](#17-test-case-15--auth-edge-cases)
18. [API Quick Reference](#18-api-quick-reference)

---

## 1. Prerequisites & Startup

### Requirements
| Component | Version |
|-----------|---------|
| Java | 17+ |
| Maven | 3.9+ |
| Node.js | 18+ |
| PostgreSQL | 15+ (tested on 18) |
| Python | 3.10+ (for ML service, optional) |

### Database Setup
```sql
-- Run once in psql or pgAdmin
CREATE DATABASE aipclm_db;
-- Default connection: localhost:5432, user: postgres, password: postgres
```

### Start Backend (Terminal 1)
```bash
cd aipclm-backend
mvn spring-boot:run
```
> Backend starts on **http://localhost:8080**  
> Verify: `curl http://localhost:8080/api/auth/health` → `{"status":"UP"}`

### Start Frontend (Terminal 2)
```bash
cd aipclm-frontend
npx vite --port 5174
```
> Frontend starts on **http://localhost:5174**

### Start ML Service (Terminal 3 — Optional)
```bash
cd aipclm-ml-service
pip install -r requirements.txt
python main.py
```
> ML service starts on **http://localhost:5001**

---

## 2. Seed Accounts

These accounts are pre-seeded when the backend first starts:

| Role | Email | Password | Call Sign |
|------|-------|----------|-----------|
| **PILOT** | `pilot@aipclm.com` | `pilot123` | ALPHA-7 |
| **ATC** | `tower@aipclm.com` | `tower123` | TOWER-1 |

---

## 3. Test Case 1 — Landing Page

**Route:** `http://localhost:5174/`

### Steps
1. Open the browser and navigate to `http://localhost:5174/`
2. Observe the landing page

### Expected Result
- **Hero Section** with animated text: *"AI-POWERED PILOT COGNITIVE LOAD MONITORING"*
- **Subtitle:** *"Advanced real-time monitoring system for pilot cognitive state analysis…"*
- **Two CTA buttons:** `BEGIN MONITORING` and `SYSTEM LOGIN` — both navigate to `/login`
- **6 Feature Cards** in a responsive grid:
  - Cognitive Load Analysis
  - Risk Assessment Engine
  - Real-Time Telemetry
  - AI Recommendations
  - Flight Simulation
  - Analytics Dashboard
- Each card has a description and an icon
- Cockpit-themed dark UI with Orbitron/Rajdhani fonts

### Pass Criteria
- Page loads without errors
- All 6 feature cards visible
- CTA buttons redirect to `/login`

---

## 4. Test Case 2 — Registration

**Route:** `http://localhost:5174/register`

### Steps
1. From the login page, click **"Create account"** at the bottom
2. Select a role: **PILOT** or **ATC**
3. Fill in the form:
   - **Full Name:** `Test Pilot`
   - **Email:** `test@aipclm.com`
   - **Password:** `test123` (min 6 chars)
   - **Call Sign:** `BRAVO-9` (optional, max 20 chars)
4. If PILOT selected, choose a **Pilot Profile**: `EXPERIENCED`, `NOVICE`, `FATIGUE_PRONE`, or `HIGH_STRESS`
5. Click **CREATE ACCOUNT**

### Expected Result
- On success: auto-login and redirect to `/home` (PILOT) or `/atc` (ATC)
- On duplicate email: red error banner *"Registration failed: …"*

### Pass Criteria
- PILOT registration → redirects to `/home`
- ATC registration → redirects to `/atc`
- Duplicate email → displays error (does not crash)
- Pilot Profile chips only appear when PILOT role selected

---

## 5. Test Case 3 — Login (Pilot)

**Route:** `http://localhost:5174/login`

### Steps
1. Navigate to `http://localhost:5174/login`
2. Enter:
   - **Email:** `pilot@aipclm.com`
   - **Password:** `pilot123`
3. Click **SIGN IN**

### Expected Result
- Redirect to `/home` (Pilot Home Page)
- JWT token stored in localStorage
- NavBar shows pilot name and role badge

### Pass Criteria
- Successful login → `/home`
- Invalid password → red error message
- Empty fields → form validation prevents submission

---

## 6. Test Case 4 — Login (ATC)

### Steps
1. Navigate to `http://localhost:5174/login`
2. Enter:
   - **Email:** `tower@aipclm.com`
   - **Password:** `tower123`
3. Click **SIGN IN**

### Expected Result
- Redirect to `/atc` (ATC Radar Page)
- ATC-specific layout and navigation

### Pass Criteria
- ATC role → redirects to `/atc` (not `/home`)

---

## 7. Test Case 5 — Create a NORMAL Session

**Route:** `/home` (must be logged in as PILOT)

### Steps
1. Login as `pilot@aipclm.com`
2. On the Home Page, locate the **"NEW MONITORING SESSION"** card
3. In the **Pilot Profile** dropdown, select `EXPERIENCED`
4. The **Scenario Configurator** accordion is collapsed — leave it collapsed (defaults to NORMAL)
5. Click **CREATE & START MONITORING**

### Expected Result
- A new session is created and the simulation engine starts automatically
- You are redirected to the **Dashboard** (`/dashboard/:sessionId`)
- **Telemetry Panel:** altitude ~35000 ft, airspeed ~460 kts, heading cycling
- **Cognitive Load Gauge:** LOW range (~9–20% stress)
- **Risk Level:** LOW (green badge)
- **Recommendations:** Routine items like `MONITOR_INSTRUMENTS`, `MAINTAIN_ALTITUDE`
- Fatigue index stays low (~1–3)

### Pass Criteria
- Session status is RUNNING
- Dashboard shows real-time updating data (1-second intervals)
- Risk stays LOW for EXPERIENCED + NORMAL scenario

---

## 8. Test Case 6 — Create an EXTREME Session

**Route:** `/home`

### Steps
1. Login as `pilot@aipclm.com`
2. On the Home Page, in the **NEW MONITORING SESSION** card:
   - Select **Pilot Profile:** `FATIGUE_PRONE`
3. Click the **"⚙ SCENARIO CONFIGURATION"** accordion to expand it
4. In the **Quick Presets** row, click the **EXTREME** chip
5. Observe all fields auto-populate:
   - Weather: `THUNDERSTORM`
   - Time of Day: `NIGHT`
   - Terrain: `MOUNTAINOUS`
   - Runway Condition: `ICY`
   - Emergency: `ENGINE_FAILURE`
   - Visibility: `ZERO`
   - Traffic Density/System Failures/Crew Fatigue sliders: all set to HIGH values
6. Click **CREATE & START MONITORING**

### Expected Result
- Redirect to Dashboard
- **Cognitive Load:** rapidly climbs to **CRITICAL** (~93–100% stress)
- **Risk Level:** **HIGH** or **CRITICAL** (red badge)
- **Risk Score:** aggregated score ~0.7–0.95
- **Recommendations include scenario-aware actions:**
  - 🔴 `SQUAWK_7700` — *"SQUAWK 7700 — Declare emergency immediately"*
  - 🔴 `DELAY_TAKEOFF` — *"Delay takeoff — conditions below minimums"*
  - 🔴 `DIVERT_TO_ALTERNATE` — *"Divert to alternate airport"*
  - 🟡 `ENGAGE_AUTOPILOT` — *"Engage autopilot to reduce workload"*
  - 🟡 `REDUCE_SPEED` — *"Reduce speed for better control"*
- Fatigue index climbs rapidly (>30)
- Swiss Cheese alignment may trigger

### Pass Criteria
- All 5 scenario-aware recommendation types appear
- Risk level reaches HIGH within ~10 seconds
- Stress index exceeds 80%
- FATIGUE_PRONE profile amplifies cognitive load (1.25× multiplier)

---

## 9. Test Case 7 — Create a MODERATE Session

**Route:** `/home`

### Steps
1. Login as `pilot@aipclm.com`
2. Select **Pilot Profile:** `NOVICE`
3. Expand the Scenario Configurator
4. Click the **MODERATE** preset chip
5. Observe auto-populated values:
   - Weather: `RAIN`
   - Time of Day: `DUSK`
   - Terrain: `COASTAL`
   - Runway Condition: `WET`
   - Emergency: `NONE`
   - Visibility: `LOW`
   - Traffic Density: 5, System Failures: 1, Crew Fatigue: 4
6. Click **CREATE & START MONITORING**

### Expected Result
- Dashboard shows **MODERATE** risk within ~15 seconds
- Stress oscillates in the 30–60% range
- Recommendations: mix of routine + caution-level items
- `REDUCE_SPEED` may appear if visibility is poor
- No emergency-level recommendations (no emergency type set)

### Pass Criteria
- Risk level stays MODERATE (doesn't spike to HIGH without emergency)
- NOVICE profile contributes higher baseline stress (1.15× multiplier)

---

## 10. Test Case 8 — Create a Custom Scenario

**Route:** `/home`

### Steps
1. Login as `pilot@aipclm.com`
2. Select **Pilot Profile:** `HIGH_STRESS`
3. Expand the Scenario Configurator
4. **Do NOT click any preset** — configure manually:
   - **Weather:** Click `FOG` chip
   - **Time of Day:** Click `NIGHT` chip
   - **Terrain:** Click `URBAN` chip
   - **Runway Condition:** Click `CONTAMINATED` chip
   - **Emergency:** Click `FIRE` chip
   - **Visibility:** Click `ZERO` chip
   - **Traffic Density slider:** Drag to 8
   - **System Failures slider:** Drag to 3
   - **Crew Fatigue slider:** Drag to 7
5. Click **CREATE & START MONITORING**

### Expected Result
- Custom scenario applied (no preset label)
- HIGH_STRESS profile (1.20× multiplier) + aggressive scenario modifiers
- **Risk Level:** HIGH or CRITICAL
- **Recommendations:** `SQUAWK_7700` (FIRE emergency), `DELAY_TAKEOFF` (ZERO visibility), `DIVERT_TO_ALTERNATE`
- Stress index ~70–95%

### Pass Criteria
- All 9 scenario fields accurately reflected in the simulation
- Custom combinations produce expected severity

---

## 11. Test Case 9 — Real-Time Dashboard (Cockpit View)

**Route:** `/dashboard/:sessionId`

### Prerequisites
- At least one RUNNING session exists

### Steps
1. From `/home`, click **OPEN DASHBOARD** on any RUNNING session
2. Observe the three-panel cockpit layout

### Expected Panels

#### Left Panel — TELEMETRY
| Field | Sample Value |
|-------|-------------|
| Altitude | 35,241 ft |
| Airspeed | 462 kts |
| Heading | 127° |
| Vertical Speed | +230 ft/min |
| Flight Phase | CRUISE |
| Turbulence | LIGHT |
| Autopilot | ENGAGED / DISENGAGED |

#### Center Panel — COGNITIVE LOAD
- **Circular Gauge** showing cognitive load percentage
- Color-coded: Green (<40%), Yellow (40–65%), Orange (65–80%), Red (>80%)
- **Metrics below gauge:**
  - Expert Computed Load
  - ML Predicted Load
  - Smoothed Cognitive Load
  - Error Probability
  - Stress Index
  - Fatigue Index
  - Confidence Score

#### Right Panel — RISK & RECOMMENDATIONS
- **Risk Level Badge:** LOW / MODERATE / HIGH / CRITICAL
- **Risk Score:** 0.00 – 1.00
- **Key probabilities:** Delayed Reaction, Unsafe Descent, Missed Checklist
- **Escalation Status / Swiss Cheese Trigger**
- **RECOMMENDATIONS list:** Each tagged with severity and color:
  - 🔴 CRITICAL (red)
  - 🟠 WARNING (orange)
  - 🟡 CAUTION (yellow)
  - 🔵 INFO (blue)

### Pass Criteria
- All three panels render correctly
- Data updates every **1 second** (watch telemetry values change)
- Gauge color matches cognitive load severity
- Risk badge color matches risk level

---

## 12. Test Case 10 — Analytics View

**Route:** `/analytics/:sessionId`

### Steps
1. From the **Dashboard**, click the **ANALYTICS** button (or navigate to `/analytics/:sessionId`)
2. Observe the three-panel analytics layout

### Expected Panels

#### Left Panel — COGNITIVE LOAD TRENDS
- **Sparkline Charts** for:
  - Expert Computed Load (over time)
  - ML Predicted Load (over time)
  - Smoothed Load (over time)
- **Current values** displayed next to each sparkline
- **Frame count** at the bottom

#### Center Panel — RISK DISTRIBUTION
- **Horizontal bar chart** showing risk breakdown:
  - Delayed Reaction probability
  - Unsafe Descent probability
  - Missed Checklist probability
- **Risk Level badge** and **Risk Score**
- **Swiss Cheese / Escalation status**

#### Right Panel — ML PERFORMANCE
- **Key Metrics:**
  - Confidence Score
  - Error Probability
  - Fatigue Trend Slope
  - Swiss Cheese Alignment Score
- **Performance assessment** based on confidence thresholds

### Pass Criteria
- Sparklines show data trend over time (not flat lines)
- Analytics data updates every **3 seconds**
- Bar chart widths correspond to probability values
- Navigating back to dashboard preserves session continuity

---

## 13. Test Case 11 — Session Management

**Route:** `/home`

### Steps

#### A — Filter Sessions
1. On the Home Page, locate the **FLIGHT SESSIONS** section
2. Click the filter tabs: **ALL**, **RUNNING**, **COMPLETED**
3. Observe the session list updates based on filter

#### B — Stop a Running Session
1. Find a session with status **RUNNING**
2. Click the **STOP** button (■ icon)
3. Observe session status changes to **COMPLETED**

#### C — Delete a Session
1. Find a session with status **COMPLETED**
2. Click the **DELETE** button (🗑 icon)
3. Session disappears from the list   

#### D — Purge All Sessions
1. If multiple sessions exist, use the API to purge:
```bash
curl -X DELETE http://localhost:8080/api/session/purge-all \
  -H "Authorization: Bearer <YOUR_JWT_TOKEN>"
```
2. Verify 0 sessions on the Home Page

### Pass Criteria
- Filters show correct subsets
- Stop button transitions RUNNING → COMPLETED
- Delete removes session and all associated data (telemetry, cognitive, risk, scenario)
- Purge-all clears everything

---

## 14. Test Case 12 — ATC Radar Dashboard

**Route:** `/atc` (must be logged in as ATC)

### Steps
1. Login as `tower@aipclm.com` / `tower123`
2. Observe the ATC Command Center

### Expected Result
- **Header:** "ATC COMMAND CENTER"
- **Stat Cards:** Active Flights | Completed | Total
- **Radar Display (left):**
  - Animated circular radar with concentric rings and rotating sweep line
  - Colored blips for each active (RUNNING) flight:
    - 🟢 Green = LOW risk
    - 🟡 Yellow = MODERATE risk
    - 🟠 Orange = HIGH risk
    - 🔴 Red = CRITICAL risk
  - Blips pulse/glow based on risk severity
- **Flight Strip Panel (right):**
  - List of active flights with: truncated Session ID, Risk Level badge, Pilot name, Frame count
  - Up to 5 completed flights shown (dimmed)

### Pass Criteria
- Data refreshes every **3 seconds**
- Blip colors match risk levels
- Clicking a blip or flight strip navigates to `/atc/flight/:sessionId`
- Multiple active flights show as separate blips

> **Tip:** Open a second browser/incognito window logged in as PILOT, create sessions there, then switch to ATC view to see flights appear on radar.

---

## 15. Test Case 13 — ATC Flight Detail Page

**Route:** `/atc/flight/:sessionId`

### Steps
1. From the ATC Radar, click on any flight blip or flight strip
2. Observe the flight detail view

### Expected Result
- **Header:** Flight ID (truncated UUID) + Risk Level badge
- **4 Info Cards:**
  
  | Card | Key Fields |
  |------|-----------|
  | **Telemetry** | Altitude, Airspeed, Heading, Vertical Speed, Flight Phase, Turbulence, Autopilot |
  | **Cognitive State** | Expert Load, ML Predicted, Smoothed, Error Probability, Confidence, Fatigue Slope, Swiss Cheese Score |
  | **Risk Assessment** | Risk Level, Risk Score, Delayed Reaction %, Unsafe Descent %, Missed Checklist %, Escalation, Swiss Cheese |
  | **Pilot Biometrics** | Heart Rate, Blink Rate, Fatigue Index, Stress Index, Reaction Time, Error Count |

- **AI Recommendations** section with severity-tagged messages (same as pilot dashboard)
- **Footer:** Frame counts for cognitive and risk data

### Pass Criteria
- All 4 info cards populated with live data
- Data refreshes every **2 seconds**
- Back button ("← RADAR") returns to `/atc`
- Risk badge color matches risk level

---

## 16. Test Case 14 — Mid-Flight Emergency Injection (API)

This test demonstrates injecting a scenario change during an active flight via the REST API.

### Prerequisites
- A RUNNING session exists (note the `sessionId`)
- You have a valid JWT token (from login response)

### Steps

```bash
# Step 1: Get your JWT token
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"pilot@aipclm.com","password":"pilot123"}' | jq -r '.token')

# Step 2: Create a session with NORMAL scenario
SESSION_ID=$(curl -s -X POST http://localhost:8080/api/session \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"pilotProfile":"EXPERIENCED"}' | jq -r '.sessionId')

# Step 3: Create a NORMAL scenario
curl -X POST "http://localhost:8080/api/scenario/${SESSION_ID}" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "weatherCondition": "CLEAR",
    "timeOfDay": "DAY",
    "terrainType": "FLAT",
    "runwayCondition": "DRY",
    "emergencyType": "NONE",
    "visibilityLevel": "UNLIMITED",
    "trafficDensity": 2,
    "systemFailures": 0,
    "crewFatigue": 1
  }'

# Step 4: Start the simulation
curl -X POST "http://localhost:8080/api/simulation/${SESSION_ID}/start" \
  -H "Authorization: Bearer $TOKEN"

# Wait 10 seconds for baseline data to accumulate...
sleep 10

# Step 5: INJECT EMERGENCY — Change to FIRE + FOG mid-flight
curl -X PUT "http://localhost:8080/api/scenario/${SESSION_ID}" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "weatherCondition": "THUNDERSTORM",
    "emergencyType": "FIRE",
    "visibilityLevel": "ZERO",
    "terrainType": "MOUNTAINOUS"
  }'

# Step 6: Check updated risk after ~5 seconds
sleep 5
curl -s "http://localhost:8080/api/session/${SESSION_ID}/risk/latest" \
  -H "Authorization: Bearer $TOKEN" | jq '.riskLevel, .aggregatedRiskScore'
```

### Expected Result
- Before injection: LOW risk, low stress
- After injection: Risk level escalates to **HIGH** or **CRITICAL**
- New recommendations appear: `SQUAWK_7700`, `DIVERT_TO_ALTERNATE`
- If watching the Dashboard in browser, changes appear in real-time

### Pass Criteria
- PUT scenario returns 200 with updated fields
- Subsequent telemetry frames reflect new scenario modifiers
- Risk level increases within 2–3 frames after injection

---

## 17. Test Case 15 — Auth Edge Cases

### A — Invalid Credentials
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"pilot@aipclm.com","password":"wrongpassword"}'
```
**Expected:** HTTP 401, error message

### B — Accessing Protected Route Without Token
```bash
curl http://localhost:8080/api/session
```
**Expected:** HTTP 403 Forbidden

### C — Accessing Protected Route With Invalid Token
```bash
curl http://localhost:8080/api/session \
  -H "Authorization: Bearer invalid.token.here"
```
**Expected:** HTTP 403 Forbidden

### D — Duplicate Registration
```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"fullName":"Dupe","email":"pilot@aipclm.com","password":"test123","role":"PILOT","callSign":"DUPE-1"}'
```
**Expected:** HTTP 400 Bad Request, *"Email already registered"*

### E — Frontend Protected Route Guard
1. Clear localStorage (browser DevTools → Application → Local Storage → Clear)
2. Navigate directly to `http://localhost:5174/home`
3. **Expected:** Automatically redirected to `/login`

### Pass Criteria
- All error responses return appropriate HTTP status codes
- No stack traces leaked to the client
- Protected routes inaccessible without valid JWT

---

## 18. API Quick Reference

### Authentication
| Method | Endpoint | Body | Description |
|--------|----------|------|-------------|
| POST | `/api/auth/register` | `{fullName, email, password, role, callSign?, pilotProfile?}` | Register new user |
| POST | `/api/auth/login` | `{email, password}` | Login, returns JWT |
| GET | `/api/auth/me` | — | Get current user info |
| GET | `/api/auth/health` | — | Health check |

### Sessions
| Method | Endpoint | Body | Description |
|--------|----------|------|-------------|
| POST | `/api/session` | `{pilotProfile}` | Create new session |
| GET | `/api/session` | — | List all sessions |
| GET | `/api/session/{id}` | — | Get session by ID |
| DELETE | `/api/session/{id}` | — | Delete session + all child data |
| DELETE | `/api/session/purge-all` | — | Delete ALL sessions |

### Scenario
| Method | Endpoint | Body | Description |
|--------|----------|------|-------------|
| POST | `/api/scenario/{sessionId}` | `{weatherCondition, timeOfDay, ...}` | Create scenario for session |
| GET | `/api/scenario/{sessionId}` | — | Get scenario for session |
| PUT | `/api/scenario/{sessionId}` | `{partial fields}` | Update scenario (mid-flight OK) |

### Simulation
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/simulation/{sessionId}/start` | Start simulation engine |
| POST | `/api/simulation/{sessionId}/stop` | Stop simulation engine |

### Telemetry & Analytics
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/session/{id}/state/latest` | Latest telemetry frame |
| GET | `/api/session/{id}/cognitive/latest` | Latest cognitive assessment |
| GET | `/api/session/{id}/risk/latest` | Latest risk assessment |
| GET | `/api/session/{id}/cognitive/history` | All cognitive frames |
| GET | `/api/session/{id}/risk/history` | All risk frames |
| GET | `/api/session/{id}/recommendations/latest` | Latest recommendations |

---

## Scenario Field Reference

### Weather Conditions
`CLEAR` · `RAIN` · `SNOW` · `FOG` · `THUNDERSTORM` · `ICE`

### Time of Day
`DAY` · `NIGHT` · `DAWN` · `DUSK`

### Terrain Types
`FLAT` · `MOUNTAINOUS` · `COASTAL` · `URBAN` · `DESERT` · `OCEANIC`

### Runway Conditions
`DRY` · `WET` · `ICY` · `CONTAMINATED` · `FLOODED`

### Emergency Types
`NONE` · `ENGINE_FAILURE` · `FIRE` · `HYDRAULIC_FAILURE` · `BIRD_STRIKE` · `MEDICAL`

### Visibility Levels
`UNLIMITED` · `GOOD` · `MODERATE` · `LOW` · `VERY_LOW` · `ZERO`

### Slider Ranges
| Slider | Min | Max | Description |
|--------|-----|-----|-------------|
| Traffic Density | 0 | 10 | Surrounding air traffic volume |
| System Failures | 0 | 5 | Number of concurrent system failures |
| Crew Fatigue | 0 | 10 | Pre-existing crew fatigue level |

### Quick Presets
| Preset | Weather | Time | Terrain | Runway | Emergency | Visibility | Traffic | Failures | Fatigue |
|--------|---------|------|---------|--------|-----------|------------|---------|----------|---------|
| **NORMAL** | CLEAR | DAY | FLAT | DRY | NONE | UNLIMITED | 2 | 0 | 1 |
| **MODERATE** | RAIN | DUSK | COASTAL | WET | NONE | LOW | 5 | 1 | 4 |
| **EXTREME** | THUNDERSTORM | NIGHT | MOUNTAINOUS | ICY | ENGINE_FAILURE | ZERO | 9 | 4 | 8 |

### Pilot Profile Multipliers
| Profile | Stress Multiplier | Description |
|---------|-------------------|-------------|
| EXPERIENCED | 1.00× | Baseline — handles stress normally |
| NOVICE | 1.15× | Higher baseline stress, less adaptive |
| FATIGUE_PRONE | 1.25× | Amplified fatigue and stress buildup |
| HIGH_STRESS | 1.20× | Elevated stress response curve |

---

## Tips & Troubleshooting

### Keyboard Shortcuts (Browser)
- **F12** — Open DevTools (check console for errors)
- **Ctrl+Shift+J** — Direct to Console

### Common Issues

| Symptom | Cause | Fix |
|---------|-------|-----|
| Backend won't start | Port 8080 in use | `netstat -ano \| findstr :8080` then kill the process |
| Frontend won't start | Port 5174 in use | `npx vite --port 5175` |
| Login returns 401 | Wrong credentials | Use seed credentials from Section 2 |
| Dashboard shows "Loading…" | Session not started | Ensure simulation was started (`/start` endpoint) |
| Delete returns 403 | Old backend jar | Rebuild: `mvn clean package -DskipTests` then restart |
| Risk stays LOW on EXTREME | Scenario not linked | Ensure scenario was created for the session before starting simulation |

### Recommended Test Sequence
1. Start with Test Case 1–4 (auth flow)
2. Create one NORMAL session (TC 5) — verify baseline
3. Create one EXTREME session (TC 6) — verify scenario engine
4. Test analytics (TC 10) on the EXTREME session
5. Test session management (TC 11) — stop, delete
6. Login as ATC (TC 12–13) to see radar view
7. Run mid-flight injection (TC 14) for advanced testing
8. Run auth edge cases (TC 15) for security validation

---

*Built with ❤ for aviation safety — AI Pilot Cognitive Load Monitor System*
