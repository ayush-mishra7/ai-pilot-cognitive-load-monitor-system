package com.aipclm.system.recommendation.model;

public enum RecommendationType {
    ENGAGE_AUTOPILOT,
    STABILIZE_DESCENT,
    EXECUTE_CHECKLIST,
    REDUCE_TASK_SWITCHING,
    GO_AROUND,
    MONITOR_ONLY,
    // Scenario-aware recommendations (Phase 1)
    REQUEST_ILS_APPROACH,
    DIVERT_TO_ALTERNATE,
    GO_AROUND_WEATHER,
    DELAY_TAKEOFF,
    SQUAWK_7700
}
