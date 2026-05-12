# 1단계: 빌드 스테이지
FROM gradle:8.5-jdk21 AS build
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src
RUN gradle build --no-daemon -x test

# 2단계: 실행 스테이지
FROM eclipse-temurin:21-jre-jammy

# 필수 시스템 패키지 및 Python 설치
# python3-requests와 python3-bs4를 apt로 설치하면 pip 충돌 문제를 완전히 피할 수 있습니다.
RUN apt-get update && apt-get install -y \
    python3 \
    python3-pip \
    python3-requests \
    python3-bs4 \
    python3-dotenv \
    fonts-nanum \
    tzdata \
    locales \
    && rm -rf /var/lib/apt/lists/*

# 한국 시간 및 언어 설정
RUN ln -snf /usr/share/zoneinfo/Asia/Seoul /etc/localtime && echo "Asia/Seoul" > /etc/timezone
RUN sed -i 's/^# \(ko_KR.UTF-8\)/\1/' /etc/locale.gen && locale-gen
ENV LANG ko_KR.UTF-8
ENV LC_ALL ko_KR.UTF-8

WORKDIR /app

# JAR 파일 및 RPA 파일 복사
COPY --from=build /home/gradle/src/build/libs/*.jar app.jar
COPY rpa/ /app/rpa/

# Render 환경 포트 설정
EXPOSE 8080

# 실행 명령어
ENV PYTHON_COMMAND=python3
ENTRYPOINT ["java", "-Dfile.encoding=UTF-8", "-jar", "app.jar"]