# Build stage
FROM node:20-alpine as build

WORKDIR /app
COPY . .

RUN npm install
RUN npm run build || (echo "Vite build failed!" && exit 1)

# NGINX stage
FROM nginx:alpine

# Copy built frontend
COPY --from=build /app/dist /usr/share/nginx/html

# Copy template nginx config
COPY nginx.conf.template /etc/nginx/templates/nginx.conf.template

# Runtime: substitute env vars into nginx.conf
ENV API_BASE_URL=http://management:8085/

CMD ["/bin/sh", "-c", "envsubst < /etc/nginx/templates/nginx.conf.template > /etc/nginx/conf.d/default.conf && exec nginx -g 'daemon off;'"]
