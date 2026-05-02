# ============================================================
# Stage 1: Layer Extractor — 호스트에서 미리 빌드한 jar에서 레이어 추출
# ============================================================
FROM eclipse-temurin:21-jdk-alpine AS extractor

WORKDIR /opt

# 호스트에서 ./gradlew bootJar로 미리 빌드된 jar를 복사
ARG JAR_FILE=build/libs/*.jar
COPY ${JAR_FILE} app.jar

# Spring Boot Layered JAR 추출
RUN java -Djarmode=layertools -jar app.jar extract

# ============================================================
# Stage 2: Runtime — Alpine JRE에 레이어별 복사
# ============================================================
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# 보안: non-root 사용자 생성
RUN addgroup --system --gid 1001 spring && \
    adduser  --system --uid 1001 --ingroup spring spring

# ⭐ OpenTelemetry Java Agent 다운로드 (Phase 4 Epic 9)
# 활성화는 K8s deployment.yaml의 JAVA_TOOL_OPTIONS 환경변수에서 결정
# 즉 이 jar이 있어도 환경변수 박지 않으면 자동 비활성화
ARG OTEL_AGENT_VERSION=2.26.1
ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${OTEL_AGENT_VERSION}/opentelemetry-javaagent.jar /opt/otel/opentelemetry-javaagent.jar
RUN chmod 644 /opt/otel/opentelemetry-javaagent.jar

# 변경 빈도가 낮은 순서대로 복사 (Docker 레이어 캐시 최적화)
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

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]