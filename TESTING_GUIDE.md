# AI-PCLM — Quick-Start & Presentation Guide

> Everything you need to launch the system and present it confidently.

---

## What Is This Project? (Introduction Script)

AI-PCLM stands for **AI-Powered Pilot Cognitive Load Monitor**. In simple words, it is a system that watches how stressed or overloaded a pilot's brain is during a flight — and warns everyone before things go wrong.

Airplane crashes are almost never just about the plane breaking. They happen because the pilot's mind gets overloaded — too many alarms, bad weather, engine problems all at once — and they start missing things. This is called **cognitive overload**.

Our system solves this by:
- **Monitoring** the pilot's heart rate, stress, fatigue, reaction time, and eye behavior in real time.
- **Running an AI model** (Machine Learning) that predicts how mentally loaded the pilot is — on a scale of 0 to 10.
- **Alerting** the pilot, the co-pilot, and Air Traffic Control the moment the load becomes dangerous.
- **Recommending** specific actions — like "turn on autopilot" or "hand over controls to the first officer" — to bring the pilot's brain back to a safe state.

It uses a **Java Spring Boot backend**, a **Python ML microservice**, a **React frontend**, and **WebSocket real-time streaming** — all working together like an actual flight operations center.

---

## How to Launch (Step by Step)

> **Important**: Open 3 separate PowerShell / Terminal windows. Start them in this exact order. Wait for each one to finish before moving to the next.

### Step 0 — Make sure PostgreSQL is running
Your Windows PostgreSQL service must be active in the background on port `5432` with a database called `aipclm_db`.

### Step 1 — Kill any leftover processes (do this every time)
Open any PowerShell and run:
```powershell
# This clears ports 8080, 8001, and 5173 so nothing conflicts
Get-NetTCPConnection -LocalPort 8080,8001,5173 -State Listen -ErrorAction SilentlyContinue |
  ForEach-Object { Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue }
```

### Step 2 — ML Service (Terminal 1)
```powershell
cd C:\Users\ayush\Downloads\ai-pclm\aipclm-ml-service
conda activate aipclm
uvicorn main:app --port 8001
```
✅ **Wait for:** `Application startup complete` and `Uvicorn running on http://127.0.0.1:8001`

### Step 3 — Java Backend (Terminal 2)
```powershell
cd C:\Users\ayush\Downloads\ai-pclm\aipclm-backend
mvn clean spring-boot:run
```
> **Always use `mvn clean spring-boot:run`** (with `clean`). Without `clean`, Maven may skip recompiling changed code and run old classes.

✅ **Wait for:** `Started AipclmBackendApplication` — this takes ~30–60 seconds.

### Step 4 — React Frontend (Terminal 3)
```powershell
cd C:\Users\ayush\Downloads\ai-pclm\aipclm-frontend
npm run dev
```
✅ **Wait for:** `VITE ready` and `Local: http://localhost:5173/`

### Step 5 — Open Browser
Go to **http://localhost:5173**. The system is live.

### Login Accounts (Pre-Created)

| Role | Email | Password |
|------|-------|----------|
| Pilot | `pilot@aipclm.com` | `pilot123` |
| ATC (Air Traffic Control) | `tower@aipclm.com` | `tower123` |

---

## Presentation Demo Flow

Follow this exact sequence during your demo. Each section tells you what to do and what to say.

---

### Screen 1 — Landing Page

**What to do:** Open `localhost:5173`. You'll see the cinematic landing page.

**What to say:**
- "This is the AI Pilot Cognitive Load Monitor — a system that monitors a pilot's mental state in real time during a flight."
- "It uses Machine Learning, real-time WebSocket streaming, and wearable sensor data to predict when a pilot is getting overloaded."
- "Let me show you how it works."

**Action:** Click **"Access Cockpit"** to go to the login page.

---

### Screen 2 — Login (Pilot)

**What to do:** Log in with `pilot@aipclm.com` / `pilot123`.

**What to say:**
- "The system supports two types of users — Pilots and Air Traffic Controllers. Both have separate dashboards."
- "Right now I'm logging in as a pilot."

---

### Screen 3 — Home Page (Mission Control)

**What to do:** You'll see the session management page.

**What to say:**
- "This is the Pilot's Mission Control. From here, pilots can start flight simulations and monitor their cognitive state."
- "The system has customizable scenarios — you can set the weather, emergency type, terrain, and visibility."

**Action:** Open the **SCENARIO** panel and click the **"EXTREME"** preset.

**What to say:**
- "I'm setting up the hardest possible flight — thunderstorm weather, engine failure emergency, mountainous terrain, and zero visibility."
- "This will stress the simulated pilot's brain to the maximum so we can see how the AI reacts."

**Action:** Click **"START NEW SESSION"**. A session card appears.

---

### Screen 4 — Live Cockpit Dashboard

**What to do:** Click the session card to open it.

**What to say:**
- "This is the live cockpit. Everything you see is updating in real time over WebSockets — there's no page refreshing."
- **Left screen:** "The left panel shows telemetry — altitude, airspeed, heart rate, fatigue, stress level, turbulence. These numbers change every 2 seconds."
- **Center screen:** "The center shows the Cognitive Load gauge. This is the main number — it tells us how loaded the pilot's brain is, from 0 to 10. The AI model and an expert formula are both calculating this, and the system blends them together."
- **Right screen:** "The right panel shows the Risk Level — LOW, MEDIUM, HIGH, or CRITICAL — and below it are AI-generated Recommendations. When the load goes up, the system automatically tells the pilot what to do — like 'Engage Autopilot' or 'Reduce Task Switching'."
- "Notice how as the extreme scenario progresses, the cognitive load climbs, the risk level turns red, and more warnings start appearing. This is the system detecting danger in real time."

---

### Screen 5 — Analytics Page

**What to do:** Go back to Home and click the **analytics icon** (chart icon) on the session card.

**What to say:**
- "This is the Analytics Dashboard. It shows live-updating charts of the pilot's cognitive journey."
- "You can see the cognitive load trend over time, risk distribution, ML confidence score, and a Swiss Cheese breakdown."
- "The Swiss Cheese Model is from aviation safety theory — accidents happen when multiple safety barriers have holes that line up. Our system tracks each barrier."
- "If the ML service is running, you'll also see SHAP explainability bars — these tell you exactly *why* the AI thinks the load is high. For example, it might say 'heart rate contributed +3.2 to the load prediction'."

---

### Screen 6 — ATC Radar (Air Traffic Control)

**What to do:** Open a **new browser tab** (or incognito window). Go to `localhost:5173`. Log in as `tower@aipclm.com` / `tower123`.

**What to say:**
- "Now I'm logging in as Air Traffic Control — the person on the ground who watches all the flights."
- "This is the ATC Radar. Every active flight appears as a blip on the radar circle."
- "The blips are color-coded by risk level — green for LOW, yellow for MEDIUM, orange for HIGH, and red for CRITICAL."
- "Watch the flight strip panel on the right — when a flight goes into HIGH or CRITICAL risk, its strip starts flashing red. This means urgent attention is needed."

**What to point out:**
- The red flashing border on emergency flight strips
- The **"📡 CONTACT FLIGHT"** button that appears on dangerous flights

---

### Screen 7 — Real-Time ATC ↔ Flight Chat

**What to do:** Click the **"📡 CONTACT FLIGHT"** button on a HIGH/CRITICAL flight strip.

**What to say:**
- "The system includes real-time text communication between ATC and the flight."
- "Notice the chat uses operational callsigns — ATC-TOWER and FLT-[flight ID] — not personal names. This matches real aviation protocol."

**Action:** Type a message like "Reduce speed, descend to FL250" and send it.

**What to do next:** Switch to the **Pilot tab** and click the **"📡 ATC COMMS"** button on the cockpit dashboard (bottom right).

**What to say:**
- "The pilot sees the same message on their cockpit screen. They can reply back. Every message has a timestamp — like a real flight log."
- "This communication happens over WebSockets — zero delay, no page refresh."

---

### Screen 8 — Crew Mode (Two Pilots)

**What to do:** Go back to Pilot Home. Toggle **"CREW MODE"** on. Select Captain = EXPERIENCED, First Officer = NOVICE. Start a new session. Open the dashboard.

**What to say:**
- "The system also supports dual-pilot Crew Resource Management."
- "You can see two separate cognitive load gauges — one for the Captain and one for the First Officer."
- "At the bottom, there are CRM scores — Communication, Workload Balance, Situational Awareness, and overall CRM Effectiveness."
- "If one pilot is fatigued, the system detects 'fatigue contagion' — the other pilot's fatigue starts rising too. This is based on real human-factors research."

---

### Screen 9 — Sensor Mode (Wearables)

**What to do:** Go back to Home. Toggle off Crew Mode. Toggle **"SENSOR MODE"** on. Start a new session. Open the dashboard.

**What to say:**
- "In Sensor Mode, the system connects to wearable devices to get real biometric data."
- "You'll see the LIVE SENSOR badge pulsing at the top, and extra biometric readings below — GSR (sweat response), SpO₂ (blood oxygen), Skin Temperature, EEG brain waves, Pupil Diameter, and Gaze Fixation."
- "The backend has a full sensor API that supports real devices — **Garmin HRM-Pro** for heart rate, **Muse 2** for EEG brainwaves, **Tobii Pro** for eye tracking."

**What to say about why sensors aren't physically connected:**
- "For this demo, the biometrics are being generated by human-factor algorithms that simulate realistic physiological responses. But the exact same backend API that processes this data is fully ready to accept real Bluetooth sensor streams. Connecting physical devices requires dedicated local Bluetooth nodes on each workstation, which is our next deployment target."

---

## Answering Follow-Up Questions

### "How does this prevent aviation accidents?"

"Airplane crashes are rarely caused by one mechanical failure. They happen because of compounding human errors — this is called the Swiss Cheese Model. When a pilot gets mentally overloaded, they lose situational awareness and start missing obvious warnings. Our system catches this *before* the pilot makes a mistake by monitoring their cognitive load in real time and alerting everyone — the pilot, the co-pilot, and ATC — the moment it gets dangerous."

### "What happens when a CRITICAL warning appears?"

"Three things happen instantly:
1. The pilot's cockpit turns red-state — the dashboard visually forces them to recognize they're overloaded.
2. ATC gets an immediate alert — the flight strip on their radar starts flashing red, and they can contact the pilot directly.
3. The AI recommends specific actions — like 'Engage Autopilot', 'Transfer Controls to First Officer', or 'Reduce Task Switching' — to mechanically reduce the pilot's workload until their heart rate and stress biologically stabilize."

### "Why can't you use eye tracking and Garmin watches right now?"

"The system's backend already has a complete sensor ingestion API — device registration, Bluetooth connection management, and real-time data normalization. What we can't do in a classroom demo is pair physical Bluetooth devices like a Garmin watch or a Tobii eye tracker, because that requires dedicated Bluetooth adapters and device-specific pairing protocols on each machine. The simulated data uses the same physiological models and goes through the exact same pipeline."

### "How does the AI model work?"

"We use a Gradient Boosting machine learning model trained on 12 biometric and environmental features — heart rate, stress, fatigue, reaction time, turbulence, weather severity, and more. It predicts cognitive load on a 0-to-10 scale. The model achieves R² = 0.98 accuracy. We also use SHAP values to explain every prediction — so we can tell you exactly which factor contributed how much."

### "Is this a black box AI?"

"No. We use SHAP — Shapley Additive Explanations — which comes from game theory. It mathematically decomposes every prediction into individual feature contributions. So if the AI says the load is 8.5, we can show you that heart rate contributed +2.1, turbulence contributed +1.8, and fatigue contributed +3.2. Everything is transparent and explainable."

### "How is this different from just putting a heart rate monitor on the pilot?"

"A heart rate monitor only tells you one number. Our system combines 12+ signals, runs them through an AI model, fuses it with an expert-computed formula, and generates actionable recommendations. It also tracks the crew dynamics, connects to ATC in real time, and uses the Swiss Cheese safety model to identify systemic risk — not just one metric."

---

## Troubleshooting

### Backend says "port 8080 already in use"
```powershell
Get-NetTCPConnection -LocalPort 8080 -State Listen | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force }
```

### Backend compiles but runs old code
Always use `mvn clean spring-boot:run` — without `clean`, Maven skips recompilation.

### ML service won't install packages
```powershell
$env:REQUESTS_CA_BUNDLE = ""
pip install --trusted-host pypi.org --trusted-host files.pythonhosted.org -r requirements.txt
```

### Frontend shows blank page or network errors
1. Make sure backend is fully started first (wait for `Started AipclmBackendApplication`)
2. Hard refresh: `Ctrl+Shift+R`
3. Check that ports 8080 and 8001 are listening:
```powershell
Get-NetTCPConnection -LocalPort 8080,8001 -State Listen
```

### Chat messages not appearing on the other side
1. Make sure you used `mvn clean spring-boot:run` for the backend (not just `mvn spring-boot:run`)
2. Hard refresh both browser tabs
3. The chat uses WebSocket pub/sub — both tabs must have an active WebSocket connection

---

<p align="center"><em>AI-PCLM Testing & Presentation Guide v6.0</em></p>
