# --- 1단계: 빌드 스테이지 ---
FROM gradle:8.5-jdk21 AS build
WORKDIR /app

# Gradle 빌드에 필요한 파일들 복사
COPY build.gradle settings.gradle ./
COPY src ./src

# 실행 가능한 jar 파일 빌드 (테스트 제외)
RUN gradle clean build -x test --no-daemon

# --- 2단계: 실행 스테이지 ---
FROM amazoncorretto:21-al2-full
WORKDIR /app

# 1단계에서 빌드된 jar 파일만 쏙 가져오기 (가장 확실한 경로)
COPY --from=build /app/build/libs/*.jar app.jar

# 시스템 패키지 및 RPA를 위한 Python 설치
RUN yum update -y && \
    yum install -y python3 python3-pip tzdata && \
    yum clean all

# Python 의존성 설치
COPY requirements.txt* ./
RUN if [ -f requirements.txt ]; then \
        pip3 install --no-cache-dir -r requirements.txt; \
    else \
        pip3 install python-dotenv requests selenium webdriver-manager; \
    fi

# RPA 스크립트 복사
COPY rpa ./rpa

ENV TZ=Asia/Seoul
ENV JAVA_OPTS="-Xmx512m -Xms256m"
EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar app.jar"]