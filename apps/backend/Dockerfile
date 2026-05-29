# syntax=docker/dockerfile:1.6
# Backend Spring Boot 多阶段构建
# Stage 1: maven 构建 jar
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
# 预热依赖（无源码改动时缓存命中）
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package \
 && cp target/xqt-backend-*.jar /workspace/app.jar

# Stage 2: runtime
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S xqt && adduser -S xqt -G xqt
COPY --from=build /workspace/app.jar /app/app.jar
USER xqt
EXPOSE 8080
# 任务 S6/S7 生产 strict 开关：环境变量覆盖；JVM 内存默认半个容器
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
