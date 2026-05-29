# syntax=docker/dockerfile:1.6
# Frontend Vue + Vite 多阶段（含国内 npm 镜像 + buildkit 缓存）
FROM node:20-alpine AS build
WORKDIR /workspace
RUN npm config set registry https://registry.npmmirror.com
COPY package.json package-lock.json* ./
# 项目暂无 package-lock.json，用 install（不严格锁版本）；后续提交 lock 后切回 ci
RUN --mount=type=cache,target=/root/.npm \
    npm install --no-audit --no-fund --legacy-peer-deps
COPY . .
RUN --mount=type=cache,target=/root/.npm \
    npm run build

# Stage 2: nginx serve
FROM nginx:1.27-alpine
RUN rm -rf /usr/share/nginx/html/*
COPY --from=build /workspace/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
