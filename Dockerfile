# 1. 빌드 스테이지
FROM gradle:8.5-jdk21 AS builder
WORKDIR /build

COPY build.gradle settings.gradle gradlew gradlew.bat ./
COPY gradle ./gradle
COPY src ./src

RUN chmod +x gradlew && ./gradlew clean build -x test --no-daemon

# 2. 런타임 스테이지
FROM amazoncorretto:21-al2-full
WORKDIR /app

RUN yum update -y && \
    yum install -y python3 python3-pip tzdata && \
    yum clean all

COPY requirements.txt* ./
RUN if [ -f requirements.txt ]; then pip3 install --no-cache-dir -r requirements.txt; \
    else pip3 install python-dotenv; fi

ENV JAVA_OPTS="-Xmx512m -Xms256m"
ENV SERVER_PORT=8080
ENV TZ=Asia/Seoul

COPY --from=builder /build/build/libs/*.jar app.jar
COPY rpa ./rpa

EXPOSE ${SERVER_PORT}

ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar app.jar --server.port=${SERVER_PORT}"]