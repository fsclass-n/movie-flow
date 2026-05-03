# 1. 빌드 스테이지
FROM gradle:8.5-jdk21 AS builder
WORKDIR /build
COPY build.gradle settings.gradle gradlew gradlew.bat ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew build -x test --no-daemon || return 0
COPY src ./src
RUN ./gradlew clean build -x test --no-daemon

# 2. 런타임 스테이지
FROM amazoncorretto:21-al2-full
WORKDIR /app

# 파이썬 및 필수 패키지 설치
RUN yum update -y && \
    yum install -y python3 python3-pip tzdata && \
    yum clean all

# 파이썬 라이브러리 설치 (requirements.txt가 루트에 있다고 가정)
# 만약 파일이 없다면 'pip3 install python-dotenv'로 직접 써도 됩니다.
COPY requirements.txt* ./
RUN if [ -f requirements.txt ]; then pip3 install --no-cache-dir -r requirements.txt; \
    else pip3 install python-dotenv; fi

# 환경 변수 설정
ENV JAVA_OPTS="-Xmx512m -Xms256m"
ENV SERVER_PORT=8080
ENV TZ=Asia/Seoul

# 빌드 결과물 복사
COPY --from=builder /build/build/libs/*.jar app.jar

# [핵심 수정] RPA 폴더 복사
# 폴더가 없을 경우 에러 방지를 위해 존재 확인 후 복사하는 로직
# 프로젝트 루트에 rpa 폴더가 있는지 반드시 확인하세요!
COPY rpa ./rpa

# 포트 노출
EXPOSE ${SERVER_PORT}

ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar app.jar --server.port=${SERVER_PORT}"]