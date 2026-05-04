FROM amazoncorretto:21-al2-full
WORKDIR /app

# 시스템 패키지 및 파이썬 설치
RUN yum update -y && \
    yum install -y python3 python3-pip tzdata && \
    yum clean all

# 파이썬 의존성 설치
COPY requirements.txt* ./
RUN if [ -f requirements.txt ]; then pip3 install --no-cache-dir -r requirements.txt; \
    else pip3 install python-dotenv; fi

ENV JAVA_OPTS="-Xmx512m -Xms256m"
ENV SERVER_PORT=8080
ENV TZ=Asia/Seoul

# 수정한 부분: *.jar 파일을 현재 폴더에서 찾도록 설정
COPY *.jar app.jar
COPY rpa ./rpa

EXPOSE ${SERVER_PORT}

ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar app.jar --server.port=${SERVER_PORT}"]