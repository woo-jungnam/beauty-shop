FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Tạo user bảo mật không đặc quyền để chạy Spring Boot
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copy trực tiếp file JAR đã đóng gói sẵn
COPY target/*.jar app.jar

EXPOSE 8080

ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
  CMD wget -q -O - http://localhost:8080/actuator/health | grep UP || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

