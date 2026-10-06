# syntax=docker/dockerfile:1
# --- Build stage -----------------------------------------------------------
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npm run build

# --- Runtime: nginx serves the SPA and proxies /api to the backend (same origin) ---
FROM nginx:stable-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY nginx-security-headers.conf /etc/nginx/snippets/gym-security-headers.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
