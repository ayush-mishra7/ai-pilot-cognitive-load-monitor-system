# AIPCLM v2.0 — Feature Expansion Master Plan

## Scenario Conditions · ATC Role · Real-Time Kafka Messaging · Black Box Logging

> Detailed implementation blueprint for transforming AIPCLM from a single-pilot monitor into a full **Pilot ↔ ATC command-and-control system** with scenario-based risk tuning, real-time Kafka messaging, and flight-data-recorder–grade audit logging.

---

## Executive Summary

Three major feature pillars:

| # | Pillar | What It Adds |
|---|--------|-------------|
| **1** | **Scenario Engine** | Pre-flight condition presets (weather, visibility, time-of-day, runway state, emergency type) that feed into the simulation and risk model |
| **2** | **ATC Role & Dual-View** | Role-based login (Pilot vs ATC), a dedicated ATC dashboard showing all active flights on a radar-style map, risk triage, and pilot details |
| **3** | **Kafka Real-Time Comms** | ATC ↔ Pilot bidirectional messaging via Apache Kafka, displayed as a cockpit radio log with black-box–grade timestamped persistence |

---

## Table of Contents

- [Phase 0 — Foundation (Auth + DB Schema)](#phase-0--foundation-auth--db-schema)
- [Phase 1 — Scenario Engine](#phase-1--scenario-engine)
- [Phase 2 — ATC Role & Dashboard](#phase-2--atc-role--dashboard)
- [Phase 3 — Kafka Real-Time Messaging](#phase-3--kafka-real-time-messaging)
- [Phase 4 — Black Box Flight Recorder](#phase-4--black-box-flight-recorder)
- [Phase 5 — UI/UX Theme & Polish](#phase-5--uiux-theme--polish)
- [Security Considerations](#security-considerations)
- [Tech Stack Additions](#tech-stack-additions)
- [Database Schema (New/Modified Tables)](#database-schema-newmodified-tables)
- [API Endpoint Plan](#api-endpoint-plan)
- [Frontend Route Plan](#frontend-route-plan)
- [File-Level Implementation Checklist](#file-level-implementation-checklist)
- [Estimated Timeline](#estimated-timeline)

---

## Phase 0 — Foundation (Auth + DB Schema)

> **Goal:** Replace the fake login with real JWT-based role authentication. This is the prerequisite for everything else.

### 0.1 — User Entity & Roles

Currently there is **no authentication** — the `auth/` package is empty, `LoginPage.jsx` does `setTimeout → navigate('/home')`, and there's no user table.

**New `users` table:**

```
users
├── id              UUID (PK)
├── email           VARCHAR(255) UNIQUE NOT NULL
├── password_hash   VARCHAR(255) NOT NULL          -- BCrypt
├── full_name       VARCHAR(150) NOT NULL
├── role            ENUM('PILOT','ATC','ADMIN')    -- determines which dashboard they see
├── call_sign       VARCHAR(20) UNIQUE             -- e.g., "ALPHA-7" for pilot, "TOWER-1" for ATC
├── created_at      TIMESTAMP
└── updated_at      TIMESTAMP
```

**For PILOT users:** link to the existing `pilots` table via a new `user_id` FK on `pilots`, so one user account maps to one pilot profile.

**For ATC users:** they don't need a pilot profile — they monitor all flights.

### 0.2 — JWT Authentication Flow

```
┌─────────┐    POST /api/auth/register     ┌──────────┐
│ Frontend │ ──────────────────────────────→│ Backend  │
│          │    POST /api/auth/login        │          │
│          │ ──────────────────────────────→│ AuthCtrl │
│          │ ←── { accessToken, role, ... } │          │
│          │                                └──────────┘
│          │    Authorization: Bearer <JWT>
│          │ ──────────────────────────────→ All /api/** endpoints
└─────────┘
```

**Backend files to create:**

| File | Purpose |
|------|---------|
| `auth/model/User.java` | JPA entity |
| `auth/model/UserRole.java` | Enum: `PILOT`, `ATC`, `ADMIN` |
| `auth/repository/UserRepository.java` | Spring Data JPA |
| `auth/dto/RegisterRequest.java` | Email, password, fullName, role, callSign |
| `auth/dto/LoginRequest.java` | Email, password |
| `auth/dto/AuthResponse.java` | Token, role, userId, callSign |
| `auth/service/AuthService.java` | Register, login, password hashing |
| `auth/controller/AuthController.java` | REST endpoints |
| `auth/security/JwtTokenProvider.java` | Token generation/validation (HMAC-SHA256, 24h expiry) |
| `auth/security/JwtAuthFilter.java` | OncePerRequestFilter — extracts JWT, sets SecurityContext |
| `config/SecurityConfig.java` | HttpSecurity config — public: `/api/auth/**`; role-gated: `/api/atc/**` requires ATC; everything else requires authenticated |

**Frontend files to create/modify:**

| File | Change |
|------|--------|
| `context/AuthContext.jsx` | **CREATE** — stores JWT token, user role, userId in state + localStorage |
| `services/api.js` | Add Axios interceptor to attach `Authorization: Bearer` header on every request |
| `pages/LoginPage.jsx` | **REWRITE** — real API call to `/api/auth/login`, store token, redirect by role |
| `pages/RegisterPage.jsx` | **CREATE** — registration form with role selector |
| `components/ProtectedRoute.jsx` | **MODIFY** — check JWT validity + role-based access |
| `router/AppRouter.jsx` | Add register route, ATC routes |

### 0.3 — Seed Data

Create a `data.sql` or `CommandLineRunner` to seed:
- 1 ATC user: `tower@aipclm.com / ATC / callSign: TOWER-1`
- 1 Pilot user: `pilot@aipclm.com / PILOT / callSign: ALPHA-7`

---

## Phase 1 — Scenario Engine

> **Goal:** Let the user (pilot or ATC) configure environmental conditions before or during a flight, and have those conditions directly affect telemetry generation and risk scoring.

### 1.1 — Scenario Model

**New `flight_scenario` table:**

```
flight_scenario
├── id                    UUID (PK)
├── flight_session_id     UUID (FK → flight_sessions) UNIQUE
│
│   ── Weather & Atmosphere ──
├── weather_condition     ENUM('CLEAR','CLOUDY','OVERCAST','RAIN','THUNDERSTORM','SNOW','ICE','FOG')
├── visibility            ENUM('UNLIMITED','GOOD','MODERATE','LOW','VERY_LOW','ZERO')  -- meters
├── wind_speed_knots      INT          -- 0–80
├── wind_gust_knots       INT          -- 0–50 (additional gusts)
├── crosswind_component   DOUBLE       -- knots, lateral
├── temperature_c         INT          -- affects engine performance
│
│   ── Time & Lighting ──
├── time_of_day           ENUM('DAY','DUSK','NIGHT')
├── moon_illumination     DOUBLE       -- 0.0 (new moon)–1.0 (full), night-only
│
│   ── Terrain & Airport ──
├── terrain_type          ENUM('FLAT','MOUNTAINOUS','COASTAL','DESERT','URBAN')
├── runway_condition      ENUM('DRY','WET','CONTAMINATED','ICY','FLOODED')
├── runway_length_ft      INT          -- short = harder landing
├── airport_elevation_ft  INT          -- high altitude = thinner air
│
│   ── Mission Profile ──
├── mission_type          ENUM('ROUTINE','TRAINING','COMBAT','MEDICAL_EVAC','CARGO','VIP')
├── emergency_type        ENUM('NONE','ENGINE_FAILURE','HYDRAULIC_LOSS','BIRD_STRIKE',
│                                'CABIN_DEPRESSURIZATION','FUEL_LEAK','ELECTRICAL_FAILURE',
│                                'FIRE','GEAR_MALFUNCTION')
│
│   ── Overall Difficulty ──
├── difficulty_preset     ENUM('NORMAL','MODERATE','EXTREME')  -- quick-select
│
├── created_at            TIMESTAMP
└── updated_at            TIMESTAMP
```

### 1.2 — Scenario → Simulation Integration

Modify `SimulationEngineService.generateNextFrame()`:

```
Current flow:
  phase → base telemetry → Gaussian noise → pilot modifiers → save

New flow:
  phase → base telemetry
       ↓
  [Scenario Modifiers Applied]
  ├── Weather → turbulence multiplier (THUNDERSTORM = ×3.0, ICE = ×2.5, FOG = ×1.0 but visibility penalty)
  ├── Visibility → affects stress (+20% in LOW, +40% in ZERO)
  ├── Wind → crosswind affects heading deviation, landing difficulty
  ├── Time of Day → NIGHT adds +15% stress, DUSK +8%
  ├── Runway → WET/ICY adds landing difficulty modifier
  ├── Emergency → injects specific anomalies (engine failure = power loss at frame X, etc.)
  ├── Terrain → MOUNTAINOUS adds turbulence, restricts altitude floor
  └── Mission → COMBAT/MEDICAL increases base stress
       ↓
  Gaussian noise → pilot modifiers → save
```

### 1.3 — Scenario → Risk Model Integration

Modify `RiskEngineService.evaluateRisk()`:

- **Scenario severity multiplier** on the aggregated risk score:
  - `NORMAL` = ×1.0, `MODERATE` = ×1.3, `EXTREME` = ×1.6
- **Weather penalty** added to smoothed load thresholds:
  - THUNDERSTORM/ICE → lower the HIGH threshold from 60 → 50
  - FOG + NIGHT → lower CRITICAL threshold from 80 → 70
- **Emergency type** forces minimum risk floor:
  - Any emergency ≠ NONE → risk floor = MEDIUM (cannot be LOW during emergencies)
  - ENGINE_FAILURE / FIRE → risk floor = HIGH

### 1.4 — New Recommendation Rules

Add to `RecommendationEngineService`:

| Rule | Condition | Type | Severity |
|------|-----------|------|----------|
| ILS approach | Visibility ≤ LOW + phase APPROACH | `REQUEST_ILS_APPROACH` | WARNING |
| Divert | Emergency active + risk CRITICAL | `DIVERT_TO_ALTERNATE` | CRITICAL |
| Go around (weather) | Crosswind > 25kt + phase LANDING | `GO_AROUND_WEATHER` | CRITICAL |
| Delay takeoff | THUNDERSTORM + phase TAKEOFF | `DELAY_TAKEOFF` | WARNING |
| Declare emergency | Any emergency_type active | `SQUAWK_7700` | CRITICAL |

### 1.5 — Frontend: Scenario Configuration Panel

**New component: `ScenarioConfigurator.jsx`**

Displayed on the **Home page** inside the "NEW SESSION" card, expandable as an accordion panel:

```
┌──────────────────────────────────────────────┐
│  ⚙ MISSION SCENARIO                          │
│                                               │
│  Quick Preset:  [NORMAL] [MODERATE] [EXTREME] │
│                                               │
│  Weather:     ☐ Clear ☐ Rain ☐ Thunder ☐ Snow │
│  Visibility:  ☐ Unlimited ☐ Moderate ☐ Low    │
│  Time:        ☐ Day  ☐ Dusk  ☐ Night          │
│  Wind:        ━━━━━━━━━●━━━━━━━━ 35 kt        │
│  Runway:      ☐ Dry  ☐ Wet  ☐ Icy             │
│  Terrain:     ☐ Flat ☐ Mountain ☐ Coastal      │
│  Emergency:   ☐ None ☐ Engine Fail ☐ Fire ...  │
│  Mission:     ☐ Routine ☐ Combat ☐ MedEvac     │
│                                               │
│  [CREATE & START WITH SCENARIO]               │
└──────────────────────────────────────────────┘
```

**Styling:** Dark glassmorphism cards with cyan/amber accent colors matching the HUD theme. Sliders use the cockpit green glow. Toggle buttons use the existing `hud-btn` classes.

---

## Phase 2 — ATC Role & Dashboard

> **Goal:** A completely separate interface for Air Traffic Controllers — a radar-style overview of all active flights with risk triage, pilot details, and communication tools.

### 2.1 — Aircraft & Flight Path Model

**New `aircraft` table:**

```
aircraft
├── id              UUID (PK)
├── registration    VARCHAR(20) NOT NULL      -- e.g., "N12345" or "VT-AXZ"
├── type            VARCHAR(50) NOT NULL      -- "Boeing 737-800", "F-16 Falcon"
├── category        ENUM('COMMERCIAL','MILITARY','PRIVATE','CARGO','HELICOPTER')
├── max_altitude_ft INT
├── max_speed_kts   INT
├── created_at      TIMESTAMP
└── updated_at      TIMESTAMP
```

**Modified `flight_sessions` — add columns:**

```
+ aircraft_id       UUID (FK → aircraft) NULLABLE (backwards-compatible)
+ departure_icao    VARCHAR(4)        -- "VIDP" (Delhi), "KJFK" (JFK)
+ arrival_icao      VARCHAR(4)        -- "VABB" (Mumbai)
+ flight_number     VARCHAR(10)       -- "AI101"
+ squawk_code       VARCHAR(4)        -- transponder code, default "1200"
+ current_lat       DOUBLE            -- updated each frame
+ current_lng       DOUBLE            -- updated each frame
+ current_heading   DOUBLE            -- degrees true north
```

**New `flight_waypoint` table (flight path):**

```
flight_waypoint
├── id                  UUID (PK)
├── flight_session_id   UUID (FK → flight_sessions)
├── sequence_number     INT
├── waypoint_name       VARCHAR(10)     -- "ALPHA", "BRAVO", "WPT03"
├── latitude            DOUBLE
├── longitude           DOUBLE
├── altitude_ft         INT             -- planned altitude at this waypoint
├── is_passed           BOOLEAN         -- true once the sim reaches it
└── created_at          TIMESTAMP
```

### 2.2 — ATC Backend Endpoints

**New controller: `AtcController.java` — `/api/atc/**`**

| Method | Endpoint | Purpose |
|--------|----------|---------|
| `GET` | `/api/atc/flights` | All active flights with pilot info, aircraft, risk level, position |
| `GET` | `/api/atc/flights/{sessionId}/detail` | Deep drill-down: full telemetry, cognitive state, recommendations, pilot profile, scenario, messages |
| `GET` | `/api/atc/flights/{sessionId}/path` | Flight plan waypoints + current position |
| `GET` | `/api/atc/flights/{sessionId}/messages` | All radio messages for this flight |
| `POST` | `/api/atc/flights/{sessionId}/assess` | ATC manual risk override or annotation |
| `GET` | `/api/atc/statistics` | Summary: total active, by risk level, by phase |

**All endpoints gated to `UserRole.ATC` or `UserRole.ADMIN` via Spring Security.**

### 2.3 — ATC Flight Summary DTO

```java
AtcFlightSummaryDto {
    UUID sessionId;
    String flightNumber;          // "AI101"
    String aircraftType;          // "Boeing 737-800"
    String aircraftRegistration;  // "VT-AXZ"
    String pilotName;             // "Cpt. Sharma"
    String pilotCallSign;         // "ALPHA-7"
    String pilotProfileType;      // "EXPERIENCED"
    String departureIcao;         // "VIDP"
    String arrivalIcao;           // "VABB"
    String phaseOfFlight;         // "APPROACH"
    String riskLevel;             // "HIGH"
    double aggregatedRiskScore;   // 72.5
    double cognitiveLoad;         // 6.8
    double latitude;
    double longitude;
    double altitude;
    double airspeed;
    int totalFrames;
    String squawkCode;            // "7700" if emergency
    boolean swissCheeseTriggered;
    String emergencyType;         // from scenario
    Instant lastUpdateTime;
    List<RecommendationDto> activeRecommendations;
}
```

### 2.4 — ATC Frontend Pages

**New route structure:**

```
/atc                    → ATC main dashboard (radar view)
/atc/flight/:sessionId  → ATC single-flight deep view
/atc/messages           → ATC message center (all comms)
/atc/blackbox/:id       → Black box replay
```

**2.4.1 — ATC Radar Dashboard (`AtcRadarPage.jsx`)**

The centerpiece — a dark radar-style map showing all active flights:

```
┌─────────────────────────────────────────────────────────────────┐
│  ATC COMMAND CENTER                            TOWER-1  LOGOUT  │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────────────────────────┐  ┌──────────────────────┐  │
│  │         RADAR DISPLAY           │  │  FLIGHT STRIP PANEL  │  │
│  │                                 │  │                      │  │
│  │    ·  AI101                     │  │  ⚠ AI101  HIGH       │  │
│  │       ↗ (heading arrow)         │  │  B737 · APPROACH     │  │
│  │       FL350 · 280kt            │  │  CLT: 6.8 / RISK: 72 │  │
│  │                                 │  │  [VIEW] [MESSAGE]    │  │
│  │              · VT202            │  │                      │  │
│  │                ↑                │  │  ✓ VT202  LOW        │  │
│  │                FL380 · 310kt   │  │  A320 · CRUISE       │  │
│  │                                 │  │  CLT: 2.1 / RISK: 15 │  │
│  │  ── radar sweep animation ──   │  │  [VIEW] [MESSAGE]    │  │
│  │                                 │  │                      │  │
│  └─────────────────────────────────┘  └──────────────────────┘  │
│                                                                 │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │  ALERTS & CRITICAL EVENTS                                  │  │
│  │  🔴 AI101 — Risk HIGH, Swiss Cheese triggered, APPROACH    │  │
│  │  🟡 VT203 — Climbing through turbulence, load increasing   │  │
│  └────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

**Design language:**
- Background: Near-black (#0A0C10) with subtle radar grid lines (green, very low opacity)
- Radar sweep: Rotating green line with phosphor afterglow (CSS animation)
- Flight blips: Colored by risk level (green/yellow/orange/red dots)
- Flight strips: Dark cards with left-border color matching risk level
- Fonts: Orbitron for headers, Share Tech Mono for data, Rajdhani for body
- Alert bar: Scrolling red/amber ticker at the bottom

**Tech:** SVG radar display, or Leaflet/Mapbox for real map. Initially use SVG with simulated lat/lng positions. Can upgrade to real map tiles later.

**2.4.2 — ATC Flight Detail (`AtcFlightDetailPage.jsx`)**

When ATC clicks "VIEW" on a flight strip:

```
┌─────────────────────────────────────────────────────────────────┐
│  ← BACK TO RADAR          FLIGHT AI101 — B737-800 VT-AXZ       │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─── PILOT INFO ───┐  ┌─── RISK PANEL ───┐  ┌── SCENARIO ──┐  │
│  │ Cpt. Sharma       │  │ RISK: HIGH  ■■■░ │  │ Weather: FOG │  │
│  │ ALPHA-7            │  │ Load: 6.8 / 10  │  │ Night flight  │  │
│  │ EXPERIENCED        │  │ HR: 92 BPM      │  │ Wet runway    │  │
│  │ Fatigue: 45.2      │  │ Err prob: 42%   │  │ MODERATE      │  │
│  │ Stress: 62.1       │  │ Swiss: YES ⚠    │  │ No emergency  │  │
│  └────────────────────┘  └──────────────────┘  └──────────────┘  │
│                                                                 │
│  ┌─── FLIGHT PATH MAP ──────────────────────────────────────┐   │
│  │  VIDP ──●── WPT01 ──●── WPT02 ──●── [CURRENT] ── VABB   │   │
│  │                                    ▲ 12,000ft, 220kt     │   │
│  └───────────────────────────────────────────────────────────┘   │
│                                                                 │
│  ┌─── AI RECOMMENDATIONS ────────────┐  ┌─── RADIO LOG ─────┐  │
│  │ [CRITICAL] Execute Go-Around      │  │ 14:32:01 TOWER→   │  │
│  │ [WARNING] Engage Autopilot        │  │ "AI101, go around" │  │
│  │ [CAUTION] Execute Checklist       │  │                    │  │
│  └────────────────────────────────────┘  │ 14:32:05 PILOT→   │  │
│                                          │ "Going around"    │  │
│  ┌─── ATC ASSESSMENT ────────────────┐  │                    │  │
│  │ ☐ False Alarm  ☐ Monitoring       │  │ [Send Message]     │  │
│  │ ☐ Serious      ☐ Critical         │  └────────────────────┘  │
│  │ [SUBMIT ASSESSMENT]               │                          │
│  └────────────────────────────────────┘                          │
└─────────────────────────────────────────────────────────────────┘
```

**ATC Assessment options:**
- **FALSE ALARM** — Risk model over-reacted, no action needed
- **MONITORING** — Elevated but not yet dangerous, keep watching
- **SERIOUS** — Confirmed issue, may require intervention
- **CRITICAL** — Immediate action required, send radio message

Each assessment is logged to a new `atc_assessment` table.

### 2.5 — ATC Assessment Table

```
atc_assessment
├── id                  UUID (PK)
├── flight_session_id   UUID (FK → flight_sessions)
├── atc_user_id         UUID (FK → users)
├── assessment_type     ENUM('FALSE_ALARM','MONITORING','SERIOUS','CRITICAL')
├── notes               TEXT
├── risk_level_at_time  VARCHAR(10)     -- snapshot of model's risk level
├── cog_load_at_time    DOUBLE          -- snapshot of cognitive load
├── timestamp           TIMESTAMP
└── created_at          TIMESTAMP
```

---

## Phase 3 — Kafka Real-Time Messaging

> **Goal:** ATC ↔ Pilot bidirectional communication via Apache Kafka, displayed as a cockpit radio frequency log.

### 3.1 — Kafka Infrastructure

**Kafka topics:**

| Topic | Purpose | Key |
|-------|---------|-----|
| `atc-to-pilot.{sessionId}` | ATC sends message to specific pilot | sessionId |
| `pilot-to-atc.{sessionId}` | Pilot replies to ATC | sessionId |
| `broadcast.all` | ATC broadcast to all active flights | — |
| `system-alerts.{sessionId}` | Auto-generated alerts (risk escalation, Swiss Cheese trigger) | sessionId |

**Kafka config (new `kafka/` section in `application.yml`):**

```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: aipclm-backend
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
```

**Docker Compose for Kafka (new `docker-compose.yml`):**

```yaml
services:
  zookeeper:
    image: confluentinc/cp-zookeeper:7.6.0
    ports: ["2181:2181"]
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181

  kafka:
    image: confluentinc/cp-kafka:7.6.0
    ports: ["9092:9092"]
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
    depends_on: [zookeeper]
```

### 3.2 — Message Entity & DTO

**New `radio_message` table:**

```
radio_message
├── id                  UUID (PK)
├── flight_session_id   UUID (FK → flight_sessions)
├── sender_user_id      UUID (FK → users)         -- NULL for system-generated
├── sender_role         ENUM('PILOT','ATC','SYSTEM')
├── sender_call_sign    VARCHAR(20)
├── message_type        ENUM('TEXT','ALERT','ADVISORY','ACKNOWLEDGEMENT','MAYDAY','PAN_PAN')
├── content             TEXT NOT NULL
├── priority            ENUM('ROUTINE','PRIORITY','DISTRESS')
├── frequency           VARCHAR(10)               -- "121.5" for emergency, "118.3" for approach
├── acknowledged        BOOLEAN DEFAULT FALSE
├── acknowledged_at     TIMESTAMP
├── kafka_topic         VARCHAR(100)              -- which topic it was published to
├── kafka_partition     INT
├── kafka_offset        BIGINT
├── timestamp           TIMESTAMP NOT NULL        -- when the message was sent
├── created_at          TIMESTAMP
└── updated_at          TIMESTAMP
```

### 3.3 — Backend Kafka Services

**New files:**

| File | Purpose |
|------|---------|
| `messaging/model/RadioMessage.java` | JPA entity |
| `messaging/model/MessageType.java` | Enum |
| `messaging/model/MessagePriority.java` | Enum |
| `messaging/repository/RadioMessageRepository.java` | Database access |
| `messaging/dto/SendMessageRequest.java` | Incoming DTO (content, priority, frequency) |
| `messaging/dto/RadioMessageDto.java` | Outgoing DTO |
| `messaging/kafka/KafkaProducerService.java` | Publishes messages to Kafka topics |
| `messaging/kafka/KafkaConsumerService.java` | Consumes messages, persists to DB, pushes via WebSocket |
| `messaging/service/RadioCommsService.java` | Business logic — send, acknowledge, fetch history |
| `messaging/controller/RadioCommsController.java` | REST endpoints for sending and fetching messages |
| `messaging/websocket/RadioWebSocketHandler.java` | WebSocket for real-time push to browser |

### 3.4 — WebSocket for Live Push

Kafka alone doesn't push to the browser — we need WebSocket (or SSE) for the last mile:

```
ATC browser                                   Pilot browser
     │                                              │
     │  WebSocket /ws/radio/{sessionId}              │  WebSocket /ws/radio/{sessionId}
     │                                              │
     ▼                                              ▼
┌─────────────────────────────────────────────────────────┐
│                   Spring WebSocket Hub                   │
│     KafkaConsumer → WebSocket broadcast per session     │
└─────────────────────────────────────────────────────────┘
     ▲                                              ▲
     │     POST /api/radio/{sessionId}/send          │
     │     (ATC sends message)                       │
     └───────────────────────────────────────────────┘
```

**Flow:**
1. ATC types message → `POST /api/radio/{sessionId}/send`
2. Backend publishes to `atc-to-pilot.{sessionId}` Kafka topic
3. KafkaConsumer picks it up → saves to `radio_message` table → pushes via WebSocket
4. Pilot's browser receives via WebSocket → appears in cockpit radio display
5. Pilot can acknowledge or reply → reverse flow via `pilot-to-atc.{sessionId}`

### 3.5 — Auto-Generated System Alerts

When the risk engine detects certain conditions, it automatically publishes to `system-alerts.{sessionId}`:

| Trigger | Auto-Message | Priority |
|---------|-------------|----------|
| Risk escalated to HIGH | "ADVISORY: Cognitive load elevated, monitoring" | PRIORITY |
| Risk escalated to CRITICAL | "ALERT: Cognitive load CRITICAL — immediate assessment required" | DISTRESS |
| Swiss Cheese triggered | "ALERT: Multiple safety barriers breached" | DISTRESS |
| Emergency scenario active | "SQUAWK 7700 — {emergency_type} declared" | DISTRESS |
| Session completed (landing) | "Flight {flightNumber} landed safely. Session closed." | ROUTINE |

### 3.6 — Frontend: Radio Comm Panel

**For Pilot (DashboardPage):** A new collapsible radio panel at the bottom of the cockpit view:

```
┌─────────────────────────────────────────────────────────────┐
│  📻 RADIO COMMS — FREQ 118.3                    ▼ COLLAPSE  │
│─────────────────────────────────────────────────────────────│
│  14:32:01  TOWER-1 → "AI101, descend and maintain FL120"   │
│  14:32:05  ALPHA-7 → "Descending FL120, AI101"             │
│  14:35:12  SYSTEM  → "⚠ ALERT: Cognitive load CRITICAL"    │
│  14:35:14  TOWER-1 → "AI101, go around, acknowledge"       │
│  14:35:18  ALPHA-7 → "Going around, AI101"                 │
│─────────────────────────────────────────────────────────────│
│  [__Type message...__________________________] [SEND] [ACK] │
└─────────────────────────────────────────────────────────────┘
```

**For ATC (AtcFlightDetailPage):** Same radio log but with additional controls:
- Message type selector (TEXT, ADVISORY, ALERT)
- Priority selector (ROUTINE, PRIORITY, DISTRESS)
- Frequency selector (approach, tower, ground, emergency 121.5)
- Broadcast button (send to all flights)

---

## Phase 4 — Black Box Flight Recorder

> **Goal:** Every piece of data from a flight session is permanently logged in an immutable, timestamped audit trail — like a real aircraft's FDR/CVR.

### 4.1 — Black Box Model

**New `black_box_entry` table:**

```
black_box_entry
├── id                  BIGSERIAL (PK)            -- sequential, not UUID (for ordering)
├── flight_session_id   UUID (FK → flight_sessions)
├── entry_type          ENUM('TELEMETRY','COGNITIVE','RISK','RECOMMENDATION',
│                             'RADIO_MESSAGE','ATC_ASSESSMENT','SCENARIO_CHANGE',
│                             'PHASE_CHANGE','SYSTEM_EVENT')
├── severity            ENUM('INFO','WARNING','CRITICAL')
├── summary             VARCHAR(500)              -- human-readable one-liner
├── payload_json        JSONB                     -- full serialized data snapshot
├── source_entity_id    UUID                      -- FK to the source record
├── timestamp           TIMESTAMP NOT NULL
├── created_at          TIMESTAMP
```

**Index:** `(flight_session_id, timestamp)` for fast sequential reads.

### 4.2 — Recording Logic

Implement a `BlackBoxRecorderService` that is called:
- After every `TelemetryFrame` save → log telemetry snapshot
- After every `CognitiveState` save → log cognitive state
- After every `RiskAssessment` save → log risk + whether it escalated
- After every `AIRecommendation` save → log recommendations
- After every `RadioMessage` save → log the message
- After every `AtcAssessment` save → log the assessment
- On phase change → log phase transition
- On scenario modification → log the change

### 4.3 — Black Box Replay UI

**New page: `BlackBoxReplayPage.jsx` — `/atc/blackbox/:sessionId`**

A timeline-based replay interface:

```
┌─────────────────────────────────────────────────────────────────┐
│  BLACK BOX RECORDER — FLIGHT AI101                              │
│  B737-800 · VT-AXZ · VIDP → VABB · 2026-03-02                 │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ━━━━━━━━━━━━━━━━━━━━●━━━━━━━━━━━━━━━━━━━━━━━━━━━              │
│  TAKEOFF   CLIMB   CRUISE   DESCENT  APPROACH  LAND            │
│                       ▲ currently viewing                       │
│                                                                 │
│  ┌── EVENT LOG ──────────────────────────────────────────────┐  │
│  │ 14:00:00  [SYSTEM]   Flight started — TAKEOFF             │  │
│  │ 14:00:02  [TELEM]    ALT: 500ft, SPD: 160kt, HR: 78      │  │
│  │ 14:00:02  [COGN]     Expert: 22.3, ML: 25.1, Risk: LOW   │  │
│  │ 14:05:30  [PHASE]    Phase changed: TAKEOFF → CLIMB       │  │
│  │ 14:15:00  [RISK]     ⚠ Risk escalated: LOW → MEDIUM       │  │
│  │ 14:15:01  [REC]      ENGAGE_AUTOPILOT (WARNING)           │  │
│  │ 14:15:05  [RADIO]    TOWER→AI101: "Engage autopilot"      │  │
│  │ 14:15:08  [RADIO]    AI101→TOWER: "Autopilot engaged"     │  │
│  │ 14:20:00  [ALERT]    🔴 Swiss Cheese model triggered      │  │
│  │ 14:20:01  [ATC]      Assessment: SERIOUS by TOWER-1       │  │
│  │ ...                                                        │  │
│  └────────────────────────────────────────────────────────────┘  │
│                                                                 │
│  Filters: [ALL] [RADIO] [RISK] [SYSTEM]     [EXPORT CSV]       │
└─────────────────────────────────────────────────────────────────┘
```

---

## Phase 5 — UI/UX Theme & Polish

### 5.1 — ATC Theme (distinct from Pilot cockpit theme)

| Element | Pilot Theme | ATC Theme |
|---------|------------|-----------|
| **Background** | Cockpit PNG overlay, dark | Dark military command center (#0A0C10), grid lines |
| **Primary color** | Green (#00FF41) | Amber (#FFB800) — classic ATC radar color |
| **Accent** | Cyan (#00C2FF) | Teal (#00CED1) |
| **Danger** | Red (#FF3333) | Same Red (#FF3333) |
| **Typography** | Same Orbitron / Rajdhani / Share Tech Mono stack |
| **Feel** | Individual cockpit instruments | Command center with multiple displays |
| **Animations** | Gauge needles, sparklines | Radar sweep, blip pulsing, alert flashing |

### 5.2 — Radar Sweep Animation (CSS)

```css
.radar-sweep {
  background: conic-gradient(
    from 0deg,
    transparent 0deg,
    rgba(255, 184, 0, 0.15) 30deg,
    transparent 60deg
  );
  animation: radar-rotate 4s linear infinite;
}

@keyframes radar-rotate {
  from { transform: rotate(0deg); }
  to   { transform: rotate(360deg); }
}
```

### 5.3 — Sound Design (optional)

| Event | Sound |
|-------|-------|
| New message received | Radio static click |
| Risk escalated to CRITICAL | Alert klaxon (2 beeps) |
| Swiss Cheese triggered | Continuous warning tone |
| ATC broadcast | "Attention all aircraft" chime |

Implement via Web Audio API, optional toggle in settings.

### 5.4 — Login Page Role Selection

Redesigned login with role tabs:

```
┌──────────────────────────────────────────┐
│           AIPCLM — AUTHENTICATE          │
│                                          │
│   [ PILOT ]              [ ATC ]         │
│   ─────────              ─────           │
│                                          │
│   Email:    [_________________________]  │
│   Password: [_________________________]  │
│   Call Sign: [________________________]  │
│                                          │
│   [         AUTHENTICATE          ]      │
│                                          │
│   Don't have an account? [Register]      │
└──────────────────────────────────────────┘
```

After login:
- **PILOT** → redirected to `/home` (existing pilot interface)
- **ATC** → redirected to `/atc` (new ATC radar dashboard)

---

## Security Considerations

| Concern | Mitigation |
|---------|-----------|
| **Authentication** | BCrypt password hashing, JWT with HMAC-SHA256, 24h expiry, refresh token rotation |
| **Authorization** | Role-based access: `/api/atc/**` requires ATC role, `/api/test/**` requires ADMIN, all others require authenticated |
| **Kafka** | SASL/SCRAM authentication in production, plain for development |
| **WebSocket** | JWT token passed as query param on connection, validated server-side before subscription |
| **CORS** | Already configured; add ATC frontend port if different |
| **Input validation** | `@Valid` on all DTOs, message content sanitized (no XSS), max 1000 chars |
| **Rate limiting** | Spring Boot rate limiter on message send (max 30 msgs/min per user) |
| **Black box immutability** | `black_box_entry` table: INSERT-only, no UPDATE/DELETE permissions for app user in production |
| **Audit trail** | Every ATC assessment, message, and scenario change is logged |

---

## Tech Stack Additions

### Backend (new Maven dependencies)

```xml
<!-- JWT -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.5</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.5</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.5</version>
    <scope>runtime</scope>
</dependency>

<!-- Kafka -->
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
</dependency>

<!-- WebSocket -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-websocket</artifactId>
</dependency>
```

### Frontend (new npm packages)

```json
{
  "stomp-js": "^7.0.0",          // WebSocket client (STOMP protocol)
  "sockjs-client": "^1.6.1",     // SockJS fallback
  "leaflet": "^1.9.4",           // Map library (for radar view)
  "react-leaflet": "^4.2.1",     // React bindings for Leaflet
  "jwt-decode": "^4.0.0",        // Decode JWT for role extraction
  "react-hot-toast": "^2.5.2"    // Toast notifications for messages
}
```

### Infrastructure

| Component | Development | Production |
|-----------|------------|-----------|
| **Apache Kafka** | Docker Compose (Confluent images) | Managed Kafka (AWS MSK / Confluent Cloud) |
| **Zookeeper** | Docker Compose bundled | Managed (or KRaft mode) |
| **PostgreSQL** | Local service (already running) | RDS / Cloud SQL |

---

## Database Schema (New/Modified Tables)

### New Tables (6)

| Table | Rows (est.) | Key Relations |
|-------|------------|---------------|
| `users` | 10s | → `pilots` (via `user_id` FK) |
| `aircraft` | 10s | → `flight_sessions` (via `aircraft_id` FK) |
| `flight_scenario` | = sessions | 1:1 with `flight_sessions` |
| `flight_waypoint` | 5–10 per session | → `flight_sessions` |
| `radio_message` | 100s per session | → `flight_sessions`, → `users` |
| `black_box_entry` | 1000s per session | → `flight_sessions` |
| `atc_assessment` | few per session | → `flight_sessions`, → `users` |

### Modified Tables (2)

| Table | New Columns |
|-------|------------|
| `pilots` | `+ user_id` (FK → users) |
| `flight_sessions` | `+ aircraft_id, departure_icao, arrival_icao, flight_number, squawk_code, current_lat, current_lng, current_heading` |

---

## API Endpoint Plan

### Auth (Phase 0)

| Method | Endpoint | Access | Purpose |
|--------|----------|--------|---------|
| `POST` | `/api/auth/register` | Public | Create account |
| `POST` | `/api/auth/login` | Public | Get JWT token |
| `GET` | `/api/auth/me` | Authenticated | Get current user profile |

### Scenario (Phase 1)

| Method | Endpoint | Access | Purpose |
|--------|----------|--------|---------|
| `POST` | `/api/scenario` | Pilot | Create scenario for new session |
| `GET` | `/api/scenario/{sessionId}` | Any auth | Get scenario for a session |
| `PUT` | `/api/scenario/{sessionId}` | Pilot | Update scenario mid-flight |

### ATC (Phase 2)

| Method | Endpoint | Access | Purpose |
|--------|----------|--------|---------|
| `GET` | `/api/atc/flights` | ATC | List all active flights |
| `GET` | `/api/atc/flights/{id}/detail` | ATC | Full flight details |
| `GET` | `/api/atc/flights/{id}/path` | ATC | Flight plan waypoints |
| `POST` | `/api/atc/flights/{id}/assess` | ATC | Submit risk assessment |
| `GET` | `/api/atc/statistics` | ATC | Summary dashboard stats |

### Radio Comms (Phase 3)

| Method | Endpoint | Access | Purpose |
|--------|----------|--------|---------|
| `POST` | `/api/radio/{sessionId}/send` | Any auth | Send message |
| `GET` | `/api/radio/{sessionId}/history` | Any auth | Get message history |
| `PUT` | `/api/radio/{sessionId}/acknowledge/{msgId}` | Any auth | Mark message as acknowledged |
| `POST` | `/api/radio/broadcast` | ATC | Broadcast to all flights |
| `WS` | `/ws/radio/{sessionId}` | Any auth | Live message stream |

### Black Box (Phase 4)

| Method | Endpoint | Access | Purpose |
|--------|----------|--------|---------|
| `GET` | `/api/blackbox/{sessionId}` | ATC | Get full black box log |
| `GET` | `/api/blackbox/{sessionId}/export` | ATC | Download as CSV/JSON |

---

## Frontend Route Plan

### Pilot Routes (existing + modified)

| Route | Page | New/Modified |
|-------|------|-------------|
| `/` | Landing | Unchanged |
| `/login` | Login | **Modified** — real auth, role tabs |
| `/register` | Register | **New** |
| `/home` | Control Center | **Modified** — scenario panel |
| `/dashboard/:sessionId` | Cockpit Dashboard | **Modified** — radio panel added |
| `/analytics/:sessionId` | Analytics | Unchanged |

### ATC Routes (all new)

| Route | Page | Description |
|-------|------|-------------|
| `/atc` | ATC Radar Dashboard | Radar view + flight strips |
| `/atc/flight/:sessionId` | Flight Detail | Deep drill-down on one flight |
| `/atc/messages` | Message Center | All radio communications |
| `/atc/blackbox/:sessionId` | Black Box Replay | Timeline event log |

### Layout Structure

```
<BrowserRouter>
  <AuthProvider>
    <SessionProvider>
      <Routes>
        {/* Public */}
        <Route element={<PublicLayout />}>
          <Route path="/" element={<LandingPage />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
        </Route>

        {/* Pilot */}
        <Route element={<ProtectedLayout requiredRole="PILOT" />}>
          <Route path="/home" element={<HomePage />} />
          <Route path="/dashboard/:sessionId" element={<DashboardPage />} />
          <Route path="/analytics/:sessionId" element={<AnalyticsPage />} />
        </Route>

        {/* ATC */}
        <Route element={<AtcLayout requiredRole="ATC" />}>
          <Route path="/atc" element={<AtcRadarPage />} />
          <Route path="/atc/flight/:sessionId" element={<AtcFlightDetailPage />} />
          <Route path="/atc/messages" element={<AtcMessageCenterPage />} />
          <Route path="/atc/blackbox/:sessionId" element={<BlackBoxReplayPage />} />
        </Route>
      </Routes>
    </SessionProvider>
  </AuthProvider>
</BrowserRouter>
```

---

## File-Level Implementation Checklist

### Phase 0 — Auth Foundation
- [ ] `auth/model/User.java`
- [ ] `auth/model/UserRole.java`
- [ ] `auth/repository/UserRepository.java`
- [ ] `auth/dto/RegisterRequest.java`
- [ ] `auth/dto/LoginRequest.java`
- [ ] `auth/dto/AuthResponse.java`
- [ ] `auth/service/AuthService.java`
- [ ] `auth/controller/AuthController.java`
- [ ] `auth/security/JwtTokenProvider.java`
- [ ] `auth/security/JwtAuthFilter.java`
- [ ] `config/SecurityConfig.java`
- [ ] Modify `pilots` table — add `user_id` FK
- [ ] Seed data — default pilot + ATC users
- [ ] Frontend: `context/AuthContext.jsx`
- [ ] Frontend: Modify `services/api.js` — JWT interceptor
- [ ] Frontend: Rewrite `LoginPage.jsx`
- [ ] Frontend: Create `RegisterPage.jsx`
- [ ] Frontend: Modify `ProtectedRoute.jsx` — role checking
- [ ] Frontend: Modify `AppRouter.jsx` — new routes

### Phase 1 — Scenario Engine
- [ ] `scenario/model/FlightScenario.java` + all enums
- [ ] `scenario/repository/FlightScenarioRepository.java`
- [ ] `scenario/dto/ScenarioRequest.java`
- [ ] `scenario/service/ScenarioService.java`
- [ ] `scenario/controller/ScenarioController.java`
- [ ] Modify `SimulationEngineService.java` — scenario modifiers
- [ ] Modify `RiskEngineService.java` — scenario risk multipliers
- [ ] Modify `RecommendationEngineService.java` — new rules
- [ ] Frontend: `components/ScenarioConfigurator.jsx`
- [ ] Frontend: Modify `HomePage.jsx` — embed configurator

### Phase 2 — ATC Role
- [ ] `aircraft/model/Aircraft.java`
- [ ] `aircraft/repository/AircraftRepository.java`
- [ ] Modify `FlightSession.java` — add aircraft, ICAO, position fields
- [ ] `flightpath/model/FlightWaypoint.java`
- [ ] `flightpath/repository/FlightWaypointRepository.java`
- [ ] `atc/model/AtcAssessment.java`
- [ ] `atc/repository/AtcAssessmentRepository.java`
- [ ] `atc/dto/AtcFlightSummaryDto.java`
- [ ] `atc/dto/AtcFlightDetailDto.java`
- [ ] `atc/service/AtcService.java`
- [ ] `atc/controller/AtcController.java`
- [ ] Modify simulation — generate lat/lng positions per frame
- [ ] Frontend: `layouts/AtcLayout.jsx`
- [ ] Frontend: `pages/AtcRadarPage.jsx`
- [ ] Frontend: `pages/AtcFlightDetailPage.jsx`
- [ ] Frontend: `components/RadarDisplay.jsx`
- [ ] Frontend: `components/FlightStrip.jsx`
- [ ] Frontend: CSS — ATC theme (amber radar)

### Phase 3 — Kafka Messaging
- [ ] `docker-compose.yml` — Kafka + Zookeeper
- [ ] `pom.xml` — add Kafka + WebSocket deps
- [ ] `messaging/model/RadioMessage.java` + enums
- [ ] `messaging/repository/RadioMessageRepository.java`
- [ ] `messaging/dto/SendMessageRequest.java`
- [ ] `messaging/dto/RadioMessageDto.java`
- [ ] `messaging/kafka/KafkaProducerService.java`
- [ ] `messaging/kafka/KafkaConsumerService.java`
- [ ] `messaging/service/RadioCommsService.java`
- [ ] `messaging/controller/RadioCommsController.java`
- [ ] `messaging/websocket/RadioWebSocketHandler.java`
- [ ] `config/WebSocketConfig.java`
- [ ] `config/KafkaConfig.java`
- [ ] Modify `RiskEngineService.java` — auto-publish alerts
- [ ] Frontend: `components/RadioCommPanel.jsx`
- [ ] Frontend: `hooks/useWebSocket.js`
- [ ] Frontend: Modify `DashboardPage.jsx` — embed radio panel
- [ ] Frontend: Modify `AtcFlightDetailPage.jsx` — embed radio panel

### Phase 4 — Black Box
- [ ] `blackbox/model/BlackBoxEntry.java` + enums
- [ ] `blackbox/repository/BlackBoxRepository.java`
- [ ] `blackbox/service/BlackBoxRecorderService.java`
- [ ] `blackbox/controller/BlackBoxController.java`
- [ ] Wire recorder into all existing services
- [ ] Frontend: `pages/BlackBoxReplayPage.jsx`
- [ ] Frontend: `components/EventTimeline.jsx`

### Phase 5 — UI Polish
- [ ] ATC theme CSS (`atc-theme.css`)
- [ ] Radar sweep animation
- [ ] Alert sound effects (Web Audio API)
- [ ] Responsive adjustments
- [ ] Loading skeletons for all new pages

---

## Estimated Timeline

| Phase | Scope | Est. Duration | Dependencies |
|-------|-------|--------------|-------------|
| **Phase 0** | Auth + JWT + roles | 2–3 days | None |
| **Phase 1** | Scenario engine | 2–3 days | Phase 0 (for protected routes) |
| **Phase 2** | ATC dashboard | 4–5 days | Phase 0 + Phase 1 |
| **Phase 3** | Kafka messaging | 3–4 days | Phase 0 + Phase 2 |
| **Phase 4** | Black box recorder | 2–3 days | Phase 3 |
| **Phase 5** | UI polish & testing | 2–3 days | All phases |
| **Total** | | **~15–20 days** | |

---

## Implementation Order (Recommended)

```
Phase 0 (Auth)
    │
    ├──→ Phase 1 (Scenarios)
    │        │
    │        ▼
    │    Phase 2 (ATC Dashboard)
    │        │
    │        ▼
    │    Phase 3 (Kafka Messaging)
    │        │
    │        ▼
    │    Phase 4 (Black Box)
    │
    └──→ Phase 5 (UI Polish — parallel with Phase 3/4)
```

Start with **Phase 0** immediately — everything else depends on real authentication. Phase 1 and Phase 2 can partially overlap since scenario is backend-heavy and ATC is frontend-heavy.

---

*This document serves as the single source of truth for the AIPCLM v2.0 expansion. Each phase can be greenlit independently. Refer to the file-level checklist for implementation tracking.*
