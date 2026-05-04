# 1. 베이스 이미지
FROM amazoncorretto:21-al2-full

# 2. 작업 디렉토리
WORKDIR /app

# 3. 타임존 및 자바 옵션
ENV TZ=Asia/Seoul
ENV JAVA_OPTS="-Xmx512m -Xms256m"

# 4. 필수 시스템 패키지 및 Python 설치 (RPA 연동용)
RUN yum update -y && \
    yum install -y python3 python3-pip tzdata && \
    yum clean all

# 5. Python 의존성 설치
COPY requirements.txt* ./
RUN if [ -f requirements.txt ]; then \
        pip3 install --no-cache-dir -r requirements.txt; \
    else \
        pip3 install python-dotenv requests selenium webdriver-manager; \
    fi

# 6. 빌드된 jar와 RPA 폴더 복사
# (deploy.yml에서 만든 app.jar를 그대로 복사)
COPY app.jar app.jar
COPY rpa ./rpa

# 7. 실행
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar app.jar"]