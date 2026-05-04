# --- 1단계: 빌드 스테이지 (Gradle 빌드 전용) ---
FROM gradle:8.5-jdk21 AS build
WORKDIR /app

# 빌드 속도 향상을 위한 설정 파일 선복사
COPY build.gradle settings.gradle ./
COPY src ./src

# 실행 가능한 jar 파일 빌드 (테스트 제외)
RUN gradle clean build -x test --no-daemon

# --- 2단계: 실행 스테이지 (최종 이미지) ---
FROM amazoncorretto:21-al2-full
WORKDIR /app

# 1단계 빌드 결과물(jar)을 가져옴
COPY --from=build /app/build/libs/*.jar app.jar

# 타임존 한국 설정 및 RPA 실행을 위한 Python/시스템 패키지 설치
ENV TZ=Asia/Seoul
RUN yum update -y && \
    yum install -y python3 python3-pip tzdata && \
    yum clean all

# Python 의존성 설치 (requirements.txt가 프로젝트 루트에 있을 경우)
COPY requirements.txt* ./
RUN if [ -f requirements.txt ]; then \
        pip3 install --no-cache-dir -r requirements.txt; \
    else \
        pip3 install python-dotenv requests selenium webdriver-manager; \
    fi

# RPA 스크립트 폴더 복사
COPY rpa ./rpa

# 자바 실행 옵션 및 포트 설정
ENV JAVA_OPTS="-Xmx512m -Xms256m"
EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar app.jar"]