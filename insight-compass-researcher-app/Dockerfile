# Build stage
FROM node:20-alpine as build

WORKDIR /app
COPY . .

RUN npm install
RUN npm run build || (echo "Vite build failed!" && exit 1)

# NGINX stage
FROM nginx:alpine

# Copy built frontend to NGINX html folder
COPY --from=build /app/dist /usr/share/nginx/html

# Copy the NGINX template config
COPY nginx.template.conf /etc/nginx/templates/default.conf.template

# Set default API base URL (can be overridden by env in docker-compose or render.yaml)
ENV API_BASE_URL=http://management:8085

# Replace placeholders in the NGINX config with env values at runtime and start NGINX
CMD ["sh", "-c", "envsubst < /etc/nginx/templates/default.conf.template > /etc/nginx/conf.d/default.conf && nginx -g 'daemon off;'"]
