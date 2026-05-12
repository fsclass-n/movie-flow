# --- 1단계: 빌드 스테이지 (Gradle 빌드) ---
FROM gradle:8.5-jdk21 AS build
WORKDIR /app

# 의존성 캐싱을 위해 설정 파일 먼저 복사
COPY build.gradle settings.gradle ./
COPY src ./src

# JAR 파일 생성 (테스트는 빌드 시간 단축을 위해 제외)
RUN gradle clean build -x test --no-daemon

# --- 2단계: 실행 스테이지 (실행 환경 구성) ---
FROM amazoncorretto:21-al2-full
WORKDIR /app

# 시스템 라이브러리 및 Python 설치
# Amazon Linux 2의 yum을 사용하여 필요한 패키지 설치
RUN yum update -y && \
    yum install -y python3 python3-pip tzdata wget unzip && \
    ln -sf /usr/bin/python3 /usr/bin/python

# 크롬 브라우저 설치 (RPA/Selenium용)
RUN wget -q https://dl.google.com/linux/direct/google-chrome-stable_current_x86_64.rpm && \
    yum localinstall -y google-chrome-stable_current_x86_64.rpm && \
    rm -f google-chrome-stable_current_x86_64.rpm && \
    yum clean all

# 타임존 설정
ENV TZ=Asia/Seoul

# Python 의존성 설치 (에러 방지를 위한 핵심 로직)
# 1. requests 필수 설치
# 2. OpenSSL 호환성을 위해 urllib3는 반드시 2.0 미만으로 고정
RUN pip3 install --no-cache-dir \
    "requests<2.30.0" \
    "urllib3<2.0" \
    "python-dotenv" \
    "beautifulsoup4"

# 빌드 결과물 및 RPA 스크립트 복사
COPY --from=build /app/build/libs/*.jar app.jar
COPY rpa ./rpa

# 환경 변수 설정
ENV JAVA_OPTS="-Xmx512m -Xms256m"
ENV PYTHON_COMMAND=python3
ENV SE_CACHE_PATH=/tmp/selenium

# 포트 개방
EXPOSE 8080

# 실행 명령어
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar app.jar"]