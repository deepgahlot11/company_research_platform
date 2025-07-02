# --- build stage ------------------------------------------------------------
    FROM node:20-alpine AS build
    WORKDIR /app
    
    # Accept environment variable at build time
    ARG VITE_API_BASE_URL
    ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
    
    COPY . .
    
    # Inject the environment into Vite build
    RUN npm install
    RUN npm run build || (echo "Vite build failed!" && exit 1)
    
    # --- nginx runtime stage ----------------------------------------------------
    FROM nginx:alpine
    
    # Copy built frontend assets
    COPY --from=build /app/dist /usr/share/nginx/html
    
    # Copy the NGINX config template
    COPY nginx.template.conf /etc/nginx/templates/default.conf.template
    
    # Default value used only by NGINX at runtime, not by Vite
    ENV API_BASE_URL=http://management:8085/
    
    # Substitute environment variables and start nginx
    CMD ["sh", "-c", "envsubst '${API_BASE_URL}' < /etc/nginx/templates/default.conf.template > /etc/nginx/conf.d/default.conf && exec nginx -g 'daemon off;'"]    