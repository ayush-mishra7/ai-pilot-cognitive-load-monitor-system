package com.aipclm.system.simulation.web;

import com.aipclm.system.cognitive.model.CognitiveState;
import com.aipclm.system.cognitive.repository.CognitiveStateRepository;
import com.aipclm.system.cognitive.service.CognitiveLoadService;
import com.aipclm.system.pilot.model.Pilot;
import com.aipclm.system.pilot.model.PilotProfileType;
import com.aipclm.system.pilot.repository.PilotRepository;
import com.aipclm.system.recommendation.model.AIRecommendation;
import com.aipclm.system.recommendation.service.RecommendationEngineService;
import com.aipclm.system.risk.model.RiskAssessment;
import com.aipclm.system.risk.repository.RiskAssessmentRepository;
import com.aipclm.system.risk.service.RiskEngineService;
import com.aipclm.system.session.model.FlightSession;
import com.aipclm.system.session.model.FlightSessionStatus;
import com.aipclm.system.session.model.FlightSessionStatus;
import com.aipclm.system.session.repository.FlightSessionRepository;
import com.aipclm.system.simulation.service.SimulationEngineService;
import com.aipclm.system.simulation.service.SimulationOrchestratorService;
import com.aipclm.system.simulation.service.SimulationSchedulerService;
import com.aipclm.system.telemetry.model.TelemetryFrame;
import com.aipclm.system.telemetry.repository.TelemetryFrameRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/test/simulation")
public class SessionTestController {

        private final SimulationEngineService simulationEngineService;
        private final FlightSessionRepository flightSessionRepository;
        private final PilotRepository pilotRepository;
        private final TelemetryFrameRepository telemetryFrameRepository;
        private final CognitiveLoadService cognitiveLoadService;
        private final CognitiveStateRepository cognitiveStateRepository;
        private final RiskEngineService riskEngineService;
        private final RecommendationEngineService recommendationEngineService;
        private final RiskAssessmentRepository riskAssessmentRepository;
        private final SimulationOrchestratorService simulationOrchestratorService;
        private final SimulationSchedulerService simulationSchedulerService;

        public SessionTestController(SimulationEngineService simulationEngineService,
                        FlightSessionRepository flightSessionRepository,
                        PilotRepository pilotRepository,
                        TelemetryFrameRepository telemetryFrameRepository,
                        CognitiveLoadService cognitiveLoadService,
                        CognitiveStateRepository cognitiveStateRepository,
                        RiskEngineService riskEngineService,
                        RecommendationEngineService recommendationEngineService,
                        RiskAssessmentRepository riskAssessmentRepository,
                        SimulationOrchestratorService simulationOrchestratorService,
                        SimulationSchedulerService simulationSchedulerService) {
                this.simulationEngineService = simulationEngineService;
                this.flightSessionRepository = flightSessionRepository;
                this.pilotRepository = pilotRepository;
                this.telemetryFrameRepository = telemetryFrameRepository;
                this.cognitiveLoadService = cognitiveLoadService;
                this.cognitiveStateRepository = cognitiveStateRepository;
                this.riskEngineService = riskEngineService;
                this.recommendationEngineService = recommendationEngineService;
                this.riskAssessmentRepository = riskAssessmentRepository;
                this.simulationOrchestratorService = simulationOrchestratorService;
                this.simulationSchedulerService = simulationSchedulerService;
        }

        @PostMapping("/start")
        public ResponseEntity<UUID> startSession(@RequestParam(defaultValue = "NOVICE") PilotProfileType profileType) {
                Pilot pilot = Pilot.builder()
                                .fullName("Test Pilot " + profileType)
                                .profileType(profileType)
                                .baselineStressSensitivity(1.0)
                                .baselineFatigueRate(1.0)
                                .build();
                pilot = pilotRepository.save(pilot);
                FlightSession session = FlightSession.builder()
                                .pilot(pilot)
                                .sessionStartTime(Instant.now())
                                .status(FlightSessionStatus.RUNNING)
                                .build();
                session = flightSessionRepository.save(session);
                return ResponseEntity.ok(session.getId());
        }

        @PostMapping("/{sessionId}/generate-frame")
        public ResponseEntity<String> generateFrame(@PathVariable UUID sessionId) {
                simulationEngineService.generateNextFrame(sessionId);
                FlightSession session = flightSessionRepository.findById(sessionId).orElseThrow();
                return ResponseEntity.ok("Generated frame " + session.getTotalFramesGenerated());
        }

        @PostMapping("/{sessionId}/generate-frames-batch")
        public ResponseEntity<String> generateFramesBatch(@PathVariable UUID sessionId,
                        @RequestParam(defaultValue = "10") int count) {
                for (int i = 0; i < count; i++)
                        simulationEngineService.generateNextFrame(sessionId);
                FlightSession session = flightSessionRepository.findById(sessionId).orElseThrow();
                return ResponseEntity.ok("Generated " + count + " frames. Total: " + session.getTotalFramesGenerated());
        }

        @PostMapping("/{sessionId}/simulate-step")
        public ResponseEntity<SimulationOrchestratorService.SimulationStepResult> simulateStep(
                        @PathVariable UUID sessionId) {
                SimulationOrchestratorService.SimulationStepResult result = simulationOrchestratorService
                                .runSingleSimulationStep(sessionId);
                return ResponseEntity.ok(result);
        }

        @PostMapping("/{sessionId}/start-schedule")
        public ResponseEntity<String> startSchedule(@PathVariable UUID sessionId) {
                FlightSession session = flightSessionRepository.findById(sessionId).orElseThrow();
                if (session.getStatus() != FlightSessionStatus.RUNNING) {
                        session.setStatus(FlightSessionStatus.RUNNING);
                        flightSessionRepository.save(session);
                }
                simulationSchedulerService.startSession(sessionId);
                return ResponseEntity.ok("Scheduler started for session " + sessionId);
        }

        @PostMapping("/{sessionId}/stop-schedule")
        public ResponseEntity<String> stopSchedule(@PathVariable UUID sessionId) {
                simulationSchedulerService.stopSession(sessionId);
                return ResponseEntity.ok("Scheduler stopped for session " + sessionId);
        }

        /**
         * Full Digital Co-Pilot Pipeline: Simulate → Expert CLI → ML → Risk →
         * Recommendations
         */
        @PostMapping("/{sessionId}/full-pipeline-batch")
        public ResponseEntity<String> fullPipelineBatch(@PathVariable UUID sessionId,
                        @RequestParam(defaultValue = "5") int count) {
                StringBuilder result = new StringBuilder();
                result.append(String.format("%-6s %-10s %-8s %-8s %-10s %-8s %s%n",
                                "Frame", "Phase", "Expert", "ML", "Risk", "Swiss", "Recommendations"));
                result.append("-".repeat(90)).append("\n");

                for (int i = 0; i < count; i++) {
                        simulationEngineService.generateNextFrame(sessionId);

                        TelemetryFrame frame = telemetryFrameRepository
                                        .findTopByFlightSessionIdOrderByFrameNumberDesc(sessionId).orElseThrow();

                        cognitiveLoadService.computeCognitiveLoad(frame.getId());

                        CognitiveState cogState = cognitiveStateRepository
                                        .findTop5BySessionIdOrderByTimestampDesc(sessionId).stream().findFirst()
                                        .orElseThrow();

                        RiskAssessment risk = riskEngineService.evaluateRisk(cogState.getId());

                        List<AIRecommendation> recs = recommendationEngineService.generateRecommendations(risk.getId());

                        String recNames = recs.stream()
                                        .map(r -> r.getRecommendationType().name() + "(" + r.getSeverity() + ")")
                                        .toList().toString();

                        result.append(String.format("%-6d %-10s %-8.1f %-8.1f %-10s %-8s %s%n",
                                        frame.getFrameNumber(),
                                        frame.getPhaseOfFlight(),
                                        cogState.getExpertComputedLoad(),
                                        cogState.getMlPredictedLoad(),
                                        risk.getRiskLevel(),
                                        risk.isSwissCheeseTriggered() ? "YES" : "no",
                                        recNames));
                }
                return ResponseEntity.ok(result.toString());
        }

        /**
         * Synthetic rule-verification endpoint.
         * Directly injects synthetic TelemetryFrame + CognitiveState + RiskAssessment
         * records
         * for each recommendation scenario and runs the recommendation engine to verify
         * all rules fire.
         * Does NOT involve the simulation engine.
         */
        @PostMapping("/{sessionId}/test-rules")
        public ResponseEntity<String> testRules(@PathVariable UUID sessionId) {
                FlightSession session = flightSessionRepository.findById(sessionId).orElseThrow();
                StringBuilder result = new StringBuilder("=== Recommendation Rule Verification ===\n\n");

                // Scenario definitions: [label, phase, autopilot, vertSpeedInstability,
                // missedChecklistProb, swissCheese, riskLevel,
                // mlLoad, errorProb, confidence]
                Object[][] scenarios = {
                                // Rule 1: HIGH + autopilot false → ENGAGE_AUTOPILOT
                                { "HIGH+NoAutopilot", "APPROACH", false, 50.0, 0.1, false,
                                                com.aipclm.system.cognitive.model.RiskLevel.HIGH, 65.0, 0.65, 0.85 },
                                // Rule 2: DESCENT + high vertSpeedInstability → STABILIZE_DESCENT
                                { "DESCENT+Unstable", "DESCENT", true, 300.0, 0.1, false,
                                                com.aipclm.system.cognitive.model.RiskLevel.HIGH, 50.0, 0.50, 0.85 },
                                // Rule 3: missedChecklistProbability > 0.5 → EXECUTE_CHECKLIST
                                { "HighChecklistProb", "CRUISE", true, 30.0, 0.6, false,
                                                com.aipclm.system.cognitive.model.RiskLevel.MEDIUM, 35.0, 0.35, 0.85 },
                                // Rule 4: Swiss Cheese → REDUCE_TASK_SWITCHING
                                { "SwissCheese", "LANDING", false, 200.0, 0.3, true,
                                                com.aipclm.system.cognitive.model.RiskLevel.HIGH, 75.0, 0.75, 0.85 },
                                // Rule 5: CRITICAL → GO_AROUND
                                { "CRITICAL", "LANDING", false, 200.0, 0.1, false,
                                                com.aipclm.system.cognitive.model.RiskLevel.CRITICAL, 90.0, 0.90,
                                                0.85 },
                };

                int frameNum = 9000;
                for (Object[] s : scenarios) {
                        String label = (String) s[0];
                        String phaseStr = (String) s[1];
                        boolean autopilot = (boolean) s[2];
                        double vsi = (double) s[3];
                        double missedProb = (double) s[4];
                        boolean swiss = (boolean) s[5];
                        com.aipclm.system.cognitive.model.RiskLevel rl = (com.aipclm.system.cognitive.model.RiskLevel) s[6];
                        double mlLoad = (double) s[7];
                        double errorProb = (double) s[8];
                        double confidence = (double) s[9];

                        com.aipclm.system.telemetry.model.PhaseOfFlight phase = com.aipclm.system.telemetry.model.PhaseOfFlight
                                        .valueOf(phaseStr);

                        // Persist synthetic frame
                        TelemetryFrame frame = telemetryFrameRepository.save(TelemetryFrame.builder()
                                        .flightSession(session)
                                        .frameNumber(frameNum++)
                                        .timestamp(Instant.now())
                                        .phaseOfFlight(phase)
                                        .autopilotEngaged(autopilot)
                                        .verticalSpeedInstability(vsi)
                                        .verticalSpeed(-1800.0)
                                        .fatigueIndex(swiss ? 70.0 : 20.0)
                                        .errorCount(swiss ? 4 : 0)
                                        .turbulenceLevel(swiss ? 0.3 : 0.05)
                                        .stressIndex(50.0)
                                        .reactionTimeMs(450)
                                        .heartRate(85.0)
                                        .blinkRate(14.0)
                                        .checklistDelaySeconds(missedProb > 0.5 ? 25.0 : 5.0)
                                        .taskSwitchRate(3.0)
                                        .controlJitterIndex(0.3)
                                        .altitude(3000.0)
                                        .airspeed(150.0)
                                        .heading(270.0)
                                        .build());

                        // Persist synthetic cognitive state
                        com.aipclm.system.cognitive.model.CognitiveState cogState = cognitiveStateRepository
                                        .save(com.aipclm.system.cognitive.model.CognitiveState.builder()
                                                        .telemetryFrame(frame)
                                                        .expertComputedLoad(mlLoad * 0.9)
                                                        .mlPredictedLoad(mlLoad)
                                                        .errorProbability(errorProb)
                                                        .confidenceScore(confidence)
                                                        .smoothedLoad(0.0)
                                                        .fatigueTrendSlope(0.0)
                                                        .swissCheeseAlignmentScore(0.0)
                                                        .advisoryGenerated(false)
                                                        .riskLevel(rl)
                                                        .timestamp(Instant.now())
                                                        .build());

                        // Compute missed checklist probability inline (override via RiskAssessment
                        // builder directly)
                        double aggScore = Math.min(100, mlLoad * 0.6 + errorProb * 100 * 0.4);
                        double delayedR = Math.min(1, mlLoad * 0.008);
                        double unsafeDescent = Math.min(1, mlLoad * 0.005);

                        com.aipclm.system.risk.model.RiskAssessment riskAssessment = riskAssessmentRepository
                                        .save(com.aipclm.system.risk.model.RiskAssessment.builder()
                                                        .cognitiveState(cogState)
                                                        .riskLevel(rl)
                                                        .aggregatedRiskScore(aggScore)
                                                        .delayedReactionProbability(delayedR)
                                                        .unsafeDescentProbability(unsafeDescent)
                                                        .missedChecklistProbability(missedProb)
                                                        .riskEscalated(rl.ordinal() > 0)
                                                        .swissCheeseTriggered(swiss)
                                                        .timestamp(Instant.now())
                                                        .build());

                        List<AIRecommendation> recs = recommendationEngineService
                                        .generateRecommendations(riskAssessment.getId());
                        String recStr = recs.stream()
                                        .map(r -> r.getRecommendationType().name() + "(" + r.getSeverity() + ")")
                                        .toList().toString();

                        result.append(String.format("%-20s | Risk=%-9s | Swiss=%-5s | %s%n",
                                        label, rl, swiss, recStr));
                }

                return ResponseEntity.ok(result.toString());
        }

        /** CLI-only batch (no risk/recommendation). */
        @PostMapping("/{sessionId}/generate-and-compute-batch")
        public ResponseEntity<String> generateAndComputeBatch(@PathVariable UUID sessionId,
                        @RequestParam(defaultValue = "10") int count) {
                StringBuilder result = new StringBuilder();
                for (int i = 0; i < count; i++) {
                        simulationEngineService.generateNextFrame(sessionId);
                        TelemetryFrame frame = telemetryFrameRepository
                                        .findTopByFlightSessionIdOrderByFrameNumberDesc(sessionId).orElseThrow();
                        double cli = cognitiveLoadService.computeCognitiveLoad(frame.getId());
                        result.append(String.format("Frame=%d Phase=%s CLI=%.2f%n",
                                        frame.getFrameNumber(), frame.getPhaseOfFlight(), cli));
                }
                return ResponseEntity.ok(result.toString());
        }
}
