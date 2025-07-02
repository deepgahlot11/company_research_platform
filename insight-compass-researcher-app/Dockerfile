# --- build stage ------------------------------------------------------------
FROM node:20-alpine AS build
WORKDIR /app
COPY . .
RUN npm install
RUN npm run build || (echo "Vite build failed!" && exit 1)

# --- nginx runtime stage ----------------------------------------------------
FROM nginx:alpine

# copy built assets
COPY --from=build /app/dist /usr/share/nginx/html

# copy template
COPY nginx.template.conf /etc/nginx/templates/default.conf.template

# default for local docker‑compose; Render overrides via env var
ENV API_BASE_URL=http://management:8085/

CMD ["sh", "-c", "envsubst < /etc/nginx/templates/default.conf.template > /etc/nginx/conf.d/default.conf && exec nginx -g 'daemon off;'"]
