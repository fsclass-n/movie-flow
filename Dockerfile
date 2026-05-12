# 1단계: 빌드 스테이지 (Gradle 빌드)
FROM gradle:8.5-jdk21 AS build
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src
# 빌드 시 테스트를 제외하여 속도 향상
RUN gradle build --no-daemon -x test

# 2단계: 실행 스테이지
FROM openjdk:21-slim

# 필요한 패키지 설치 (Python, 한글 로케일, 타임존 설정)
RUN apt-get update && apt-get install -y \
    python3 \
    python3-pip \
    python3-venv \
    fonts-nanum \
    tzdata \
    locales \
    && rm -rf /var/lib/apt/lists/*

# 한국 시간 및 언어 설정 (중요: 로그 및 상영시간 동기화)
RUN ln -snf /usr/share/zoneinfo/Asia/Seoul /etc/localtime && echo "Asia/Seoul" > /etc/timezone
RUN sed -i 's/^# \(ko_KR.UTF-8\)/\1/' /etc/locale.gen && locale-gen
ENV LANG ko_KR.UTF-8
ENV LANGUAGE ko_KR:ko
ENV LC_ALL ko_KR.UTF-8

# 작업 디렉토리 설정
WORKDIR /app

# 빌드된 JAR 파일 복사
COPY --from=build /home/gradle/src/build/libs/*.jar app.jar

# RPA 스크립트 및 관련 파일 복사
# (프로젝트 구조에 맞춰 rpa 디렉토리 전체를 복사합니다)
COPY rpa/ /app/rpa/

# Python 라이브러리 설치
# requirements.txt가 rpa 폴더 안에 있다면 아래 경로를 수정하세요.
RUN pip3 install --no-cache-dir --break-system-packages \
    requests \
    beautifulsoup4 \
    python-dotenv

# Render 환경을 위한 포트 설정
EXPOSE 8080

# 컨테이너 실행 명령어
# Python 경로를 환경 변수로 지정하여 Spring Boot에서 참조하게 함
ENV PYTHON_COMMAND=python3
ENTRYPOINT ["java", "-Dfile.encoding=UTF-8", "-jar", "app.jar"]