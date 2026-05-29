# syntax=docker/dockerfile:1.6
# Backend Spring Boot 多阶段构建（含国内 maven 镜像 + buildkit 缓存）
# 用 daocloud 镜像源直接拉，避免 buildx --platform 时绕过 daemon registry-mirrors
FROM docker.m.daocloud.io/library/maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY .docker-maven-settings.xml /root/.m2/settings.xml
COPY pom.xml .
# buildkit cache mount：跨构建复用 ~/.m2，避免重复下载
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn -B -q dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn -B -q -DskipTests package \
 && cp target/xqt-backend-*.jar /workspace/app.jar

# Stage 2: runtime（用通用 tag 避免 alpine multi-arch 不全）
FROM docker.m.daocloud.io/library/eclipse-temurin:17-jre
WORKDIR /app
RUN groupadd -r xqt && useradd -r -g xqt xqt
COPY --from=build /workspace/app.jar /app/app.jar
USER xqt
EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
