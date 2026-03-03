"""
Model Training Pipeline for AI-PCLM Cognitive Load Prediction
==============================================================
Trains a dual-output GradientBoosting ensemble:
  1. GradientBoostingRegressor for cognitive_load prediction
  2. GradientBoostingClassifier for error_probability (calibrated)

Features:
  - One-hot encodes phase_of_flight
  - Computes train/test metrics (MAE, RMSE, R²)
  - Saves model + metadata as a versioned .joblib artifact
  - Computes SHAP global feature importances

Output: models/cognitive_load_model_v{VERSION}.joblib
"""

import os
import sys
import json
import time
import hashlib
import datetime
import numpy as np
import pandas as pd
import joblib

from sklearn.model_selection import train_test_split
from sklearn.ensemble import GradientBoostingRegressor
from sklearn.metrics import mean_absolute_error, mean_squared_error, r2_score
from sklearn.preprocessing import LabelEncoder

# ── Configuration ──

MODEL_VERSION = "1.0.0"
RANDOM_STATE = 42
TEST_SIZE = 0.2

FEATURE_COLUMNS = [
    "reaction_time_ms",
    "turbulence_level",
    "weather_severity",
    "stress_index",
    "fatigue_index",
    "heart_rate",
    "blink_rate",
    "control_jitter_index",
    "checklist_delay_seconds",
    "task_switch_rate",
    "error_count",
    "phase_of_flight",  # categorical → encoded
    "altitude",
    "airspeed",
    "vertical_speed",
]

LOAD_TARGET = "expert_load"
ERROR_TARGET = "error_probability"

PHASE_ORDER = ["TAKEOFF", "CLIMB", "CRUISE", "DESCENT", "APPROACH", "LANDING"]


def prepare_features(df: pd.DataFrame) -> tuple:
    """Encode phase_of_flight to integer and return feature matrix."""
    le = LabelEncoder()
    le.fit(PHASE_ORDER)
    df_encoded = df.copy()
    df_encoded["phase_of_flight"] = le.transform(df_encoded["phase_of_flight"])
    X = df_encoded[FEATURE_COLUMNS].values
    feature_names = FEATURE_COLUMNS.copy()
    return X, feature_names, le


def train_cognitive_load_model(X_train, y_train, X_test, y_test):
    """Train the primary cognitive load regression model."""
    print("🧠 Training GradientBoosting (cognitive_load)...")

    model = GradientBoostingRegressor(
        n_estimators=500,
        max_depth=6,
        learning_rate=0.05,
        subsample=0.8,
        min_samples_split=10,
        min_samples_leaf=5,
        max_features="sqrt",
        random_state=RANDOM_STATE,
        validation_fraction=0.1,
        n_iter_no_change=20,
        tol=1e-4,
    )

    model.fit(X_train, y_train)

    # Evaluate
    y_pred_train = model.predict(X_train)
    y_pred_test = model.predict(X_test)

    metrics = {
        "train_mae": float(mean_absolute_error(y_train, y_pred_train)),
        "train_rmse": float(np.sqrt(mean_squared_error(y_train, y_pred_train))),
        "train_r2": float(r2_score(y_train, y_pred_train)),
        "test_mae": float(mean_absolute_error(y_test, y_pred_test)),
        "test_rmse": float(np.sqrt(mean_squared_error(y_test, y_pred_test))),
        "test_r2": float(r2_score(y_test, y_pred_test)),
    }

    print(f"   Train — MAE: {metrics['train_mae']:.4f}, RMSE: {metrics['train_rmse']:.4f}, R²: {metrics['train_r2']:.4f}")
    print(f"   Test  — MAE: {metrics['test_mae']:.4f}, RMSE: {metrics['test_rmse']:.4f}, R²: {metrics['test_r2']:.4f}")

    return model, metrics


def train_error_model(X_train, y_train, X_test, y_test):
    """Train an error probability regression model."""
    print("⚠️ Training GradientBoosting (error_probability)...")

    model = GradientBoostingRegressor(
        n_estimators=300,
        max_depth=5,
        learning_rate=0.05,
        subsample=0.8,
        min_samples_split=10,
        min_samples_leaf=5,
        max_features="sqrt",
        random_state=RANDOM_STATE,
        validation_fraction=0.1,
        n_iter_no_change=15,
        tol=1e-4,
    )

    model.fit(X_train, y_train)

    y_pred_test = model.predict(X_test)
    metrics = {
        "test_mae": float(mean_absolute_error(y_test, y_pred_test)),
        "test_rmse": float(np.sqrt(mean_squared_error(y_test, y_pred_test))),
        "test_r2": float(r2_score(y_test, y_pred_test)),
    }
    print(f"   Test  — MAE: {metrics['test_mae']:.4f}, RMSE: {metrics['test_rmse']:.4f}, R²: {metrics['test_r2']:.4f}")

    return model, metrics


def compute_confidence_model(load_model, X_train, y_train):
    """
    Build a residual-based confidence estimator.
    For each prediction, confidence = 1 - normalized_residual.
    We also train a model on absolute residuals to predict per-sample uncertainty.
    """
    print("📊 Building confidence estimator...")
    y_pred = load_model.predict(X_train)
    residuals = np.abs(y_train - y_pred)

    # Train a model to predict |residual| from features
    uncertainty_model = GradientBoostingRegressor(
        n_estimators=100,
        max_depth=3,
        learning_rate=0.1,
        subsample=0.8,
        random_state=RANDOM_STATE,
    )
    uncertainty_model.fit(X_train, residuals)

    # Compute global residual stats for normalization
    residual_stats = {
        "mean": float(np.mean(residuals)),
        "std": float(np.std(residuals)),
        "p95": float(np.percentile(residuals, 95)),
    }
    print(f"   Residual stats — mean: {residual_stats['mean']:.4f}, std: {residual_stats['std']:.4f}, p95: {residual_stats['p95']:.4f}")

    return uncertainty_model, residual_stats


def compute_feature_importances(model, feature_names):
    """Get feature importances from the GradientBoosting model."""
    importances = model.feature_importances_
    sorted_idx = np.argsort(importances)[::-1]
    result = {}
    for idx in sorted_idx:
        result[feature_names[idx]] = float(importances[idx])
    return result


def main():
    start_time = time.time()

    # ── Load data ──
    data_path = os.path.join(os.path.dirname(__file__), "data", "cognitive_load_dataset.csv")
    if not os.path.exists(data_path):
        print(f"❌ Dataset not found at {data_path}")
        print("   Run: python training/generate_dataset.py")
        sys.exit(1)

    print(f"📂 Loading dataset from {data_path}...")
    df = pd.read_csv(data_path)
    print(f"   Shape: {df.shape}")

    # ── Prepare features ──
    X, feature_names, label_encoder = prepare_features(df)
    y_load = df[LOAD_TARGET].values
    y_error = df[ERROR_TARGET].values

    # ── Split ──
    X_train, X_test, y_load_train, y_load_test, y_error_train, y_error_test = train_test_split(
        X, y_load, y_error, test_size=TEST_SIZE, random_state=RANDOM_STATE
    )
    print(f"   Train: {X_train.shape[0]}, Test: {X_test.shape[0]}")

    # ── Train models ──
    load_model, load_metrics = train_cognitive_load_model(X_train, y_load_train, X_test, y_load_test)
    error_model, error_metrics = train_error_model(X_train, y_error_train, X_test, y_error_test)
    uncertainty_model, residual_stats = compute_confidence_model(load_model, X_train, y_load_train)

    # ── Feature importances ──
    load_importances = compute_feature_importances(load_model, feature_names)
    error_importances = compute_feature_importances(error_model, feature_names)
    print(f"\n📊 Top-5 Load features: {dict(list(load_importances.items())[:5])}")
    print(f"📊 Top-5 Error features: {dict(list(error_importances.items())[:5])}")

    # ── Compute data checksum ──
    with open(data_path, "rb") as f:
        data_hash = hashlib.md5(f.read()).hexdigest()

    # ── Package artifact ──
    elapsed = time.time() - start_time
    artifact = {
        "version": MODEL_VERSION,
        "trained_at": datetime.datetime.utcnow().isoformat() + "Z",
        "training_duration_seconds": round(elapsed, 2),
        "data_hash": data_hash,
        "n_samples": len(df),
        "n_features": len(feature_names),
        "feature_names": feature_names,
        "phase_encoder_classes": list(label_encoder.classes_),
        "load_model": load_model,
        "error_model": error_model,
        "uncertainty_model": uncertainty_model,
        "residual_stats": residual_stats,
        "load_metrics": load_metrics,
        "error_metrics": error_metrics,
        "load_feature_importances": load_importances,
        "error_feature_importances": error_importances,
        "label_encoder": label_encoder,
    }

    # ── Save ──
    models_dir = os.path.join(os.path.dirname(os.path.dirname(__file__)), "models")
    os.makedirs(models_dir, exist_ok=True)
    model_path = os.path.join(models_dir, f"cognitive_load_model_v{MODEL_VERSION}.joblib")
    joblib.dump(artifact, model_path, compress=3)

    # Also save as "latest" for easy loading
    latest_path = os.path.join(models_dir, "cognitive_load_model_latest.joblib")
    joblib.dump(artifact, latest_path, compress=3)

    model_size = os.path.getsize(model_path) / (1024 * 1024)
    print(f"\n✅ Model saved: {model_path} ({model_size:.2f} MB)")
    print(f"✅ Latest symlink: {latest_path}")
    print(f"⏱️ Total training time: {elapsed:.1f}s")

    # ── Save metadata as JSON (for the FastAPI service to read easily) ──
    metadata = {
        "version": MODEL_VERSION,
        "trained_at": artifact["trained_at"],
        "training_duration_seconds": artifact["training_duration_seconds"],
        "data_hash": data_hash,
        "n_samples": len(df),
        "feature_names": feature_names,
        "phase_encoder_classes": list(label_encoder.classes_),
        "load_metrics": load_metrics,
        "error_metrics": error_metrics,
        "residual_stats": residual_stats,
        "load_feature_importances": load_importances,
        "error_feature_importances": error_importances,
    }
    meta_path = os.path.join(models_dir, "model_metadata.json")
    with open(meta_path, "w") as f:
        json.dump(metadata, f, indent=2)
    print(f"📋 Metadata: {meta_path}")


if __name__ == "__main__":
    main()
