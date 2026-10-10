# Build the Spring Boot application
FROM gradle:9-jdk21 AS build

WORKDIR /app

COPY . .

RUN gradle clean bootJar -x test

# Run the application
FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /app/build/libs/ /tmp/jars/

RUN find /tmp/jars -maxdepth 1 -name '*.jar' \
    ! -name '*-plain.jar' -exec cp {} /app/app.jar \;

EXPOSE 10000

ENTRYPOINT ["java", "-jar", "/app/app.jar"]