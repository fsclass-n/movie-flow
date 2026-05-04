# 1. 빌드 및 실행 환경 설정
FROM amazoncorretto:21-al2-full

# 2. 작업 디렉토리 생성
WORKDIR /app

# 3. 환경 변수 설정
ENV TZ=Asia/Seoul
ENV JAVA_OPTS="-Xmx512m -Xms256m"

# 4. 시스템 패키지 및 RPA 연동을 위한 Python 설치
RUN yum update -y && \
    yum install -y python3 python3-pip tzdata && \
    yum clean all

# 5. Python 의존성 설치
# requirements.txt가 있으면 사용하고, 없으면 자주 쓰이는 라이브러리 직접 설치
COPY requirements.txt* ./
RUN if [ -f requirements.txt ]; then \
        pip3 install --no-cache-dir -r requirements.txt; \
    else \
        pip3 install python-dotenv requests selenium webdriver-manager; \
    fi

# 6. 애플리케이션 파일 복사
# deploy.yml에서 루트 폴더로 가져온 app.jar를 복사합니다.
COPY app.jar app.jar
COPY rpa ./rpa

# 7. 실행 설정
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar app.jar"]