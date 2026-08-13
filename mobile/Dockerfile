# ── Stage 1: build (export web do Expo) ────────────────────────────────────
FROM node:22-alpine AS builder
WORKDIR /app

COPY package*.json .npmrc .
RUN npm ci

COPY . .

# URL da API configurável em build-time (baked no bundle, igual ao VITE_API_BASE_URL do admin)
ARG EXPO_PUBLIC_API_URL=http://localhost:8080/api/v1
ENV EXPO_PUBLIC_API_URL=$EXPO_PUBLIC_API_URL

RUN npx expo export --platform web

# ── Stage 2: nginx ───────────────────────────────────────────────────────────
FROM nginx:1.27-alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
