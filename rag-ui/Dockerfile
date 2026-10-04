# Only needed if self-hosting the frontend (e.g. on the same VM as the
# backend). If deploying to Vercel/Netlify/Cloudflare Pages instead,
# this file isn't used - those platforms build straight from the repo.

FROM node:22-slim AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
# Bake the real backend/voice URLs in at build time, since Vite env
# vars are compiled into the static bundle, not read at runtime.
ARG VITE_API_BASE_URL
ARG VITE_VOICE_WS_URL
RUN npm run build

FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html

# Client-side routing (React Router) - unknown paths fall back to
# index.html instead of nginx's default 404.
RUN echo 'server { listen 80; location / { root /usr/share/nginx/html; try_files $uri /index.html; } }' \
    > /etc/nginx/conf.d/default.conf

EXPOSE 80
