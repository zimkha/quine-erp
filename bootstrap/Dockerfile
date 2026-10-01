FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY . .
RUN mvn -q -B -pl bootstrap -am package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/bootstrap/target/bootstrap-1.0-SNAPSHOT.jar app.jar
EXPOSE 18080
ENTRYPOINT ["java", "-jar", "app.jar"]
