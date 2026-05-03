# Render 배포 가이드

## 준비 사항

1. **Render 계정 생성**: https://render.com 에서 무료 계정 생성
2. **GitHub 저장소 연결**: Render에서 GitHub 계정 연결
3. **데이터베이스 준비**: MySQL 데이터베이스 생성 또는 연결

## 배포 방법

### 방법 1: Render 대시보드에서 직접 배포

1. Render 대시보드에 로그인
2. **New +** → **Web Service** 선택
3. GitHub 저장소 연결
4. 다음 설정 입력:
   - **Name**: movie-flow
   - **Root Directory**: ./
   - **Runtime**: Docker
   - **Build Command**: `chmod +x gradlew && ./gradlew clean build -x test --no-daemon`
   - **Start Command**: `java ${JAVA_OPTS} -jar build/libs/movie-flow-0.0.1.jar --server.port=${SERVER_PORT}`

### 방법 2: render.yaml을 사용한 자동 배포

프로젝트 루트의 `render.yaml` 파일을 Render가 자동으로 감지하여 배포합니다.

```bash
# GitHub에 commit 및 push
git add .
git commit -m "Add Render deployment configuration"
git push origin main
```

## 환경 변수 설정

Render 대시보드에서 다음 환경 변수를 설정하세요:

```
SPRING_DATASOURCE_URL=jdbc:mysql://<db-host>:<db-port>/<db-name>
SPRING_DATASOURCE_USERNAME=<db-username>
SPRING_DATASOURCE_PASSWORD=<db-password>
SPRING_PROFILES_ACTIVE=prod
JAVA_OPTS=-Xmx512m -Xms256m
SERVER_PORT=8080
```

## 데이터베이스 연결

### MySQL 원격 데이터베이스 사용

1. **Render의 PostgreSQL/MySQL 서비스 사용**:
   - Render 대시보드에서 **Databases** → **New Database** 선택
   - PostgreSQL 또는 MySQL 선택
   - 자동으로 환경 변수 설정

2. **외부 MySQL 서버 사용**:
   - 데이터베이스 URL, 사용자명, 비밀번호를 환경 변수로 설정
   - 방화벽에서 Render 서버 IP 허용

### 초기 스키마 설정

배포 후 SQL 초기화 스크립트 실행:

```bash
# Render에서 실행 (Web Service 셸 접근)
curl -X POST http://localhost:8080/api/init-db
```

또는 `src/main/resources/schema.sql` 파일을 데이터베이스에 직접 실행합니다.

## 배포 확인

1. 배포 완료 후 Render에서 제공하는 URL 방문: `https://<your-app>.onrender.com`
2. 로그 확인: Render 대시보드 → **Logs** 탭
3. 헬스 체크: `curl https://<your-app>.onrender.com/health`

## 문제 해결

### 빌드 실패
- **Gradle 권한 문제**: `chmod +x gradlew` 추가
- **메모리 부족**: `JAVA_OPTS=-Xmx256m -Xms128m` 으로 감소
- **의존성 문제**: `build.gradle` 확인 및 `gradlew clean` 실행

### 런타임 오류
- **포트 바인딩**: `SERVER_PORT` 환경 변수 확인
- **데이터베이스 연결**: 연결 문자열 및 방화벽 설정 확인
- **로그 확인**: Render 대시보드의 Logs 탭에서 에러 메시지 확인

### 성능 최적화
- **메모리 할당**: `JAVA_OPTS` 조정
- **동시성**: `spring.datasource.hikari.maximum-pool-size` 설정
- **캐싱**: Redis 추가 (Render의 Redis 서비스 이용)

## 자동 배포 설정

GitHub 푸시 시 자동으로 Render에 배포되도록 설정:

1. Render 대시보드에서 Web Service 선택
2. **Settings** → **Deploy Hooks** 복사
3. GitHub 저장소 → **Settings** → **Webhooks**
4. Payload URL로 Render Deploy Hook 입력
5. Content type: `application/json`

이제 주요 브랜치(main/master)에 푸시하면 자동 배포됩니다.

## 비용 최적화

- **프리 플랜**: 월 750시간 무료 (약 30일)
- **Starter 플랜**: 월 $7부터 시작
- **활성 유지**: 15분 동안 요청이 없으면 자동 절전 (프리 플랜)
- **Always On**: 절전 비활성화 (Starter 플랜 이상)

## 참고 자료

- [Render 공식 문서](https://render.com/docs)
- [Docker 배포 가이드](https://render.com/docs/docker)
- [데이터베이스 설정](https://render.com/docs/databases)
