# ---------- Etapa 1: compilación con Maven (HU-97) ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests

# ---------- Etapa 2: solo el JRE y el JAR (sin código fuente ni Maven) ----------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S minimundo && adduser -S minimundo -G minimundo
COPY --from=build /app/target/app.jar app.jar
USER minimundo
# Render asigna el puerto en la variable PORT; en local se usa el puerto por defecto del servicio.
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS="-XX:+UseSerialGC -XX:MaxRAMPercentage=65 -XX:TieredStopAtLevel=1 -Xss256k -Djava.security.egd=file:/dev/./urandom"
EXPOSE 8085
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
