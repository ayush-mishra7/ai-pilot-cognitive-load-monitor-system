"""
AIPCLM ML Prediction Service — Phase 3: Advanced ML Pipeline
=============================================================
Serves a trained GradientBoosting model for cognitive load prediction
with per-prediction confidence scoring and SHAP explainability.

Endpoints:
  POST /predict      — Real-time cognitive load + error probability prediction
  POST /explain      — SHAP feature contributions for a single prediction
  GET  /model/info   — Model version, training metrics, feature importances
  GET  /health       — Service health check
"""

import os
import logging
import json
import time
from contextlib import asynccontextmanager
from typing import Dict, List, Optional

import numpy as np
import joblib
import shap
import uvicorn
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field
from enum import Enum

# ── Logging ──
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s - %(message)s",
)
logger = logging.getLogger("aipclm-ml-service")

# ── Global model state ──
_model_artifact: Optional[dict] = None
_shap_explainer: Optional[shap.TreeExplainer] = None


PHASE_ORDER = ["TAKEOFF", "CLIMB", "CRUISE", "DESCENT", "APPROACH", "LANDING"]


def _load_model():
    """Load the trained model artifact from disk."""
    global _model_artifact, _shap_explainer

    models_dir = os.path.join(os.path.dirname(__file__), "models")
    model_path = os.path.join(models_dir, "cognitive_load_model_latest.joblib")

    if not os.path.exists(model_path):
        logger.warning("No trained model found at %s — predictions will use fallback", model_path)
        return

    logger.info("Loading model from %s...", model_path)
    _model_artifact = joblib.load(model_path)

    version = _model_artifact.get("version", "unknown")
    load_metrics = _model_artifact.get("load_metrics", {})
    logger.info(
        "Model v%s loaded — Test R²=%.4f, MAE=%.4f",
        version,
        load_metrics.get("test_r2", 0),
        load_metrics.get("test_mae", 0),
    )

    # Pre-build SHAP explainer (TreeExplainer is fast for GBM)
    try:
        _shap_explainer = shap.TreeExplainer(_model_artifact["load_model"])
        logger.info("SHAP TreeExplainer initialized")
    except Exception as e:
        logger.warning("Failed to initialize SHAP explainer: %s", e)
        _shap_explainer = None


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Load model on startup."""
    _load_model()
    yield


app = FastAPI(
    title="AIPCLM ML Prediction Service",
    description="Cognitive Load ML Prediction with SHAP Explainability for AI-PCLM",
    version="3.0.0",
    lifespan=lifespan,
)


# ── Request / Response Models ──

class PhaseOfFlight(str, Enum):
    TAKEOFF = "TAKEOFF"
    CLIMB = "CLIMB"
    CRUISE = "CRUISE"
    DESCENT = "DESCENT"
    APPROACH = "APPROACH"
    LANDING = "LANDING"


class PredictionRequest(BaseModel):
    expertComputedLoad: float = Field(..., ge=0.0, le=100.0)
    reactionTimeMs: int = Field(..., ge=0)
    turbulenceLevel: float = Field(..., ge=0.0, le=1.0)
    stressIndex: float = Field(..., ge=0.0, le=100.0)
    fatigueIndex: float = Field(..., ge=0.0, le=100.0)
    phaseOfFlight: PhaseOfFlight
    # Extended features (optional — use defaults if not provided)
    weatherSeverity: float = Field(0.0, ge=0.0, le=1.0)
    heartRate: float = Field(75.0, ge=40.0, le=200.0)
    blinkRate: float = Field(18.0, ge=5.0, le=40.0)
    controlJitterIndex: float = Field(0.05, ge=0.0, le=1.0)
    checklistDelaySeconds: float = Field(0.0, ge=0.0, le=30.0)
    taskSwitchRate: float = Field(1.0, ge=0.0, le=15.0)
    errorCount: int = Field(0, ge=0, le=15)
    altitude: float = Field(10000.0, ge=0.0)
    airspeed: float = Field(250.0, ge=0.0)
    verticalSpeed: float = Field(0.0)


class PredictionResponse(BaseModel):
    predicted_load: float
    error_probability: float
    confidence_score: float
    model_version: str


class ExplainRequest(BaseModel):
    expertComputedLoad: float = Field(..., ge=0.0, le=100.0)
    reactionTimeMs: int = Field(..., ge=0)
    turbulenceLevel: float = Field(..., ge=0.0, le=1.0)
    stressIndex: float = Field(..., ge=0.0, le=100.0)
    fatigueIndex: float = Field(..., ge=0.0, le=100.0)
    phaseOfFlight: PhaseOfFlight
    weatherSeverity: float = Field(0.0, ge=0.0, le=1.0)
    heartRate: float = Field(75.0, ge=40.0, le=200.0)
    blinkRate: float = Field(18.0, ge=5.0, le=40.0)
    controlJitterIndex: float = Field(0.05, ge=0.0, le=1.0)
    checklistDelaySeconds: float = Field(0.0, ge=0.0, le=30.0)
    taskSwitchRate: float = Field(1.0, ge=0.0, le=15.0)
    errorCount: int = Field(0, ge=0, le=15)
    altitude: float = Field(10000.0, ge=0.0)
    airspeed: float = Field(250.0, ge=0.0)
    verticalSpeed: float = Field(0.0)


class FeatureContribution(BaseModel):
    feature: str
    value: float
    shap_value: float


class ExplainResponse(BaseModel):
    predicted_load: float
    base_value: float
    feature_contributions: List[FeatureContribution]
    top_positive_drivers: List[str]
    top_negative_drivers: List[str]


class ModelInfoResponse(BaseModel):
    version: str
    trained_at: str
    training_duration_seconds: float
    n_samples: int
    feature_names: List[str]
    load_metrics: Dict[str, float]
    error_metrics: Dict[str, float]
    load_feature_importances: Dict[str, float]
    error_feature_importances: Dict[str, float]
    residual_stats: Dict[str, float]


# ── Helpers ──

def clamp(value: float, min_val: float, max_val: float) -> float:
    return max(min_val, min(max_val, value))


def _encode_phase(phase: str) -> int:
    """Encode phase to integer matching the training label encoder."""
    try:
        return PHASE_ORDER.index(phase)
    except ValueError:
        return 0


def _build_feature_vector(req) -> np.ndarray:
    """Build a feature vector matching the training feature order."""
    return np.array([[
        req.reactionTimeMs,
        req.turbulenceLevel,
        req.weatherSeverity,
        req.stressIndex,
        req.fatigueIndex,
        req.heartRate,
        req.blinkRate,
        req.controlJitterIndex,
        req.checklistDelaySeconds,
        req.taskSwitchRate,
        req.errorCount,
        _encode_phase(req.phaseOfFlight.value),
        req.altitude,
        req.airspeed,
        req.verticalSpeed,
    ]])


def _compute_confidence(X: np.ndarray, predicted_load: float) -> float:
    """
    Dynamic confidence = 1 - normalized predicted uncertainty.
    Uses the trained uncertainty model to estimate expected |residual|.
    """
    if _model_artifact is None:
        return 0.5

    uncertainty_model = _model_artifact.get("uncertainty_model")
    residual_stats = _model_artifact.get("residual_stats", {})

    if uncertainty_model is None:
        return 0.85

    predicted_error = uncertainty_model.predict(X)[0]
    p95 = residual_stats.get("p95", 5.0)

    # Normalize error by p95 of training residuals → [0, 1]
    normalized = clamp(predicted_error / p95, 0.0, 1.0)

    # Confidence: high when normalized error is low
    confidence = clamp(1.0 - normalized * 0.6, 0.5, 0.99)
    return round(confidence, 4)


# ── Fallback (heuristic) prediction for when model is unavailable ──

def _fallback_predict(req) -> PredictionResponse:
    """Heuristic fallback when the trained model is not loaded."""
    base = req.expertComputedLoad
    turb = req.turbulenceLevel * 10.0
    stress = max(0.0, (req.stressIndex - 30.0) * 0.10)
    fatigue = req.fatigueIndex * 0.05
    reaction = clamp((req.reactionTimeMs - 200) / 1300.0 * 5.0, 0.0, 5.0)
    phase_mult = {
        "TAKEOFF": 1.05, "CLIMB": 1.00, "CRUISE": 0.95,
        "DESCENT": 1.05, "APPROACH": 1.10, "LANDING": 1.12,
    }.get(req.phaseOfFlight.value, 1.0)

    raw = (base + turb + stress + fatigue + reaction) * phase_mult
    load = clamp(round(raw, 4), 0.0, 100.0)

    return PredictionResponse(
        predicted_load=load,
        error_probability=clamp(round(load / 100.0, 4), 0.0, 1.0),
        confidence_score=0.50,
        model_version="fallback-heuristic",
    )


# ── Endpoints ──

@app.get("/health")
def health_check():
    model_loaded = _model_artifact is not None
    version = _model_artifact.get("version", "none") if model_loaded else "none"
    return {
        "status": "ok",
        "service": "aipclm-ml-service",
        "model_loaded": model_loaded,
        "model_version": version,
    }


@app.post("/predict", response_model=PredictionResponse)
def predict_cognitive_load(request: PredictionRequest) -> PredictionResponse:
    start = time.time()

    logger.info(
        "Prediction request: phase=%s expertLoad=%.2f reactionMs=%d turbulence=%.3f stress=%.2f fatigue=%.2f",
        request.phaseOfFlight, request.expertComputedLoad, request.reactionTimeMs,
        request.turbulenceLevel, request.stressIndex, request.fatigueIndex,
    )

    if _model_artifact is None:
        logger.warning("Model not loaded — using fallback heuristic")
        return _fallback_predict(request)

    X = _build_feature_vector(request)

    # Predict cognitive load
    load_model = _model_artifact["load_model"]
    predicted_load = clamp(round(float(load_model.predict(X)[0]), 4), 0.0, 100.0)

    # Predict error probability
    error_model = _model_artifact["error_model"]
    error_prob = clamp(round(float(error_model.predict(X)[0]), 6), 0.0, 1.0)

    # Dynamic confidence
    confidence = _compute_confidence(X, predicted_load)

    version = _model_artifact.get("version", "unknown")
    elapsed_ms = (time.time() - start) * 1000

    logger.info(
        "Prediction complete (%.1fms): load=%.2f errorProb=%.4f confidence=%.4f model=v%s",
        elapsed_ms, predicted_load, error_prob, confidence, version,
    )

    return PredictionResponse(
        predicted_load=predicted_load,
        error_probability=error_prob,
        confidence_score=confidence,
        model_version=version,
    )


@app.post("/explain", response_model=ExplainResponse)
def explain_prediction(request: ExplainRequest) -> ExplainResponse:
    """Return SHAP feature contributions for a single prediction."""
    if _model_artifact is None or _shap_explainer is None:
        raise HTTPException(status_code=503, detail="Model or SHAP explainer not loaded")

    X = _build_feature_vector(request)
    feature_names = _model_artifact.get("feature_names", [])

    # Predict
    load_model = _model_artifact["load_model"]
    predicted_load = clamp(float(load_model.predict(X)[0]), 0.0, 100.0)

    # SHAP values
    shap_values = _shap_explainer.shap_values(X)
    shap_row = shap_values[0]
    base_value = float(_shap_explainer.expected_value)

    # Build feature contributions
    contributions: List[FeatureContribution] = []
    for i, name in enumerate(feature_names):
        contributions.append(FeatureContribution(
            feature=name,
            value=round(float(X[0, i]), 4),
            shap_value=round(float(shap_row[i]), 4),
        ))

    # Sort by |SHAP| descending
    contributions.sort(key=lambda c: abs(c.shap_value), reverse=True)

    # Top drivers
    top_positive = [c.feature for c in contributions if c.shap_value > 0][:5]
    top_negative = [c.feature for c in contributions if c.shap_value < 0][:5]

    return ExplainResponse(
        predicted_load=round(predicted_load, 4),
        base_value=round(base_value, 4),
        feature_contributions=contributions,
        top_positive_drivers=top_positive,
        top_negative_drivers=top_negative,
    )


@app.get("/model/info", response_model=ModelInfoResponse)
def model_info() -> ModelInfoResponse:
    """Return metadata about the currently loaded model."""
    if _model_artifact is None:
        raise HTTPException(status_code=503, detail="No model loaded")

    return ModelInfoResponse(
        version=_model_artifact.get("version", "unknown"),
        trained_at=_model_artifact.get("trained_at", "unknown"),
        training_duration_seconds=_model_artifact.get("training_duration_seconds", 0),
        n_samples=_model_artifact.get("n_samples", 0),
        feature_names=_model_artifact.get("feature_names", []),
        load_metrics=_model_artifact.get("load_metrics", {}),
        error_metrics=_model_artifact.get("error_metrics", {}),
        load_feature_importances=_model_artifact.get("load_feature_importances", {}),
        error_feature_importances=_model_artifact.get("error_feature_importances", {}),
        residual_stats=_model_artifact.get("residual_stats", {}),
    )


@app.post("/model/reload")
def reload_model():
    """Hot-reload the model from disk without restarting the service."""
    _load_model()
    if _model_artifact is None:
        raise HTTPException(status_code=500, detail="Failed to reload model")
    return {
        "status": "reloaded",
        "version": _model_artifact.get("version", "unknown"),
    }


if __name__ == "__main__":
    uvicorn.run("main:app", host="0.0.0.0", port=8001, reload=True)
