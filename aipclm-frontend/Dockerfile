# Stage 1: Build the React Application
FROM node:20-alpine AS builder

WORKDIR /app

# Install dependencies using layer caching
COPY package.json package-lock.json ./
RUN npm ci

# Copy the source code and build for production
COPY . .
RUN npm run build

# Stage 2: Serve using ultra-light Nginx
FROM nginx:alpine

# Copy the build artifacts from the builder to Nginx's public folder
COPY --from=builder /app/dist /usr/share/nginx/html

# Copy a custom fallback Nginx config to support React Client-Side Routing
RUN echo $'server { \\n\
    listen 80; \\n\
    server_name localhost; \\n\
    location / { \\n\
        root   /usr/share/nginx/html; \\n\
        index  index.html index.htm; \\n\
        try_files $uri $uri/ /index.html; \\n\
    } \\n\
}' > /etc/nginx/conf.d/default.conf

# Expose 80 inside container (Will map to 5173 externally in docker-compose.yml)
EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
