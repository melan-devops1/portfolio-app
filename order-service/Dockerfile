# ============================================================
# Stage 1 (extractor): 호스트에서 빌드한 jar의 Layered JAR 분해
#   - 최종 이미지엔 포함되지 않음 (멀티스테이지)
#   - layertools는 jar 내부에 포함되므로 사실 JRE만으로도 동작
# ============================================================
FROM eclipse-temurin:21-jdk-alpine AS extractor

WORKDIR /opt

ARG JAR_FILE=build/libs/*.jar
COPY ${JAR_FILE} app.jar

RUN java -Djarmode=layertools -jar app.jar extract

# ============================================================
# Stage 2 (runtime): Alpine JRE + 앱 + OTel Agent
#
# 레이어별 측정 사이즈는 파일 하단의 표 참조.
# ============================================================
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# [L8] non-root 사용자
RUN addgroup --system --gid 1001 spring && \
    adduser  --system --uid 1001 --ingroup spring spring

# [L9] OpenTelemetry Java Agent (Phase 4 Epic 9)
# 활성화는 K8s deployment.yaml의 JAVA_TOOL_OPTIONS=-javaagent:... 환경변수가 결정.
# ADD --chmod 으로 별도 RUN chmod 레이어 제거 — 같은 jar의 중복 복사 방지
# (이전 비효율: ADD 24MB + 별도 RUN chmod 24MB = 48MB → 24MB로 정정).
ARG OTEL_AGENT_VERSION=2.26.1
ADD --chmod=644 \
    https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${OTEL_AGENT_VERSION}/opentelemetry-javaagent.jar \
    /opt/otel/opentelemetry-javaagent.jar

# [L10~L13] Spring Boot Layered JAR
# 변경 빈도 낮은 순서대로 복사 → Docker 레이어 캐시 최대화
COPY --from=extractor --chown=spring:spring /opt/dependencies/          ./
COPY --from=extractor --chown=spring:spring /opt/spring-boot-loader/    ./
COPY --from=extractor --chown=spring:spring /opt/snapshot-dependencies/ ./
COPY --from=extractor --chown=spring:spring /opt/application/           ./

USER spring:spring

# JVM 컨테이너 인식 + 메모리 75% + G1GC + OOM 시 즉시 종료
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 \
               -XX:+UseG1GC \
               -XX:+ExitOnOutOfMemoryError \
               -Dfile.encoding=UTF-8"

# 메타데이터 (실제 listen 포트는 application.yaml/SERVER_PORT 환경변수가 결정)
EXPOSE 8082

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]

# ============================================================
# 레이어별 사이즈 — 2026-05-14 측정 (order-service, attestation 미포함)
#   · 압축 해제: `docker history` SIZE 컬럼 (디스크 점유)
#   · 압축       : `docker save | gzip | wc -c` (ECR 저장 / pull 시 네트워크 전송)
# ============================================================
# # | Layer (CreatedBy 요약)                     | 압축 해제   | 의미
# --+--------------------------------------------+-------------+-------------------
# 1 | ADD alpine-minirootfs-3.23.4               |   9.11 MB   | 베이스 OS
# 2 | apk add (fontconfig/gnupg/ca-cert/...)     |  37.6  MB   | 시스템 패키지
# 3 | install Adoptium Temurin JRE21             | 165    MB   | JRE 본체 (단일 최대)
# 4 | RUN verify java + COPY cacert entrypoint   |  24.6 kB    | 베이스 셋업
# 5 | WORKDIR /app                               |   8.2 kB    | 디렉토리
# 6 | RUN addgroup/adduser spring                |  41   kB    | non-root 사용자
# 7 | ADD --chmod=644 OTel agent jar             |  24.4 MB    | OTLP 자동 추적
# 8 | COPY dependencies/                         |  62.3 MB    | Spring + 3rd-party
# 9 | COPY spring-boot-loader/                   | 692   kB    | 부트 로더
# 10| COPY snapshot-dependencies/                |   4.1 kB    | 스냅샷 의존성
# 11| COPY application/                          | 205   kB    | 앱 코드 (order)
# --+--------------------------------------------+-------------+-------------------
# 총 합 (압축 해제, 디스크 점유)                  | 299.4 MB
# 압축 사이즈 (ECR 저장 / 네트워크 전송, 실질지표) | 142.5 MB   ← README/이력서 인용
# (참고) docker images 표시치                    | 450   MB   (BuildKit/manifest 메타 포함)
#
# 이전 (chmod 별도 RUN 레이어 시절) 대비 OTel 중복 24 MB 제거 →
#   디스크 점유 ~323.7 → 299.4 MB (-24 MB)
#   ECR 압축    ~162.5 → 142.5 MB (-20 MB)
# ============================================================
