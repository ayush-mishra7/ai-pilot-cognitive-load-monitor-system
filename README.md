<p align="center">
  <img src="https://img.shields.io/badge/AI--PCLM-Cognitive%20Load%20Monitor-blueviolet?style=for-the-badge&logo=openai&logoColor=white" alt="AI-PCLM Banner"/>
</p>

<h1 align="center">AI-Pilot Cognitive Load Monitor System</h1>

<p align="center">
  <em>Full-stack real-time simulation and monitoring of pilot cognitive workload with a cockpit-grade React UI, JWT authentication, scenario-driven flight engine, expert + ML cognitive load fusion, Swiss Cheese risk model, and AI-powered recommendations.</em>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-17-ED8B00?style=flat-square&logo=openjdk&logoColor=white" alt="Java 17"/>
  <img src="https://img.shields.io/badge/Spring%20Boot-3.3.4-6DB33F?style=flat-square&logo=springboot&logoColor=white" alt="Spring Boot 3.3.4"/>
  <img src="https://img.shields.io/badge/React-19-61DAFB?style=flat-square&logo=react&logoColor=black" alt="React 19"/>
  <img src="https://img.shields.io/badge/Vite-7-646CFF?style=flat-square&logo=vite&logoColor=white" alt="Vite 7"/>
  <img src="https://img.shields.io/badge/Python-3.11+-3776AB?style=flat-square&logo=python&logoColor=white" alt="Python 3.11+"/>
  <img src="https://img.shields.io/badge/FastAPI-0.115-009688?style=flat-square&logo=fastapi&logoColor=white" alt="FastAPI"/>
  <img src="https://img.shields.io/badge/PostgreSQL-18-4169E1?style=flat-square&logo=postgresql&logoColor=white" alt="PostgreSQL"/>
  <img src="https://img.shields.io/badge/License-MIT-yellow?style=flat-square" alt="License MIT"/>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Tests-115%20Passed-brightgreen?style=flat-square&logo=junit5&logoColor=white" alt="115 Tests Passed"/>
  <img src="https://img.shields.io/badge/Build-Passing-brightgreen?style=flat-square&logo=github-actions&logoColor=white" alt="Build Passing"/>
  <img src="https://img.shields.io/badge/Phase-1%20Complete-blueviolet?style=flat-square" alt="Phase 1"/>
</p>

---

## Table of Contents

- [Overview](#-overview)
- [Screenshots](#-screenshots)
- [Key Features](#-key-features)
- [System Architecture](#-system-architecture)
- [Pipeline Flow](#-pipeline-flow)
- [Tech Stack](#-tech-stack)
- [Project Structure](#-project-structure)
- [Getting Started](#-getting-started)
- [API Endpoints](#-api-endpoints)
- [Testing](#-testing)
- [Bugs Fixed](#-bugs-fixed)
- [Development Roadmap](#-development-roadmap)

---

## 🧠 Overview

**AI-PCLM** (AI-Pilot Cognitive Load Monitor) is a full-stack simulation and monitoring platform designed to evaluate, predict, and mitigate pilot cognitive overload in real time. The system features:

- A **cockpit-themed React frontend** with real-time dashboards, animated radar displays, and scenario configuration
- **JWT-secured REST API** with role-based access (Pilot / ATC)
- **Configurable flight scenarios** (weather, emergency, terrain, visibility) with NORMAL / MODERATE / EXTREME presets
- **6-phase flight simulation** generating deterministic telemetry across TAKEOFF → CRUISE → LANDING
- **Expert + ML hybrid cognitive load** computation with confidence-gated fusion
- **Swiss Cheese multi-barrier risk assessment** with hysteresis-based escalation
- **AI-driven recommendations** including scenario-aware emergency procedures (SQUAWK 7700, DIVERT, DELAY TAKEOFF)

Built for **aviation safety researchers**, **human factors engineers**, and **cockpit design teams** who need a controlled environment to study cognitive load evolution during simulated flight missions.

---

## 📸 Screenshots

<p align="center">
  <img src="aipclm-frontend/src/assets/landing-page.png" width="45%" alt="Landing Page"/>
  <img src="aipclm-frontend/src/assets/home-page.png" width="45%" alt="Home Page"/>
</p>
<p align="center">
  <img src="aipclm-frontend/src/assets/dashboard-page.png" width="45%" alt="Dashboard"/>
  <img src="aipclm-frontend/src/assets/analytics-page.png" width="45%" alt="Analytics"/>
</p>

---

## ✨ Key Features

| Feature | Description |
|---------|-------------|
| 🔐 **JWT Authentication** | BCrypt password hashing, HMAC-SHA384 tokens (24h expiry), role-based routing (PILOT → cockpit, ATC → radar), auto-seeded demo accounts |
| 🎮 **Scenario Engine** | 9-axis flight scenario configuration (weather, emergency, terrain, visibility, runway, time-of-day, traffic, failures, fatigue) with 3 quick presets |
| 🛩️ **6-Phase Flight Simulation** | Deterministic telemetry generation with scenario-aware modifiers across TAKEOFF → CLIMB → CRUISE → DESCENT → APPROACH → LANDING |
| 🧮 **Expert + ML Hybrid Cognitive Load** | Weighted expert model (70%) blended with ML predictions (30%) using confidence-gated fusion |
| 🤖 **ML Inference Service** | FastAPI microservice with 3-second timeout and automatic fallback (confidence=0.5) on failure |
| 🔴 **4-Level Risk Classification** | LOW → MODERATE → HIGH → CRITICAL with hysteresis thresholds to prevent oscillation |
| 🧀 **Swiss Cheese Safety Model** | 4-barrier breach detection (fatigue, errors, turbulence, physiological stress) inspired by James Reason's model |
| 💡 **12 Recommendation Types** | 7 baseline + 5 scenario-aware (SQUAWK_7700, DELAY_TAKEOFF, DIVERT_TO_ALTERNATE, ENGAGE_AUTOPILOT, REDUCE_SPEED) |
| 📊 **Real-Time Cockpit Dashboard** | 3-panel layout with telemetry gauges, cognitive load radial gauge, risk & recommendations — 1-second polling |
| 📈 **Analytics Dashboard** | Sparkline trends, risk distribution bars, ML performance metrics — 3-second polling |
| 🗼 **ATC Radar View** | Animated radar with risk-colored blips, flight strip panel, 3-second auto-refresh |
| ⚛️ **Atomic Pipeline** | 5-stage @Transactional pipeline with full rollback on any stage failure |
| 🔄 **EMA Smoothing** | Exponential Moving Average (α=0.3) over last 5 frames for stable load tracking |
| 🛡️ **Duplicate Frame Guard** | Prevents duplicate telemetry frames on concurrent scheduler ticks |

---

## 🏗️ System Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                            AI-PCLM SYSTEM                                   │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                     REACT FRONTEND (:5174)                          │    │
│  │  Landing → Login/Register → Home → Dashboard → Analytics            │    │
│  │  ATC Radar → ATC Flight Detail                                      │    │
│  │  ScenarioConfigurator · RadialGauge · Sparkline · ThreeBackground   │    │
│  └──────────────────────────────┬──────────────────────────────────────┘    │
│                                 │ REST + JWT                                │
│  ┌──────────────────────────────▼──────────────────────────────────────┐    │
│  │                   SPRING BOOT BACKEND (:8080)                       │    │
│  │                                                                     │    │
│  │  ┌──────────┐  ┌──────────┐  ┌───────────────┐  ┌──────────────┐  │    │
│  │  │   Auth   │  │ Scenario │  │  Scheduler    │  │  Session     │  │    │
│  │  │  JWT +   │  │  Engine  │  │  (1Hz tick)   │  │  Controller  │  │    │
│  │  │  BCrypt  │  │  9-axis  │  └──────┬────────┘  └──────────────┘  │    │
│  │  └──────────┘  └──────────┘         │                              │    │
│  │                              ┌──────▼────────┐                     │    │
│  │                              │  Orchestrator  │ (Atomic Tx)        │    │
│  │                              └──────┬────────┘                     │    │
│  │                    ┌────────────────┼────────────────┐             │    │
│  │              ┌─────▼─────┐  ┌──────▼──────┐  ┌─────▼──────┐      │    │
│  │              │ Simulation│  │  Cognitive   │  │   Risk     │      │    │
│  │              │  Engine   │  │  Load Svc    │  │  Engine    │      │    │
│  │              │ +Scenario │  │ Expert+ML    │  │ SwissCheese│      │    │
│  │              └───────────┘  └──────┬──────┘  └────────────┘      │    │
│  │                                    │                              │    │
│  │                           ┌────────▼─────────┐                    │    │
│  │                           │  Recommendation   │                    │    │
│  │                           │  Engine (12 rules)│                    │    │
│  │                           └──────────────────┘                    │    │
│  └──────────────────────────────────┬────────────────────────────────┘    │
│                                     │                                     │
│  ┌──────────────────┐    ┌──────────▼──────────┐                          │
│  │  ML Inference Svc │    │    PostgreSQL 18    │                          │
│  │  (FastAPI :8001)  │    │    (aipclm_db)      │                          │
│  │  /predict /health │    └─────────────────────┘                          │
│  └──────────────────┘                                                     │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 🔄 Pipeline Flow

Each simulation tick executes this **5-stage atomic pipeline**:

```
Stage 1 ─ Telemetry Generation
   │  SimulationEngineService generates a TelemetryFrame with
   │  scenario-aware modifiers (weather, emergency, visibility multipliers)
   ▼
Stage 2 ─ Cognitive Load Computation
   │  CognitiveLoadService computes expert load (weighted sum of 12 factors)
   │  and fuses it with ML predictions using confidence-gated blending
   ▼
Stage 3 ─ Risk Assessment
   │  RiskEngineService classifies risk (LOW/MODERATE/HIGH/CRITICAL)
   │  using EMA-smoothed load, hysteresis bands, Swiss Cheese barriers,
   │  scenario severity floor, and confidence gate
   ▼
Stage 4 ─ Recommendation Generation
   │  RecommendationEngineService applies 12 rule-based triggers including
   │  scenario-aware rules (emergency → SQUAWK_7700, visibility → DELAY_TAKEOFF)
   ▼
Stage 5 ─ Persist & Commit
      All entities saved atomically; any failure rolls back all stages
```

### Hysteresis Thresholds

| Transition | Escalation (↑) | De-escalation (↓) |
|------------|:--------------:|:------------------:|
| LOW → MODERATE | 40 | 35 |
| MODERATE → HIGH | 60 | 55 |
| HIGH → CRITICAL | 80 | 75 |

### Swiss Cheese Barriers

| Barrier | Trigger Condition |
|---------|-------------------|
| 🔋 Fatigue | `fatigueIndex > 70` |
| ❌ Errors | `errorCount >= 3` |
| 🌪️ Turbulence | `turbulenceLevel > 0.7` |
| 💓 Physiological | `heartRate > 120 AND stressIndex > 60` |

> **Rule**: All 4 barriers must be breached simultaneously + `smoothedLoad > 70` to trigger Swiss Cheese CRITICAL escalation.

### Scenario Modifiers

| Factor | Options | Effect |
|--------|---------|--------|
| Weather | CLEAR · RAIN · SNOW · FOG · THUNDERSTORM · ICE | Turbulence & stress multiplier |
| Emergency | NONE · ENGINE_FAILURE · FIRE · HYDRAULIC_FAILURE · BIRD_STRIKE · MEDICAL | Risk floor + emergency recommendations |
| Terrain | FLAT · MOUNTAINOUS · COASTAL · URBAN · DESERT · OCEANIC | Workload modifier |
| Visibility | UNLIMITED → ZERO (6 levels) | Low-visibility triggers DELAY_TAKEOFF |
| Time of Day | DAY · NIGHT · DAWN · DUSK | Fatigue modifier |
| Runway | DRY · WET · ICY · CONTAMINATED · FLOODED | Landing risk |
| Traffic Density | 0–10 | ATC workload |
| System Failures | 0–5 | Cascading stress |
| Crew Fatigue | 0–10 | Pre-existing fatigue |

---

## 🛠️ Tech Stack

### Backend

| Technology | Purpose |
|-----------|---------|
| ![Java](https://img.shields.io/badge/Java_17-ED8B00?style=flat-square&logo=openjdk&logoColor=white) | Core language |
| ![Spring Boot](https://img.shields.io/badge/Spring_Boot_3.3.4-6DB33F?style=flat-square&logo=springboot&logoColor=white) | Application framework |
| ![Spring Data JPA](https://img.shields.io/badge/Spring_Data_JPA-6DB33F?style=flat-square&logo=spring&logoColor=white) | ORM & repository layer |
| ![Spring Security](https://img.shields.io/badge/Spring_Security-6DB33F?style=flat-square&logo=springsecurity&logoColor=white) | JWT authentication & authorization |
| ![Spring WebFlux](https://img.shields.io/badge/Spring_WebFlux-6DB33F?style=flat-square&logo=spring&logoColor=white) | Non-blocking ML service calls |
| ![PostgreSQL](https://img.shields.io/badge/PostgreSQL_18-4169E1?style=flat-square&logo=postgresql&logoColor=white) | Production database |
| ![H2](https://img.shields.io/badge/H2-0000BB?style=flat-square&logo=database&logoColor=white) | In-memory test database |
| ![JJWT](https://img.shields.io/badge/JJWT_0.12.5-000000?style=flat-square) | JWT token generation & validation |
| ![Lombok](https://img.shields.io/badge/Lombok-DC382D?style=flat-square&logo=lombok&logoColor=white) | Boilerplate reduction |
| ![Maven](https://img.shields.io/badge/Maven-C71A36?style=flat-square&logo=apachemaven&logoColor=white) | Build tool |

### Frontend

| Technology | Purpose |
|-----------|---------|
| ![React](https://img.shields.io/badge/React_19-61DAFB?style=flat-square&logo=react&logoColor=black) | UI framework |
| ![Vite](https://img.shields.io/badge/Vite_7-646CFF?style=flat-square&logo=vite&logoColor=white) | Build tool & dev server |
| ![TailwindCSS](https://img.shields.io/badge/Tailwind_CSS-06B6D4?style=flat-square&logo=tailwindcss&logoColor=white) | Utility-first styling |
| ![Three.js](https://img.shields.io/badge/Three.js-000000?style=flat-square&logo=threedotjs&logoColor=white) | 3D animated background |
| ![React Router](https://img.shields.io/badge/React_Router_7-CA4245?style=flat-square&logo=reactrouter&logoColor=white) | Client-side routing |
| ![Axios](https://img.shields.io/badge/Axios-5A29E4?style=flat-square&logo=axios&logoColor=white) | HTTP client with JWT interceptor |

### ML Service

| Technology | Purpose |
|-----------|---------|
| ![Python](https://img.shields.io/badge/Python_3.11+-3776AB?style=flat-square&logo=python&logoColor=white) | ML service language |
| ![FastAPI](https://img.shields.io/badge/FastAPI_0.115-009688?style=flat-square&logo=fastapi&logoColor=white) | REST API framework |
| ![scikit-learn](https://img.shields.io/badge/scikit--learn_1.6-F7931E?style=flat-square&logo=scikitlearn&logoColor=white) | ML library (future trained model) |
| ![NumPy](https://img.shields.io/badge/NumPy_2.2-013243?style=flat-square&logo=numpy&logoColor=white) | Numerical operations |
| ![Pydantic](https://img.shields.io/badge/Pydantic_2.10-E92063?style=flat-square&logo=pydantic&logoColor=white) | Request validation |
| ![Uvicorn](https://img.shields.io/badge/Uvicorn-499848?style=flat-square&logo=gunicorn&logoColor=white) | ASGI server |

### Testing

| Technology | Purpose |
|-----------|---------|
| ![JUnit5](https://img.shields.io/badge/JUnit_5-25A162?style=flat-square&logo=junit5&logoColor=white) | Testing framework |
| ![Mockito](https://img.shields.io/badge/Mockito-78A641?style=flat-square) | Mocking framework |
| ![AssertJ](https://img.shields.io/badge/AssertJ-4CBB17?style=flat-square) | Fluent assertions |

---

## 📁 Project Structure

```
ai-pclm/
├── README.md
│
├── aipclm-backend/                              # Spring Boot Backend
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/aipclm/system/
│       │   ├── AipclmBackendApplication.java
│       │   ├── auth/                            # ── Phase 0: Auth Foundation ──
│       │   │   ├── controller/
│       │   │   │   └── AuthController.java          # /api/auth/** endpoints
│       │   │   ├── dto/
│       │   │   │   ├── AuthResponse.java
│       │   │   │   ├── LoginRequest.java
│       │   │   │   └── RegisterRequest.java
│       │   │   ├── model/
│       │   │   │   ├── User.java                    # User entity (email, password, role)
│       │   │   │   └── UserRole.java                # PILOT | ATC
│       │   │   ├── repository/
│       │   │   │   └── UserRepository.java
│       │   │   ├── security/
│       │   │   │   ├── JwtAuthFilter.java           # OncePerRequestFilter
│       │   │   │   └── JwtTokenProvider.java        # HMAC-SHA384 token utils
│       │   │   └── service/
│       │   │       └── AuthService.java             # Register, login, BCrypt
│       │   ├── cognitive/
│       │   │   ├── model/
│       │   │   │   ├── CognitiveState.java
│       │   │   │   └── RiskLevel.java               # LOW | MODERATE | HIGH | CRITICAL
│       │   │   ├── repository/
│       │   │   │   └── CognitiveStateRepository.java
│       │   │   └── service/
│       │   │       ├── CognitiveLoadService.java    # Expert + ML fusion
│       │   │       ├── MLInferenceService.java      # WebClient ML caller
│       │   │       ├── MLPredictionRequest.java
│       │   │       └── MLPredictionResponse.java
│       │   ├── config/
│       │   │   ├── CorsConfig.java
│       │   │   ├── DataSeeder.java                  # Seed pilot@aipclm.com & tower@aipclm.com
│       │   │   └── SecurityConfig.java              # Spring Security filter chain
│       │   ├── pilot/
│       │   │   ├── model/
│       │   │   │   ├── Pilot.java
│       │   │   │   └── PilotProfileType.java        # EXPERIENCED | NOVICE | FATIGUE_PRONE | HIGH_STRESS
│       │   │   └── repository/
│       │   │       └── PilotRepository.java
│       │   ├── recommendation/
│       │   │   ├── model/
│       │   │   │   ├── AIRecommendation.java
│       │   │   │   ├── RecommendationType.java      # 12 recommendation types
│       │   │   │   └── Severity.java                # INFO | CAUTION | WARNING | CRITICAL
│       │   │   ├── repository/
│       │   │   │   └── AIRecommendationRepository.java
│       │   │   └── service/
│       │   │       └── RecommendationEngineService.java
│       │   ├── risk/
│       │   │   ├── model/
│       │   │   │   └── RiskAssessment.java
│       │   │   ├── repository/
│       │   │   │   └── RiskAssessmentRepository.java
│       │   │   └── service/
│       │   │       └── RiskEngineService.java       # Hysteresis + Swiss Cheese + scenario floor
│       │   ├── scenario/                            # ── Phase 1: Scenario Engine ──
│       │   │   ├── controller/
│       │   │   │   └── ScenarioController.java      # /api/scenario/** CRUD
│       │   │   ├── dto/
│       │   │   │   └── ScenarioRequest.java
│       │   │   ├── model/
│       │   │   │   ├── DifficultyPreset.java        # NORMAL | MODERATE | EXTREME
│       │   │   │   ├── EmergencyType.java
│       │   │   │   ├── FlightScenario.java          # 9-axis scenario entity
│       │   │   │   ├── MissionType.java
│       │   │   │   ├── RunwayCondition.java
│       │   │   │   ├── TerrainType.java
│       │   │   │   ├── TimeOfDay.java
│       │   │   │   ├── VisibilityLevel.java
│       │   │   │   └── WeatherCondition.java
│       │   │   ├── repository/
│       │   │   │   └── FlightScenarioRepository.java
│       │   │   └── service/
│       │   │       └── ScenarioService.java
│       │   ├── session/
│       │   │   ├── controller/
│       │   │   │   └── SessionMonitoringController.java  # /api/session/** REST API
│       │   │   ├── model/
│       │   │   │   ├── FlightSession.java
│       │   │   │   └── FlightSessionStatus.java
│       │   │   └── repository/
│       │   │       └── FlightSessionRepository.java
│       │   ├── simulation/
│       │   │   ├── service/
│       │   │   │   ├── SimulationEngineService.java     # Scenario-aware telemetry gen
│       │   │   │   ├── SimulationOrchestratorService.java
│       │   │   │   └── SimulationSchedulerService.java  # 1Hz scheduler
│       │   │   └── web/
│       │   │       └── SessionTestController.java
│       │   └── telemetry/
│       │       ├── model/
│       │       │   ├── PhaseOfFlight.java
│       │       │   └── TelemetryFrame.java          # 30+ sensor fields
│       │       └── repository/
│       │           └── TelemetryFrameRepository.java
│       │
│       ├── main/resources/
│       │   └── application.yml
│       │
│       └── test/java/com/aipclm/system/             # 115 unit tests
│
├── aipclm-frontend/                                 # React Frontend
│   ├── package.json
│   ├── vite.config.js
│   ├── tailwind.config.js
│   └── src/
│       ├── App.jsx
│       ├── main.jsx
│       ├── index.css                                # Cockpit theme (Orbitron/Rajdhani/Share Tech Mono)
│       ├── components/
│       │   ├── GlassPanel.jsx                       # Frosted glass card component
│       │   ├── MiniChart.jsx                        # Inline trend charts
│       │   ├── RadialGauge.jsx                      # Cognitive load circular gauge
│       │   ├── RecommendationCard.jsx               # Severity-tagged AI recommendation
│       │   ├── RiskIndicator.jsx                    # Risk level badge
│       │   ├── ScenarioConfigurator.jsx             # 9-axis scenario config accordion
│       │   ├── Sparkline.jsx                        # Animated sparkline charts
│       │   ├── TechGrid.jsx                         # Background grid pattern
│       │   └── ThreeBackground.jsx                  # Three.js animated cockpit background
│       ├── context/
│       │   ├── AuthContext.jsx                      # JWT auth state management
│       │   └── SessionContext.jsx                   # Active session state
│       ├── layouts/
│       │   ├── AtcLayout.jsx                        # ATC navigation layout
│       │   ├── ProtectedLayout.jsx                  # Pilot navigation layout
│       │   └── PublicLayout.jsx                     # Public page layout
│       ├── pages/
│       │   ├── LandingPage.jsx                      # / — Hero + feature cards
│       │   ├── LoginPage.jsx                        # /login
│       │   ├── RegisterPage.jsx                     # /register
│       │   ├── HomePage.jsx                         # /home — Session creation + list
│       │   ├── DashboardPage.jsx                    # /dashboard/:id — Real-time cockpit
│       │   ├── AnalyticsPage.jsx                    # /analytics/:id — Trends & ML perf
│       │   ├── AtcRadarPage.jsx                     # /atc — Animated radar display
│       │   └── AtcFlightDetailPage.jsx              # /atc/flight/:id — Flight detail
│       ├── router/
│       │   └── AppRouter.jsx                        # Route config with guards
│       └── services/
│           └── api.js                               # Axios client + JWT interceptor
│
└── aipclm-ml-service/                               # Python ML Microservice
    ├── main.py                                      # FastAPI with /predict & /health
    └── requirements.txt
```

---

## 🚀 Getting Started

### Prerequisites

| Requirement | Version |
|------------|---------|
| Java JDK | 17+ |
| Maven | 3.8+ |
| Node.js | 18+ |
| Python | 3.11+ |
| PostgreSQL | 15+ |
| Git | 2.30+ |

### 1. Clone the Repository

```bash
git clone https://github.com/ayush-mishra7/ai-pilot-cognitive-load-monitor-system.git
cd ai-pilot-cognitive-load-monitor-system
```

### 2. Set Up the Database

```sql
CREATE DATABASE aipclm_db;
```

> Default credentials: `postgres:postgres` on `localhost:5432`. Update `aipclm-backend/src/main/resources/application.yml` if different.

### 3. Start the ML Service (Optional)

```bash
cd aipclm-ml-service
pip install -r requirements.txt
python main.py
# → http://localhost:8001 (auto-fallback if unavailable)
```

### 4. Start the Backend

```bash
cd aipclm-backend
mvn spring-boot:run
# → http://localhost:8080
```

Verify: `curl http://localhost:8080/api/auth/health` → `{"status":"UP"}`

### 5. Start the Frontend

```bash
cd aipclm-frontend
npm install
npx vite --port 5174
# → http://localhost:5174
```

### 6. Login & Start Monitoring

Open `http://localhost:5174` in your browser. Use the pre-seeded demo accounts:

| Role | Email | Password | Call Sign |
|------|-------|----------|-----------|
| **PILOT** | `pilot@aipclm.com` | `pilot123` | ALPHA-7 |
| **ATC** | `tower@aipclm.com` | `tower123` | TOWER-1 |

---

## 📡 API Endpoints

### Authentication (`/api/auth`)

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/auth/register` | Register new user (PILOT or ATC) |
| `POST` | `/api/auth/login` | Login → returns JWT token |
| `GET` | `/api/auth/me` | Get current user info |
| `GET` | `/api/auth/health` | Health check |

### Sessions (`/api/session`) — *Requires JWT*

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/session` | Create new flight session |
| `GET` | `/api/session/list` | List all sessions |
| `GET` | `/api/session/{id}` | Get session by ID |
| `DELETE` | `/api/session/{id}` | Delete session + all child data |
| `DELETE` | `/api/session/purge-all` | Purge all sessions |
| `GET` | `/api/session/{id}/latest-state` | Latest telemetry + cognitive + risk + recommendations |
| `GET` | `/api/session/{id}/cognitive/latest` | Latest cognitive assessment |
| `GET` | `/api/session/{id}/risk/latest` | Latest risk assessment |
| `GET` | `/api/session/{id}/cognitive/history` | All cognitive frames |
| `GET` | `/api/session/{id}/risk/history` | All risk frames |
| `GET` | `/api/session/{id}/recommendations/latest` | Latest recommendations |

### Scenarios (`/api/scenario`) — *Requires JWT*

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/scenario/{sessionId}` | Create scenario for session |
| `GET` | `/api/scenario/{sessionId}` | Get scenario for session |
| `PUT` | `/api/scenario/{sessionId}` | Update scenario (mid-flight OK) |

### Simulation (`/api/simulation`) — *Requires JWT*

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/simulation/{sessionId}/start` | Start simulation engine |
| `POST` | `/api/simulation/{sessionId}/stop` | Stop simulation engine |

### ML Inference Service (`:8001`)

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/health` | Health check |
| `POST` | `/predict` | Predict cognitive load from telemetry features |

---

## 🧪 Testing

### Run All Tests

```bash
cd aipclm-backend
mvn test
```

### Test Results

```
Tests run: 115, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS
```

### Test Suite Breakdown

| # | Test Class | Tests | Category | Description |
|---|-----------|:-----:|----------|-------------|
| 1 | `SimulationEngineServiceTest` | **23** | 🛩️ Simulation | Phase transitions, noise determinism, scenario modifiers |
| 2 | `RiskEngineServiceTest` | **20** | 🔴 Risk | Hysteresis, Swiss Cheese, confidence gate |
| 3 | `SystemMustNotDoTest` | **14** | 🚫 Constraints | 14 negative safety invariants |
| 4 | `RecommendationEngineServiceTest` | **13** | 💡 Recommendation | All 12 rule triggers, deduplication |
| 5 | `CognitiveLoadServiceTest` | **10** | 🧮 Cognitive | Expert weights, ML fusion, clamping |
| 6 | `SimulationOrchestratorServiceTest` | **9** | ⚛️ Orchestration | Atomic integrity, rollback |
| 7 | `MLInferenceServiceTest` | **8** | 🤖 ML Inference | Timeout, fallback modes |
| 8 | `SessionMonitoringControllerTest` | **8** | 📊 Controller | DTO-only exposure, 404 handling |
| 9 | `SimulationSchedulerServiceTest` | **8** | 🔄 Scheduler | Lifecycle, concurrency guards |
| 10 | `PilotRepositoryTest` | **1** | 🗄️ Repository | JPA integration |
| 11 | `TelemetryFrameRepositoryTest` | **1** | 🗄️ Repository | JPA integration |

---

## 🐛 Bugs Fixed

| # | Bug | Fix |
|---|-----|-----|
| 1 | **ML service crash** — No error handling when ML unreachable | Added try/catch with fallback (confidence=0.5) + 3s timeout |
| 2 | **smoothedLoad always 0.0** — EMA never persisted | Added `cogState.setSmoothedLoad()` + save |
| 3 | **Duplicate telemetry frames** — Concurrent scheduler race | Added frame number guard before generation |
| 4 | **DELETE 403 Forbidden** — SecurityAutoConfiguration exclusion conflicting with custom SecurityConfig | Removed exclusion from `@SpringBootApplication` |
| 5 | **Session delete FK violation** — flight_scenario not cleaned up | Added scenario delete to session delete + purge-all |

---

## 🗺️ Development Roadmap

### Completed Phases

| Phase | Name | Status | Description |
|:-----:|------|:------:|-------------|
| **0** | **Auth Foundation** | ✅ Done | JWT authentication, BCrypt, role-based access (PILOT/ATC), auto-seeded accounts, Spring Security config, React login/register |
| **1** | **Scenario Engine** | ✅ Done | 9-axis flight scenario configuration, 3 presets (NORMAL/MODERATE/EXTREME), scenario-aware simulation modifiers, 5 new recommendation types, cockpit dashboard, analytics, ATC radar |

### Upcoming Phases

| Phase | Name | Status | Description |
|:-----:|------|:------:|-------------|
| **2** | **WebSocket Real-Time Streaming** | 🔜 Next | Replace HTTP polling (1s/3s) with WebSocket push for sub-100ms dashboard updates. STOMP over SockJS with per-session topic channels. |
| **3** | **Advanced ML Pipeline** | 📋 Planned | Replace simulated ML formula with a trained LSTM/Transformer model using NASA-TLX and MATB-II datasets. Add model versioning, A/B testing, and SHAP/LIME explainability layer. |
| **4** | **Multi-Pilot & CRM Simulation** | 📋 Planned | Simulate Captain + First Officer with Crew Resource Management (CRM) cognitive interaction modeling. Shared cockpit state, cross-crew fatigue propagation. |
| **5** | **Wearable & Sensor Integration** | 📋 Planned | Ingest real physiological data from Garmin HRM, EEG headbands, and eye trackers. Replace simulated biometrics with live sensor feeds. |
| **6** | **Containerization & Orchestration** | 📋 Planned | **Docker** — Multi-stage Dockerfiles for backend, frontend, and ML service. Docker Compose for single-command local dev startup. **Kubernetes** — Helm charts for production deployment with auto-scaling, health probes, ConfigMaps, and Secrets. Horizontal Pod Autoscaler for ML inference under load. |
| **7** | **CI/CD & Observability** | 📋 Planned | GitHub Actions pipeline (build → test → Docker push → deploy). Prometheus + Grafana monitoring. OpenTelemetry + Jaeger distributed tracing across Spring Boot ↔ FastAPI boundaries. |
| **8** | **Dynamic Weather & ADS-B** | 📋 Planned | Real-time METAR/TAF weather API integration. ADS-B live feed ingestion for shadow-monitoring actual flights in research mode. |

### Infrastructure Goals

| Goal | Technology | Description |
|------|-----------|-------------|
| 🐳 **Containerization** | Docker + Docker Compose | Multi-stage builds for all 3 services, single `docker-compose up` for full-stack local development |
| ☸️ **Orchestration** | Kubernetes + Helm | Production-grade deployment with auto-scaling pods, rolling updates, liveness/readiness probes, persistent volume claims for PostgreSQL |
| 🔄 **CI/CD** | GitHub Actions | Automated test → build → push → deploy pipeline with environment promotion (dev → staging → prod) |
| 📊 **Observability** | Prometheus + Grafana + Jaeger | Pipeline latency P95/P99, ML inference throughput, database connection pool monitoring, distributed request tracing |
| 🗃️ **Schema Migrations** | Flyway | Version-controlled database migrations replacing `ddl-auto: update` |
| 📝 **API Documentation** | SpringDoc OpenAPI | Auto-generated Swagger UI for all REST endpoints |

---

## 👤 Author

**Ayush Mishra** — [@ayush-mishra7](https://github.com/ayush-mishra7)

---

<p align="center">
  <img src="https://img.shields.io/badge/Made%20with-☕%20Java%20%2B%20⚛️%20React%20%2B%20🐍%20Python-blueviolet?style=for-the-badge" alt="Made with Java + React + Python"/>
</p>

<p align="center">
  <sub>Built for aviation safety research. Simulating cognitive load so pilots don't have to bear it alone.</sub>
</p>
