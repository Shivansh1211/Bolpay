FROM node:20-alpine AS build

WORKDIR /app

ARG VITE_PUBLIC_BASE_URL=http://localhost:5173
ENV VITE_PUBLIC_BASE_URL=$VITE_PUBLIC_BASE_URL
ARG VITE_API_URL=
ENV VITE_API_URL=$VITE_API_URL
ARG VITE_WS_URL=
ENV VITE_WS_URL=$VITE_WS_URL

COPY package.json package-lock.json* ./
RUN npm ci

COPY . .
RUN npm run build

FROM nginx:alpine

ARG NGINX_CONFIG=nginx.conf
COPY --from=build /app/dist /usr/share/nginx/html
COPY ${NGINX_CONFIG} /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
