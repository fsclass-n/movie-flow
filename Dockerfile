# --- 1단계: 빌드 스테이지 ---
FROM gradle:8.5-jdk21 AS build
WORKDIR /app

# 빌드 속도 향상을 위한 설정 파일 선복사
COPY build.gradle settings.gradle ./
COPY src ./src

# JAR 파일 생성 (테스트 제외)
RUN gradle clean build -x test --no-daemon

# --- 2단계: 실행 스테이지 ---
FROM amazoncorretto:21-al2-full
WORKDIR /app

# 빌드 결과물 복사
COPY --from=build /app/build/libs/*.jar app.jar

# RPA 실행을 위한 Python 및 타임존 설정
ENV TZ=Asia/Seoul
RUN yum update -y && \
    yum install -y python3 python3-pip tzdata && \
    yum clean all

# Python 라이브러리 설치
COPY requirements.txt* ./
RUN if [ -f requirements.txt ]; then \
        pip3 install --no-cache-dir -r requirements.txt; \
    else \
        pip3 install python-dotenv requests selenium webdriver-manager; \
    fi

COPY rpa ./rpa

# 메모리 최적화 옵션 (t2.micro 권장)
ENV JAVA_OPTS="-Xmx512m -Xms256m"
EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar app.jar"]