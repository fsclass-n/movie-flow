# --- 1단계: 빌드 스테이지 ---
FROM gradle:8.5-jdk21 AS build
WORKDIR /app
COPY build.gradle settings.gradle ./
COPY src ./src
RUN gradle clean build -x test --no-daemon

# --- 2단계: 실행 스테이지 ---
FROM amazoncorretto:21-al2-full
WORKDIR /app

# 빌드된 jar 복사
COPY --from=build /app/build/libs/*.jar app.jar

# RPA 환경 설치 (Python)
RUN yum update -y && \
    yum install -y python3 python3-pip tzdata && \
    yum clean all

COPY requirements.txt* ./
RUN if [ -f requirements.txt ]; then \
        pip3 install --no-cache-dir -r requirements.txt; \
    else \
        pip3 install python-dotenv requests selenium webdriver-manager; \
    fi

COPY rpa ./rpa

ENV TZ=Asia/Seoul
ENV JAVA_OPTS="-Xmx512m -Xms256m"
EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar app.jar"]