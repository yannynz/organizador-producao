FROM node:18-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm cache clean --force && npm install --legacy-peer-deps
COPY . .
RUN npm install --legacy-peer-deps
RUN npm run build --prod
FROM nginx:alpine
COPY --from=build /app/dist/ /usr/share/nginx/html
CMD ["nginx", "-g", "daemon off;"]

