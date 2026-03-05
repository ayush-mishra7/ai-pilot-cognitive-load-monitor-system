# ──────────────────────────────────────────────
#  AI-PCLM Frontend — Multi-stage Docker Build
# ──────────────────────────────────────────────
#  Stage 1: Node build   (Vite production build)
#  Stage 2: Nginx serve  (Alpine)
# ──────────────────────────────────────────────

# ── Stage 1: Build ──
FROM node:20-alpine AS builder

WORKDIR /build

# Cache node_modules
COPY package.json package-lock.json* ./
RUN npm ci --ignore-scripts || npm install

# Copy source and build
COPY . .

# Build arg for backend URL (defaults to Docker Compose service name)
ARG VITE_API_BASE_URL=http://localhost:8080
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}

RUN npm run build

# ── Stage 2: Serve with Nginx ──
FROM nginx:1.27-alpine

LABEL maintainer="Ayush Mishra <ayush-mishra7>"
LABEL description="AI-PCLM Frontend — React 19 + Vite 7"

# Remove default config
RUN rm /etc/nginx/conf.d/default.conf

# Custom Nginx config for SPA routing
COPY nginx.conf /etc/nginx/conf.d/aipclm.conf

# Copy built assets
COPY --from=builder /build/dist /usr/share/nginx/html

# Health check
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD wget -qO- http://localhost:80/ || exit 1

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
