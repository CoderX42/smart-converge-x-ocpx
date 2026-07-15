FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /workspace
COPY . .
RUN mvn -pl scx-boot -am -DskipTests package

FROM eclipse-temurin:17-jre-jammy

WORKDIR /app
COPY --from=build /workspace/scx-boot/target/scx-boot.jar app.jar

ENV JAVA_OPTS="-Xms512m -Xmx1024m -XX:+ExitOnOutOfMemoryError"

EXPOSE 9999

ENTRYPOINT ["sh", "-c", "exec java ${JAVA_OPTS} -jar app.jar"]
