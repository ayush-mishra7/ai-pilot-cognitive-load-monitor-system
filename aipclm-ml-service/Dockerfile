# ──────────────────────────────────────────────
#  AI-PCLM ML Service — Multi-stage Docker Build
# ──────────────────────────────────────────────
#  Stage 1: Dependencies  (install heavy ML libs)
#  Stage 2: Runtime       (copy app + models)
# ──────────────────────────────────────────────

# ── Stage 1: Dependencies ──
FROM python:3.11-slim AS builder

WORKDIR /build

# Install build deps for numpy/scikit-learn
RUN apt-get update && apt-get install -y --no-install-recommends \
    gcc g++ && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir --prefix=/install -r requirements.txt

# ── Stage 2: Runtime ──
FROM python:3.11-slim

LABEL maintainer="Ayush Mishra <ayush-mishra7>"
LABEL description="AI-PCLM ML Service — FastAPI + GradientBoosting + SHAP"

WORKDIR /app

# Create non-root user
RUN groupadd -r aipclm && useradd -r -g aipclm -s /bin/false aipclm

# Copy installed packages
COPY --from=builder /install /usr/local

# Copy application
COPY main.py .
COPY models/ ./models/

# Health check
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:8001/health')" || exit 1

# Use non-root
USER aipclm

EXPOSE 8001

# Production: no reload
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8001", "--workers", "2"]
