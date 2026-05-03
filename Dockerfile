# 1. 빌드 스테이지
FROM gradle:8.5-jdk21 AS builder

WORKDIR /build

# 빌드 캐시 효율을 위해 설정 파일 먼저 복사
COPY build.gradle settings.gradle gradlew gradlew.bat ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew build -x test --no-daemon || return 0

# 전체 소스 복사 및 빌드
COPY src ./src
RUN ./gradlew clean build -x test --no-daemon

# 2. 런타임 스테이지 (안정적인 Amazon Corretto 사용)
FROM amazoncorretto:21-al2-full

WORKDIR /app

# 파이썬 및 타임존 설정을 위한 패키지 설치
# Amazon Linux 2(al2) 기반이므로 yum을 사용합니다.
RUN yum update -y && \
    yum install -y python3 python3-pip tzdata && \
    yum clean all

# 파이썬 환경 변수 및 라이브러리 설정 (선택 사항)
# RUN pip3 install python-dotenv  # 만약 파이썬에서 dotenv 등을 사용한다면 추가

# 환경 변수 설정
ENV JAVA_OPTS="-Xmx512m -Xms256m"
ENV SERVER_PORT=8080
ENV TZ=Asia/Seoul

# 빌드 스테이지에서 생성된 JAR 복사 (파일명 유연하게 처리)
COPY --from=builder /build/build/libs/*.jar app.jar

# RPA 스크립트 폴더 복사 (프로젝트 루트에 rpa 폴더가 있는 경우)
COPY rpa ./rpa

# 포트 노출
EXPOSE ${SERVER_PORT}

# 애플리케이션 실행
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar app.jar --server.port=${SERVER_PORT}"]