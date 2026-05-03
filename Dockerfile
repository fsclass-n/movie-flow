# 멀티 스테이지 빌드 - 빌드 스테이지
FROM gradle:8.5-jdk21 AS builder

WORKDIR /build

COPY build.gradle settings.gradle gradlew gradlew.bat ./
COPY gradle ./gradle
COPY src ./src

RUN chmod +x gradlew && ./gradlew clean build -x test --no-daemon

# 런타임 스테이지
FROM openjdk:21-slim

WORKDIR /app

# 보안 및 성능을 위한 기본 설정
ENV JAVA_OPTS="-Xmx512m -Xms256m"
ENV SERVER_PORT=8080

# 타임존 설정
RUN apt-get update && apt-get install -y tzdata && rm -rf /var/lib/apt/lists/*
ENV TZ=Asia/Seoul

# 빌드 스테이지에서 JAR 파일 복사
COPY --from=builder /build/build/libs/movie-flow-0.0.1.jar app.jar

# 헬스 체크 설정
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
    CMD java -cp app.jar org.springframework.boot.loader.JarLauncher &>/dev/null || exit 1

# 포트 노출
EXPOSE ${SERVER_PORT}

# 애플리케이션 실행
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar app.jar --server.port=${SERVER_PORT}"]