# Build stage
FROM node:20-alpine as build

WORKDIR /app
COPY . .

RUN npm install
RUN npm run build || (echo "Vite build failed!" && exit 1)

# NGINX stage
FROM nginx:alpine

COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
