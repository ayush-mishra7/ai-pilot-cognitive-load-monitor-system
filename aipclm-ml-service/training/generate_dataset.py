"""
Synthetic Dataset Generator for AI-PCLM Cognitive Load Model
=============================================================
Generates training data that mirrors the backend expert formula
(5-component weighted CLI) with controlled Gaussian noise to create
realistic supervised learning targets.

Each sample = one telemetry frame with all sensor inputs + the expert-
computed cognitive load as the label. A GradientBoosting model trained
on this data learns the non-linear feature interactions the expert
formula encodes, while capturing additional patterns from noise.

Output: training/data/cognitive_load_dataset.csv  (~50,000 rows)
"""

import os
import csv
import random
import math
from dataclasses import dataclass, asdict
from typing import List

# ── Constants matching the backend expert formula exactly ──

PHASES = ["TAKEOFF", "CLIMB", "CRUISE", "DESCENT", "APPROACH", "LANDING"]

PHASE_BOOST = {
    "APPROACH": 1.4,
    "LANDING": 1.4,
    "TAKEOFF": 1.2,
    "DESCENT": 1.1,
    "CLIMB": 1.0,
    "CRUISE": 0.8,
}

# Phase-specific parameter ranges (realistic simulation bounds)
PHASE_PROFILES = {
    "TAKEOFF": {
        "altitude": (0, 3000), "airspeed": (140, 220), "vertical_speed": (500, 2500),
        "turbulence": (0.0, 0.15), "weather_severity": (0.0, 0.3),
        "heart_rate": (70, 100), "stress": (5, 30), "fatigue": (2, 20),
        "reaction_ms": (200, 600), "error_count": (0, 2),
        "jitter": (0.01, 0.15), "checklist_delay": (0, 5), "switch_rate": (1, 4),
        "blink_rate": (15, 25),
    },
    "CLIMB": {
        "altitude": (3000, 15000), "airspeed": (200, 280), "vertical_speed": (500, 2000),
        "turbulence": (0.0, 0.2), "weather_severity": (0.0, 0.35),
        "heart_rate": (65, 90), "stress": (3, 25), "fatigue": (3, 25),
        "reaction_ms": (200, 550), "error_count": (0, 2),
        "jitter": (0.01, 0.12), "checklist_delay": (0, 4), "switch_rate": (0.5, 3),
        "blink_rate": (15, 25),
    },
    "CRUISE": {
        "altitude": (28000, 42000), "airspeed": (250, 310), "vertical_speed": (-200, 200),
        "turbulence": (0.0, 0.1), "weather_severity": (0.0, 0.2),
        "heart_rate": (60, 80), "stress": (1, 15), "fatigue": (5, 40),
        "reaction_ms": (200, 500), "error_count": (0, 1),
        "jitter": (0.005, 0.08), "checklist_delay": (0, 3), "switch_rate": (0.2, 2),
        "blink_rate": (12, 25),
    },
    "DESCENT": {
        "altitude": (10000, 28000), "airspeed": (220, 280), "vertical_speed": (-2500, -500),
        "turbulence": (0.0, 0.25), "weather_severity": (0.0, 0.4),
        "heart_rate": (65, 95), "stress": (5, 30), "fatigue": (10, 45),
        "reaction_ms": (200, 650), "error_count": (0, 3),
        "jitter": (0.01, 0.18), "checklist_delay": (0, 6), "switch_rate": (1, 5),
        "blink_rate": (14, 25),
    },
    "APPROACH": {
        "altitude": (1000, 10000), "airspeed": (140, 220), "vertical_speed": (-1500, -300),
        "turbulence": (0.0, 0.4), "weather_severity": (0.0, 0.6),
        "heart_rate": (70, 110), "stress": (10, 50), "fatigue": (15, 60),
        "reaction_ms": (250, 900), "error_count": (0, 5),
        "jitter": (0.02, 0.3), "checklist_delay": (0, 10), "switch_rate": (2, 7),
        "blink_rate": (12, 25),
    },
    "LANDING": {
        "altitude": (0, 1000), "airspeed": (120, 170), "vertical_speed": (-1200, -100),
        "turbulence": (0.0, 0.5), "weather_severity": (0.0, 0.7),
        "heart_rate": (75, 120), "stress": (15, 60), "fatigue": (20, 70),
        "reaction_ms": (300, 1200), "error_count": (0, 6),
        "jitter": (0.03, 0.4), "checklist_delay": (0, 15), "switch_rate": (2, 8),
        "blink_rate": (10, 25),
    },
}

# Stress scenario multipliers for generating high-load edge cases
STRESS_SCENARIOS = [
    {"name": "normal", "weight": 0.50, "multiplier": 1.0},
    {"name": "moderate_stress", "weight": 0.25, "multiplier": 1.5},
    {"name": "high_stress", "weight": 0.15, "multiplier": 2.2},
    {"name": "extreme", "weight": 0.10, "multiplier": 3.5},
]


def clamp(value: float, min_val: float = 0.0, max_val: float = 100.0) -> float:
    return max(min_val, min(max_val, value))


@dataclass
class TelemetrySample:
    """One synthetic telemetry frame with all features + expert label."""
    # Input features
    reaction_time_ms: int
    turbulence_level: float
    weather_severity: float
    stress_index: float
    fatigue_index: float
    heart_rate: float
    blink_rate: float
    control_jitter_index: float
    checklist_delay_seconds: float
    task_switch_rate: float
    error_count: int
    phase_of_flight: str
    altitude: float
    airspeed: float
    vertical_speed: float
    # Expert-computed label
    expert_load: float
    # Error probability label (derived from expert model)
    error_probability: float


def compute_expert_load(sample: dict) -> float:
    """Exact replica of the backend CognitiveLoadService expert formula."""

    phase = sample["phase_of_flight"]
    phase_boost = PHASE_BOOST.get(phase, 1.0)

    # Component 1: TaskComplexity (weight 0.25)
    norm_reaction = clamp((sample["reaction_time_ms"] - 200.0) / (1500.0 - 200.0) * 100.0)
    norm_errors = clamp(sample["error_count"] / 10.0 * 100.0)
    task_complexity = clamp(phase_boost * ((norm_reaction * 0.6) + (norm_errors * 0.4)))

    # Component 2: EnvironmentalStress (weight 0.20)
    norm_turbulence = clamp(sample["turbulence_level"] * 100.0)
    norm_weather = clamp(sample["weather_severity"] * 100.0)
    env_stress = clamp((norm_turbulence * 0.6) + (norm_weather * 0.4))

    # Component 3: BehavioralStrain (weight 0.20)
    norm_jitter = clamp(sample["control_jitter_index"] * 100.0)
    norm_checklist = clamp(sample["checklist_delay_seconds"] / 30.0 * 100.0)
    norm_switch = clamp(sample["task_switch_rate"] / 10.0 * 100.0)
    behavioral_strain = clamp((norm_jitter * 0.4) + (norm_checklist * 0.35) + (norm_switch * 0.25))

    # Component 4: PhysiologicalStrain (weight 0.15)
    norm_stress = clamp(sample["stress_index"])
    norm_hr = clamp((sample["heart_rate"] - 60.0) / (160.0 - 60.0) * 100.0)
    physio_strain = clamp((norm_stress * 0.6) + (norm_hr * 0.4))

    # Component 5: FatigueComponent (weight 0.20)
    norm_fatigue = clamp(sample["fatigue_index"])
    norm_blink_fatigue = clamp((30.0 - sample["blink_rate"]) / 20.0 * 100.0)
    fatigue_component = clamp((norm_fatigue * 0.7) + (norm_blink_fatigue * 0.3))

    # Weighted sum
    expert_load = clamp(
        (task_complexity * 0.25)
        + (env_stress * 0.20)
        + (behavioral_strain * 0.20)
        + (physio_strain * 0.15)
        + (fatigue_component * 0.20)
    )

    return expert_load


def compute_error_probability(expert_load: float, fatigue: float, stress: float) -> float:
    """
    Non-linear error probability model:
    - Base: logistic sigmoid centered at load=50
    - Fatigue multiplier: high fatigue amplifies error risk
    - Stress multiplier: high stress amplifies error risk
    """
    base = 1.0 / (1.0 + math.exp(-(expert_load - 50.0) / 15.0))
    fatigue_mult = 1.0 + (fatigue / 100.0) * 0.3
    stress_mult = 1.0 + (stress / 100.0) * 0.2
    return clamp(base * fatigue_mult * stress_mult, 0.0, 1.0)


def sample_range(rng: tuple, noise_factor: float = 0.0) -> float:
    lo, hi = rng
    value = random.uniform(lo, hi)
    if noise_factor > 0:
        noise = random.gauss(0, (hi - lo) * noise_factor)
        value = max(lo, min(hi * 1.2, value + noise))  # allow slight overflow
    return value


def generate_sample(phase: str, stress_multiplier: float = 1.0) -> TelemetrySample:
    """Generate a single synthetic telemetry sample."""
    profile = PHASE_PROFILES[phase]

    raw = {
        "phase_of_flight": phase,
        "reaction_time_ms": int(sample_range(profile["reaction_ms"]) * min(stress_multiplier, 2.5)),
        "turbulence_level": clamp(sample_range(profile["turbulence"]) * stress_multiplier, 0.0, 1.0),
        "weather_severity": clamp(sample_range(profile["weather_severity"]) * stress_multiplier, 0.0, 1.0),
        "stress_index": clamp(sample_range(profile["stress"]) * stress_multiplier),
        "fatigue_index": clamp(sample_range(profile["fatigue"]) * stress_multiplier),
        "heart_rate": clamp(sample_range(profile["heart_rate"]) * min(stress_multiplier, 1.3), 55.0, 180.0),
        "blink_rate": clamp(sample_range(profile["blink_rate"]) / max(stress_multiplier, 0.5), 5.0, 35.0),
        "control_jitter_index": clamp(sample_range(profile["jitter"]) * stress_multiplier, 0.0, 1.0),
        "checklist_delay_seconds": clamp(
            sample_range(profile["checklist_delay"]) * stress_multiplier, 0.0, 30.0
        ),
        "task_switch_rate": clamp(sample_range(profile["switch_rate"]) * stress_multiplier, 0.0, 10.0),
        "error_count": min(int(sample_range(profile["error_count"]) * stress_multiplier), 10),
        "altitude": sample_range(profile["altitude"]),
        "airspeed": sample_range(profile["airspeed"]),
        "vertical_speed": sample_range(profile["vertical_speed"]),
    }

    # Compute expert load using the exact backend formula
    expert_load = compute_expert_load(raw)

    # Add controlled noise to the label (simulating real-world measurement uncertainty)
    noise = random.gauss(0, 2.5)  # ±2.5σ noise on the label
    noisy_load = clamp(expert_load + noise)

    # Compute error probability from a non-linear model
    error_prob = compute_error_probability(noisy_load, raw["fatigue_index"], raw["stress_index"])

    return TelemetrySample(
        reaction_time_ms=raw["reaction_time_ms"],
        turbulence_level=round(raw["turbulence_level"], 6),
        weather_severity=round(raw["weather_severity"], 6),
        stress_index=round(raw["stress_index"], 4),
        fatigue_index=round(raw["fatigue_index"], 4),
        heart_rate=round(raw["heart_rate"], 2),
        blink_rate=round(raw["blink_rate"], 2),
        control_jitter_index=round(raw["control_jitter_index"], 6),
        checklist_delay_seconds=round(raw["checklist_delay_seconds"], 4),
        task_switch_rate=round(raw["task_switch_rate"], 4),
        error_count=raw["error_count"],
        phase_of_flight=phase,
        altitude=round(raw["altitude"], 2),
        airspeed=round(raw["airspeed"], 2),
        vertical_speed=round(raw["vertical_speed"], 2),
        expert_load=round(noisy_load, 4),
        error_probability=round(error_prob, 6),
    )


def generate_dataset(n_samples: int = 50000, seed: int = 42) -> List[TelemetrySample]:
    """Generate a balanced dataset across all phases and stress scenarios."""
    random.seed(seed)
    samples: List[TelemetrySample] = []

    per_phase = n_samples // len(PHASES)

    for phase in PHASES:
        for _ in range(per_phase):
            # Pick stress scenario by weighted probability
            r = random.random()
            cumulative = 0.0
            multiplier = 1.0
            for scenario in STRESS_SCENARIOS:
                cumulative += scenario["weight"]
                if r <= cumulative:
                    multiplier = scenario["multiplier"]
                    break
            samples.append(generate_sample(phase, multiplier))

    # Shuffle for training
    random.shuffle(samples)
    return samples


def save_dataset(samples: List[TelemetrySample], filepath: str):
    """Save dataset to CSV."""
    os.makedirs(os.path.dirname(filepath), exist_ok=True)

    fields = list(asdict(samples[0]).keys())
    with open(filepath, "w", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=fields)
        writer.writeheader()
        for sample in samples:
            writer.writerow(asdict(sample))

    print(f"✅ Dataset saved: {filepath} ({len(samples)} samples, {len(fields)} features)")


if __name__ == "__main__":
    output_path = os.path.join(os.path.dirname(__file__), "data", "cognitive_load_dataset.csv")
    dataset = generate_dataset(n_samples=50000, seed=42)
    save_dataset(dataset, output_path)

    # Quick stats
    loads = [s.expert_load for s in dataset]
    print(f"   Load range: [{min(loads):.2f}, {max(loads):.2f}]")
    print(f"   Mean load: {sum(loads)/len(loads):.2f}")
    print(f"   Phase distribution: {dict((p, sum(1 for s in dataset if s.phase_of_flight == p)) for p in PHASES)}")
