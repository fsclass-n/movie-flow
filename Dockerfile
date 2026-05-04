# 빌드 스테이지 삭제 (GitHub Actions가 빌드한 jar를 바로 사용)
FROM amazoncorretto:21-al2-full
WORKDIR /app

# 시스템 패키지 설치
RUN yum update -y && \
    yum install -y python3 python3-pip tzdata && \
    yum clean all

# 파이썬 의존성 설치
COPY requirements.txt* ./
RUN if [ -f requirements.txt ]; then pip3 install --no-cache-dir -r requirements.txt; \
    else pip3 install python-dotenv; fi

# 환경 변수 설정
ENV JAVA_OPTS="-Xmx512m -Xms256m"
ENV SERVER_PORT=8080
ENV TZ=Asia/Seoul

# GitHub Actions 빌드 결과물인 jar 파일을 복사
# (주의: deploy.yml의 빌드 결과물 경로와 일치해야 함)
COPY build/libs/*.jar app.jar
COPY rpa ./rpa

EXPOSE ${SERVER_PORT}

ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar app.jar --server.port=${SERVER_PORT}"]