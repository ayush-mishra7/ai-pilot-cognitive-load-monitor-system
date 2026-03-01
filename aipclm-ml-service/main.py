import logging
import uvicorn
from fastapi import FastAPI
from pydantic import BaseModel, Field
from enum import Enum

# Logging setup
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s - %(message)s"
)
logger = logging.getLogger("aipclm-ml-service")


app = FastAPI(
    title="AIPCLM ML Prediction Service",
    description="Cognitive Load ML Prediction endpoint for the AI-PCLM system",
    version="1.0.0"
)


class PhaseOfFlight(str, Enum):
    TAKEOFF = "TAKEOFF"
    CLIMB = "CLIMB"
    CRUISE = "CRUISE"
    DESCENT = "DESCENT"
    APPROACH = "APPROACH"
    LANDING = "LANDING"


class PredictionRequest(BaseModel):
    expertComputedLoad: float = Field(..., ge=0.0, le=100.0, description="Expert-computed cognitive load (0-100)")
    reactionTimeMs: int = Field(..., ge=0, description="Pilot reaction time in milliseconds")
    turbulenceLevel: float = Field(..., ge=0.0, le=1.0, description="Turbulence level (0.0 = calm, 1.0 = severe)")
    stressIndex: float = Field(..., ge=0.0, le=100.0, description="Pilot stress index (0-100)")
    fatigueIndex: float = Field(..., ge=0.0, le=100.0, description="Pilot fatigue index (0-100)")
    phaseOfFlight: PhaseOfFlight = Field(..., description="Current flight phase")


class PredictionResponse(BaseModel):
    predicted_load: float
    error_probability: float
    confidence_score: float


def clamp(value: float, min_val: float, max_val: float) -> float:
    return max(min_val, min(max_val, value))


@app.get("/health")
def health_check():
    return {"status": "ok", "service": "aipclm-ml-service"}


@app.post("/predict", response_model=PredictionResponse)
def predict_cognitive_load(request: PredictionRequest) -> PredictionResponse:
    logger.info(
        "Received prediction request: phase=%s expertLoad=%.2f reactionMs=%d turbulence=%.3f stress=%.2f fatigue=%.2f",
        request.phaseOfFlight,
        request.expertComputedLoad,
        request.reactionTimeMs,
        request.turbulenceLevel,
        request.stressIndex,
        request.fatigueIndex
    )

    # --- Simulated ML prediction ---
    # Base: start from expert load (it's already a well-calibrated number)
    base_load = request.expertComputedLoad

    # Turbulence adjustment: turbulenceLevel is 0–1, scale to 0–10 impact on load
    turbulence_adjustment = request.turbulenceLevel * 10.0

    # Stress adjustment: stress above 30 starts to push load up modestly
    stress_adjustment = max(0.0, (request.stressIndex - 30.0) * 0.10)

    # Fatigue adjustment: every 10 units of fatigue adds ~0.5 load
    fatigue_adjustment = request.fatigueIndex * 0.05

    # Reaction time adjustment: normalize 200–1500ms range to 0–5 impact
    reaction_adjustment = clamp((request.reactionTimeMs - 200) / 1300.0 * 5.0, 0.0, 5.0)

    # Phase multiplier — critical phases get a slight upward nudge
    phase_multiplier = {
        PhaseOfFlight.TAKEOFF:  1.05,
        PhaseOfFlight.CLIMB:    1.00,
        PhaseOfFlight.CRUISE:   0.95,
        PhaseOfFlight.DESCENT:  1.05,
        PhaseOfFlight.APPROACH: 1.10,
        PhaseOfFlight.LANDING:  1.12,
    }.get(request.phaseOfFlight, 1.0)

    raw_predicted = (
        base_load +
        turbulence_adjustment +
        stress_adjustment +
        fatigue_adjustment +
        reaction_adjustment
    ) * phase_multiplier

    predicted_load = clamp(round(raw_predicted, 4), 0.0, 100.0)
    error_probability = clamp(round(predicted_load / 100.0, 4), 0.0, 1.0)
    confidence_score = 0.85  # Fixed for simulated model; will be dynamic post real training

    logger.info(
        "Prediction complete: predictedLoad=%.2f errorProb=%.4f confidence=%.2f "
        "[turbAdj=%.2f stressAdj=%.2f fatigueAdj=%.2f reactionAdj=%.2f phaseMultiplier=%.2f]",
        predicted_load, error_probability, confidence_score,
        turbulence_adjustment, stress_adjustment, fatigue_adjustment,
        reaction_adjustment, phase_multiplier
    )

    return PredictionResponse(
        predicted_load=predicted_load,
        error_probability=error_probability,
        confidence_score=confidence_score
    )


if __name__ == "__main__":
    uvicorn.run("main:app", host="0.0.0.0", port=8001, reload=True)
