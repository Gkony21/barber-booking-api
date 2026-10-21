# Builds and runs the API in a container (used by Render; works on any Docker host).

FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
# Dependencies first, so they are cached between builds when only the code changes.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN ./mvnw -q -B dependency:go-offline
COPY src src
RUN ./mvnw -q -B -DskipTests package && cp target/barber-booking-api-*.jar app.jar

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 1001 app
COPY --from=build /app/app.jar app.jar
USER app
# Sized for a small (512 MB) instance: the heap gets most of the memory, the rest stays lean.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Xss512k -XX:ReservedCodeCacheSize=64m -Djava.awt.headless=true"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
