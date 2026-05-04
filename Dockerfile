# 1. 베이스 이미지 설정
FROM amazoncorretto:21-al2-full

# 2. 작업 디렉토리 설정
WORKDIR /app

# 3. 환경 변수 설정 (타임존 및 자바 옵션)
ENV TZ=Asia/Seoul
ENV JAVA_OPTS="-Xmx512m -Xms256m"

# 4. 시스템 패키지 업데이트 및 RPA를 위한 Python 설치
RUN yum update -y && \
    yum install -y python3 python3-pip tzdata && \
    yum clean all

# 5. Python 의존성 설치 (requirements.txt가 있으면 설치, 없으면 기본 라이브러리 설치)
COPY requirements.txt* ./
RUN if [ -f requirements.txt ]; then \
        pip3 install --no-cache-dir -r requirements.txt; \
    else \
        pip3 install python-dotenv requests selenium webdriver-manager; \
    fi

# 6. 빌드된 jar 파일 및 RPA 스크립트 복사
# (deploy.yml에서 복사해둔 app.jar를 사용합니다)
COPY app.jar app.jar
COPY rpa ./rpa

# 7. 포트 개방
EXPOSE 8080

# 8. 애플리케이션 실행
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar app.jar"]