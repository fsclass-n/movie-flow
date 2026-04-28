FROM openjdk:21-slim

WORKDIR /app

COPY build/libs/movie-flow-0.0.1.jar app.jar

# Python/RPA 환경 (실전에서는 별도 컨테이너 사용 권장)
RUN apt-get update && apt-get install -y python3 python3-pip firefox && rm -rf /var/lib/apt/lists/*
RUN pip3 install selenium

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]