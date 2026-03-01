<p align="center">
  <img src="https://img.shields.io/badge/AI--PCLM-Cognitive%20Load%20Monitor-blueviolet?style=for-the-badge&logo=openai&logoColor=white" alt="AI-PCLM Banner"/>
</p>

<h1 align="center">AI-Pilot Cognitive Load Monitor System</h1>

<p align="center">
  <em>Real-time simulation and monitoring of pilot cognitive workload using expert systems, ML inference, and multi-barrier risk assessment.</em>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-17-ED8B00?style=flat-square&logo=openjdk&logoColor=white" alt="Java 17"/>
  <img src="https://img.shields.io/badge/Spring%20Boot-3.3.4-6DB33F?style=flat-square&logo=springboot&logoColor=white" alt="Spring Boot 3.3.4"/>
  <img src="https://img.shields.io/badge/Python-3.11+-3776AB?style=flat-square&logo=python&logoColor=white" alt="Python 3.11+"/>
  <img src="https://img.shields.io/badge/FastAPI-0.115-009688?style=flat-square&logo=fastapi&logoColor=white" alt="FastAPI"/>
  <img src="https://img.shields.io/badge/PostgreSQL-16-4169E1?style=flat-square&logo=postgresql&logoColor=white" alt="PostgreSQL"/>
  <img src="https://img.shields.io/badge/License-MIT-yellow?style=flat-square" alt="License MIT"/>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Tests-115%20Passed-brightgreen?style=flat-square&logo=junit5&logoColor=white" alt="115 Tests Passed"/>
  <img src="https://img.shields.io/badge/Build-Passing-brightgreen?style=flat-square&logo=github-actions&logoColor=white" alt="Build Passing"/>
  <img src="https://img.shields.io/badge/Coverage-Service%20%7C%20Repository%20%7C%20Controller-blue?style=flat-square" alt="Coverage"/>
</p>

---

## Table of Contents

- [Overview](#-overview)
- [Key Features](#-key-features)
- [System Architecture](#-system-architecture)
- [Pipeline Flow](#-pipeline-flow)
- [Tech Stack](#-tech-stack)
- [Project Structure](#-project-structure)
- [Getting Started](#-getting-started)
- [API Endpoints](#-api-endpoints)
- [Testing](#-testing)
- [Bugs Fixed](#-bugs-fixed)
- [Future Advancements](#-future-advancements)

---

## 🧠 Overview

**AI-PCLM** (AI-Pilot Cognitive Load Monitor) is a full-stack simulation and monitoring platform designed to evaluate, predict, and mitigate pilot cognitive overload in real time. It simulates realistic flight telemetry across six flight phases, feeds the data through an expert cognitive load computation model enhanced by ML predictions, evaluates risk using a Swiss Cheese multi-barrier safety model with hysteresis-based escalation, and generates actionable AI-driven recommendations — all within an atomic, transactional pipeline.

The system is built for **aviation safety researchers**, **human factors engineers**, and **cockpit design teams** who need a controlled environment to study how cognitive load evolves during simulated flight missions and how automated interventions can reduce pilot error probability.

---

## ✨ Key Features

| Feature | Description |
|---------|-------------|
| 🛩️ **6-Phase Flight Simulation** | Deterministic telemetry generation across TAKEOFF → CLIMB → CRUISE → DESCENT → APPROACH → LANDING with seeded randomness (Random(42)) |
| 🧮 **Expert + ML Hybrid Cognitive Load** | Weighted expert model (70%) blended with ML predictions (30%) using confidence-gated fusion |
| 🤖 **ML Inference Service** | FastAPI microservice with 3-second timeout and automatic fallback (confidence=0.5) on failure |
| 🔴 **4-Level Risk Classification** | LOW → MEDIUM → HIGH → CRITICAL with hysteresis thresholds to prevent oscillation |
| 🧀 **Swiss Cheese Safety Model** | 4-barrier breach detection (fatigue, errors, turbulence, physiological stress) inspired by James Reason's model |
| 🎯 **Confidence Gate** | ML confidence < 0.7 automatically blocks CRITICAL risk escalation for safety |
| 💡 **7 Recommendation Types** | REDUCE_TASKS, SUGGEST_BREAK, INCREASE_MONITORING, ALERT_SUPERVISOR, ADJUST_AUTOMATION, SIMPLIFY_DISPLAY, REDISTRIBUTE_WORKLOAD |
| ⚛️ **Atomic Pipeline** | 5-stage @Transactional pipeline with full rollback on any stage failure |
| 🔄 **EMA Smoothing** | Exponential Moving Average (α=0.3) over last 5 frames for stable load tracking |
| 🛡️ **Duplicate Frame Guard** | Prevents duplicate telemetry frame generation on concurrent scheduler ticks |
| 📊 **Session Monitoring API** | REST endpoints exposing DTO-only views (no entity leakage) with proper error handling |

---

## 🏗️ System Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                        AI-PCLM SYSTEM                               │
│                                                                     │
│  ┌──────────────┐    ┌──────────────┐    ┌───────────────────────┐  │
│  │   Scheduler   │───▶│ Orchestrator │───▶│  Simulation Engine    │  │
│  │  (1Hz tick)   │    │  (Atomic Tx) │    │  (Telemetry Gen)     │  │
│  └──────────────┘    └──────┬───────┘    └───────────────────────┘  │
│                             │                                       │
│                    ┌────────▼────────┐                               │
│                    │  Cognitive Load  │◀──── Expert Model (70%)      │
│                    │    Service       │◀──── ML Prediction (30%)     │
│                    └────────┬────────┘                               │
│                             │              ┌──────────────────────┐  │
│                    ┌────────▼────────┐     │  ML Inference Svc    │  │
│                    │   Risk Engine   │     │  (FastAPI :8001)     │  │
│                    │  Swiss Cheese + │     │  ┌────────────────┐  │  │
│                    │  Hysteresis     │     │  │ /predict        │  │  │
│                    └────────┬────────┘     │  │ /health         │  │  │
│                             │              │  └────────────────┘  │  │
│                    ┌────────▼────────┐     └──────────────────────┘  │
│                    │ Recommendation  │                               │
│                    │    Engine       │                               │
│                    └────────┬────────┘                               │
│                             │                                       │
│                    ┌────────▼────────┐                               │
│                    │   PostgreSQL    │                               │
│                    │   Database      │                               │
│                    └─────────────────┘                               │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 🔄 Pipeline Flow

Each simulation tick executes this **5-stage atomic pipeline**:

```
Stage 1 ─ Telemetry Generation
   │  SimulationEngineService generates a deterministic TelemetryFrame
   │  with phase-aware parameters and seeded noise (Random(42))
   ▼
Stage 2 ─ Cognitive Load Computation
   │  CognitiveLoadService computes expert load (weighted sum of 12 factors)
   │  and fuses it with ML predictions using confidence-gated blending
   ▼
Stage 3 ─ Risk Assessment
   │  RiskEngineService classifies risk (LOW/MED/HIGH/CRITICAL)
   │  using EMA-smoothed load, hysteresis bands, Swiss Cheese barriers,
   │  and confidence gate (blocks CRITICAL when confidence < 0.7)
   ▼
Stage 4 ─ Recommendation Generation
   │  RecommendationEngineService applies rule-based triggers
   │  to produce context-appropriate pilot advisories
   ▼
Stage 5 ─ Persist & Commit
      All entities saved atomically; any failure rolls back all stages
```

### Hysteresis Thresholds

| Transition | Escalation (↑) | De-escalation (↓) |
|------------|:--------------:|:------------------:|
| LOW → MEDIUM | 40 | 35 |
| MEDIUM → HIGH | 60 | 55 |
| HIGH → CRITICAL | 80 | 75 |

### Swiss Cheese Barriers

| Barrier | Trigger Condition |
|---------|-------------------|
| 🔋 Fatigue | `fatigueIndex > 70` |
| ❌ Errors | `errorCount >= 3` |
| 🌪️ Turbulence | `turbulenceLevel > 0.7` |
| 💓 Physiological | `heartRate > 120 AND stressIndex > 60` |

> **Rule**: All 4 barriers must be breached simultaneously + `smoothedLoad > 70` to trigger Swiss Cheese CRITICAL escalation.

---

## 🛠️ Tech Stack

### Backend

| Technology | Purpose |
|-----------|---------|
| ![Java](https://img.shields.io/badge/Java_17-ED8B00?style=flat-square&logo=openjdk&logoColor=white) | Core language |
| ![Spring Boot](https://img.shields.io/badge/Spring_Boot_3.3.4-6DB33F?style=flat-square&logo=springboot&logoColor=white) | Application framework |
| ![Spring Data JPA](https://img.shields.io/badge/Spring_Data_JPA-6DB33F?style=flat-square&logo=spring&logoColor=white) | ORM & repository layer |
| ![Spring Security](https://img.shields.io/badge/Spring_Security-6DB33F?style=flat-square&logo=springsecurity&logoColor=white) | Authentication framework |
| ![Spring WebFlux](https://img.shields.io/badge/Spring_WebFlux-6DB33F?style=flat-square&logo=spring&logoColor=white) | Non-blocking ML service calls |
| ![PostgreSQL](https://img.shields.io/badge/PostgreSQL_16-4169E1?style=flat-square&logo=postgresql&logoColor=white) | Production database |
| ![H2](https://img.shields.io/badge/H2-0000BB?style=flat-square&logo=database&logoColor=white) | In-memory test database |
| ![Lombok](https://img.shields.io/badge/Lombok-DC382D?style=flat-square&logo=lombok&logoColor=white) | Boilerplate reduction |
| ![Maven](https://img.shields.io/badge/Maven-C71A36?style=flat-square&logo=apachemaven&logoColor=white) | Build tool |

### ML Service

| Technology | Purpose |
|-----------|---------|
| ![Python](https://img.shields.io/badge/Python_3.11+-3776AB?style=flat-square&logo=python&logoColor=white) | ML service language |
| ![FastAPI](https://img.shields.io/badge/FastAPI_0.115-009688?style=flat-square&logo=fastapi&logoColor=white) | REST API framework |
| ![scikit-learn](https://img.shields.io/badge/scikit--learn_1.6-F7931E?style=flat-square&logo=scikitlearn&logoColor=white) | ML library (future real model) |
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
├── aipclm-backend/                          # Spring Boot Backend
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/aipclm/system/
│       │   ├── AipclmBackendApplication.java
│       │   ├── auth/                        # Authentication (placeholder)
│       │   ├── cognitive/
│       │   │   ├── model/
│       │   │   │   ├── CognitiveState.java      # Core cognitive state entity
│       │   │   │   └── RiskLevel.java            # LOW | MEDIUM | HIGH | CRITICAL
│       │   │   ├── repository/
│       │   │   │   └── CognitiveStateRepository.java
│       │   │   └── service/
│       │   │       ├── CognitiveLoadService.java     # Expert + ML fusion engine
│       │   │       ├── MLInferenceService.java       # WebClient-based ML caller
│       │   │       ├── MLPredictionRequest.java      # ML request DTO
│       │   │       └── MLPredictionResponse.java     # ML response DTO
│       │   ├── config/
│       │   │   └── CorsConfig.java
│       │   ├── pilot/
│       │   │   ├── model/
│       │   │   │   ├── Pilot.java                # Pilot entity with profile type
│       │   │   │   └── PilotProfileType.java     # NOVICE | INTERMEDIATE | EXPERIENCED
│       │   │   └── repository/
│       │   │       └── PilotRepository.java
│       │   ├── recommendation/
│       │   │   ├── model/
│       │   │   │   ├── AIRecommendation.java         # Recommendation entity
│       │   │   │   ├── RecommendationType.java       # 7 recommendation types
│       │   │   │   └── Severity.java                 # INFO | WARNING | CRITICAL
│       │   │   ├── repository/
│       │   │   │   └── AIRecommendationRepository.java
│       │   │   └── service/
│       │   │       └── RecommendationEngineService.java  # Rule-based engine
│       │   ├── risk/
│       │   │   ├── model/
│       │   │   │   └── RiskAssessment.java       # Risk assessment entity
│       │   │   ├── repository/
│       │   │   │   └── RiskAssessmentRepository.java
│       │   │   └── service/
│       │   │       └── RiskEngineService.java    # Hysteresis + Swiss Cheese
│       │   ├── session/
│       │   │   ├── controller/
│       │   │   │   └── SessionMonitoringController.java  # REST API
│       │   │   ├── model/
│       │   │   │   ├── FlightSession.java
│       │   │   │   └── FlightSessionStatus.java
│       │   │   └── repository/
│       │   │       └── FlightSessionRepository.java
│       │   ├── simulation/
│       │   │   ├── service/
│       │   │   │   ├── SimulationEngineService.java      # Telemetry generator
│       │   │   │   ├── SimulationOrchestratorService.java # Atomic pipeline
│       │   │   │   └── SimulationSchedulerService.java   # 1Hz scheduler
│       │   │   └── web/
│       │   │       └── SessionTestController.java
│       │   └── telemetry/
│       │       ├── model/
│       │       │   ├── PhaseOfFlight.java
│       │       │   └── TelemetryFrame.java       # 30+ sensor fields
│       │       └── repository/
│       │           └── TelemetryFrameRepository.java
│       │
│       ├── main/resources/
│       │   └── application.yml                   # PostgreSQL config
│       │
│       └── test/
│           ├── java/com/aipclm/system/
│           │   ├── TestFixtures.java                     # Shared test factories
│           │   ├── SystemMustNotDoTest.java               # 14 negative constraint tests
│           │   ├── cognitive/service/
│           │   │   ├── CognitiveLoadServiceTest.java     # 10 tests
│           │   │   └── MLInferenceServiceTest.java       # 8 tests
│           │   ├── pilot/repository/
│           │   │   └── PilotRepositoryTest.java          # 1 test
│           │   ├── recommendation/service/
│           │   │   └── RecommendationEngineServiceTest.java # 13 tests
│           │   ├── risk/service/
│           │   │   └── RiskEngineServiceTest.java        # 20 tests
│           │   ├── session/controller/
│           │   │   └── SessionMonitoringControllerTest.java # 8 tests
│           │   ├── simulation/service/
│           │   │   ├── SimulationEngineServiceTest.java       # 23 tests
│           │   │   ├── SimulationOrchestratorServiceTest.java # 9 tests
│           │   │   └── SimulationSchedulerServiceTest.java    # 8 tests
│           │   └── telemetry/repository/
│           │       └── TelemetryFrameRepositoryTest.java     # 1 test
│           └── resources/
│               └── application.yml               # H2 in-memory test config
│
└── aipclm-ml-service/                       # Python ML Microservice
    ├── main.py                               # FastAPI app with /predict & /health
    └── requirements.txt                      # FastAPI, scikit-learn, numpy, pydantic
```

---

## 🚀 Getting Started

### Prerequisites

| Requirement | Version |
|------------|---------|
| Java JDK | 17+ |
| Maven | 3.8+ |
| Python | 3.11+ |
| PostgreSQL | 14+ |
| Git | 2.30+ |

### 1. Clone the Repository

```bash
git clone https://github.com/ayush-mishra7/ai-pilot-cognitive-load-monitor-system.git
cd ai-pilot-cognitive-load-monitor-system
```

### 2. Set Up the Database

```sql
-- Connect to PostgreSQL and create the database
CREATE DATABASE aipclm_db;
```

> The application uses `postgres:postgres` as default credentials. Update `aipclm-backend/src/main/resources/application.yml` if your setup differs.

### 3. Start the ML Service

```bash
cd aipclm-ml-service
pip install -r requirements.txt
python main.py
```

The ML service will start on `http://localhost:8001`. Verify with:
```bash
curl http://localhost:8001/health
# → {"status":"ok","service":"aipclm-ml-service"}
```

### 4. Start the Backend

```bash
cd aipclm-backend
mvn spring-boot:run
```

The Spring Boot application will start on `http://localhost:8080`.

### 5. Run a Simulation

```bash
# Create a test session (via the test controller)
curl -X POST http://localhost:8080/api/test/sessions/start

# Monitor the session
curl http://localhost:8080/api/sessions/{sessionId}/latest
```

---

## 📡 API Endpoints

### Session Monitoring Controller

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/sessions/{id}/latest` | Get latest telemetry + cognitive state for a session |
| `GET` | `/api/sessions/{id}/recommendations` | Get all AI recommendations for a session |
| `GET` | `/api/sessions/{id}/risk-history` | Get risk assessment history |
| `GET` | `/api/sessions/{id}/cognitive-history` | Get cognitive state history |

### ML Inference Service

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/health` | Health check |
| `POST` | `/predict` | Predict cognitive load from telemetry features |

#### Prediction Request Body

```json
{
  "expertComputedLoad": 45.0,
  "reactionTimeMs": 350,
  "turbulenceLevel": 0.3,
  "stressIndex": 40.0,
  "fatigueIndex": 25.0,
  "phaseOfFlight": "CRUISE"
}
```

#### Prediction Response

```json
{
  "predicted_load": 47.23,
  "error_probability": 0.4723,
  "confidence_score": 0.85
}
```

---

## 🧪 Testing

### Run All Tests

```bash
cd aipclm-backend
mvn test
```

### Test Results Overview

```
Tests run: 115, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS
```

### Test Suite Breakdown

| # | Test Class | Tests | Category | Description |
|---|-----------|:-----:|----------|-------------|
| 1 | `SimulationEngineServiceTest` | **23** | 🛩️ Simulation | Basic functionality, phase transitions (10 boundary tests), noise determinism, edge cases |
| 2 | `RiskEngineServiceTest` | **20** | 🔴 Risk | Classification, hysteresis (7 tests), Swiss Cheese model, confidence gate, edge cases |
| 3 | `SystemMustNotDoTest` | **14** | 🚫 Constraints | 10 nested classes: no CRITICAL on spike, no crash on ML down, no frames after completion, bounded load, etc. |
| 4 | `RecommendationEngineServiceTest` | **13** | 💡 Recommendation | All 7 rule triggers, deduplication, edge cases |
| 5 | `CognitiveLoadServiceTest` | **10** | 🧮 Cognitive | Core computation, expert weight verification, phase boost, persistence, clamping |
| 6 | `SimulationOrchestratorServiceTest` | **9** | ⚛️ Orchestration | Atomic integrity, rollback on each pipeline stage failure |
| 7 | `MLInferenceServiceTest` | **8** | 🤖 ML Inference | ML up, ML down (4 failure modes), timeout with fallback |
| 8 | `SessionMonitoringControllerTest` | **8** | 📊 Controller | DTO-only exposure (no entity leakage), not-found handling, null safety |
| 9 | `SimulationSchedulerServiceTest` | **8** | 🔄 Scheduler | Normal operation, edge cases, concurrency guards |
| 10 | `PilotRepositoryTest` | **1** | 🗄️ Repository | JPA integration with H2 |
| 11 | `TelemetryFrameRepositoryTest` | **1** | 🗄️ Repository | JPA integration with H2 |

### Test Categories Covered

- ✅ **Simulation Engine** — Telemetry generation, 6-phase transitions, deterministic noise
- ✅ **Cognitive Load** — Expert model weights, ML fusion, phase boost, clamping (0–100)
- ✅ **ML Inference** — Service up/down, timeout (3s), automatic fallback
- ✅ **Risk Engine** — 4-level classification, hysteresis bands, Swiss Cheese 4-barrier model
- ✅ **Recommendations** — All 7 types, severity mapping, deduplication
- ✅ **Orchestration** — Atomic pipeline, @Transactional rollback on all failure points
- ✅ **Scheduler** — Session lifecycle, duplicate prevention, concurrency
- ✅ **Controller** — DTO-only projection, 404 handling, null field safety
- ✅ **System Must Not Do** — 14 negative constraint tests ensuring safety invariants

---

## 🐛 Bugs Fixed

Three critical bugs were identified and fixed during the testing phase:

| # | Bug | File | Fix |
|---|-----|------|-----|
| 1 | **ML service crash** — No error handling when ML service is unreachable | `MLInferenceService.java` | Added try/catch with `buildFallback()` (confidence=0.5) + 3-second timeout via `.timeout(Duration.ofSeconds(3))` |
| 2 | **smoothedLoad always 0.0** — EMA-smoothed load was computed but never persisted | `RiskEngineService.java` | Added `cogState.setSmoothedLoad(smoothedLoad)` + `cognitiveStateRepository.save(cogState)` |
| 3 | **Duplicate telemetry frames** — Concurrent scheduler ticks could generate frames with the same number | `SimulationEngineService.java` | Added guard checking `existingLatest.getFrameNumber() >= nextFrameNumber` before generation |

---

## 🔮 Future Advancements

### Near-Term Enhancements

| Enhancement | Description | Priority |
|------------|-------------|:--------:|
| 🧠 **Real ML Model Training** | Replace the simulated prediction formula with a trained model (LSTM/Transformer) using real pilot physiological data from NASA-TLX or MATB-II datasets | ![High](https://img.shields.io/badge/-HIGH-red?style=flat-square) |
| 📊 **React Dashboard Revival** | Rebuild the React frontend with real-time WebSocket streaming, live telemetry charts (Recharts/D3), and cognitive load heatmaps | ![High](https://img.shields.io/badge/-HIGH-red?style=flat-square) |
| 🔐 **JWT Authentication** | Complete the auth module with JWT token-based authentication, role-based access (Pilot, Supervisor, Admin), and session-scoped API access | ![High](https://img.shields.io/badge/-HIGH-red?style=flat-square) |
| 📈 **Historical Analytics Engine** | Build trend analysis across sessions — fatigue accumulation over multiple flights, pilot performance degradation tracking | ![Medium](https://img.shields.io/badge/-MEDIUM-orange?style=flat-square) |
| 🔔 **Real-Time Alert System** | WebSocket-based push notifications to supervisor dashboards when risk crosses HIGH or Swiss Cheese alignment triggers | ![Medium](https://img.shields.io/badge/-MEDIUM-orange?style=flat-square) |

### Mid-Term Features

| Feature | Description | Priority |
|---------|-------------|:--------:|
| 🎮 **Multi-Pilot Simulation** | Simulate entire flight crews (Captain + First Officer) with crew resource management (CRM) cognitive load interaction modeling | ![Medium](https://img.shields.io/badge/-MEDIUM-orange?style=flat-square) |
| 🌦️ **Dynamic Weather Integration** | Plug in real-time weather APIs (METAR/TAF) to inject actual atmospheric conditions into turbulence modeling | ![Medium](https://img.shields.io/badge/-MEDIUM-orange?style=flat-square) |
| 🏥 **Wearable Device Integration** | Connect real physiological sensors (Garmin HRM, EEG headbands, eye trackers) for live pilot biometric data ingestion | ![Medium](https://img.shields.io/badge/-MEDIUM-orange?style=flat-square) |
| 📱 **Mobile Supervisor App** | Flutter/React Native companion app for supervisors to monitor active flight sessions remotely | ![Low](https://img.shields.io/badge/-LOW-blue?style=flat-square) |
| 🐳 **Docker + K8s Deployment** | Containerize all services with Docker Compose for local dev and Kubernetes Helm charts for production deployment | ![Medium](https://img.shields.io/badge/-MEDIUM-orange?style=flat-square) |

### Long-Term Vision

| Vision | Description | Priority |
|--------|-------------|:--------:|
| 🤖 **Reinforcement Learning Autopilot** | Train an RL agent that dynamically adjusts cockpit automation level based on real-time cognitive load — reducing load during overload and increasing engagement during underload | ![Low](https://img.shields.io/badge/-LOW-blue?style=flat-square) |
| 🧬 **Digital Twin Pilot Profiles** | Build personalized cognitive models per pilot using transfer learning — each pilot's fatigue curves, reaction patterns, and stress responses modeled individually | ![Low](https://img.shields.io/badge/-LOW-blue?style=flat-square) |
| 🌐 **FAA/EASA Compliance Module** | Implement regulatory reporting aligned with FAA Advisory Circulars (AC 120-51E) and EASA Acceptable Means of Compliance for fatigue risk management | ![Low](https://img.shields.io/badge/-LOW-blue?style=flat-square) |
| 🔬 **Explainable AI (XAI) Layer** | Add SHAP/LIME explanations for every ML prediction — showing which telemetry features contributed most to the cognitive load estimate | ![Low](https://img.shields.io/badge/-LOW-blue?style=flat-square) |
| 📡 **ADS-B Live Feed Integration** | Ingest real ADS-B aircraft telemetry for shadow-monitoring actual flights (research mode) to validate the model against real-world operations | ![Low](https://img.shields.io/badge/-LOW-blue?style=flat-square) |

### Infrastructure & DevOps

| Enhancement | Description |
|------------|-------------|
| 🔄 **CI/CD Pipeline** | GitHub Actions with automated `mvn test`, ML service pytest, Docker image builds, and deployment to AWS/GCP |
| 📊 **Observability Stack** | Prometheus metrics + Grafana dashboards for pipeline latency, ML inference P95, and database connection pool monitoring |
| 🔍 **Distributed Tracing** | OpenTelemetry + Jaeger for end-to-end request tracing across Spring Boot ↔ FastAPI service boundaries |
| 📝 **OpenAPI Documentation** | Auto-generated Swagger UI for all REST endpoints with request/response examples |
| 🗃️ **Database Migrations** | Flyway or Liquibase for version-controlled schema migrations replacing `ddl-auto: update` |

---

## 👤 Author

**Ayush Mishra** — [@ayush-mishra7](https://github.com/ayush-mishra7)

---

<p align="center">
  <img src="https://img.shields.io/badge/Made%20with-☕%20Java%20%2B%20🐍%20Python-blueviolet?style=for-the-badge" alt="Made with Java + Python"/>
</p>

<p align="center">
  <sub>Built for aviation safety research. Simulating cognitive load so pilots don't have to bear it alone.</sub>
</p>
