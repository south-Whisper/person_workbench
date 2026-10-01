ARG DOCKER_REGISTRY=docker.io/library
FROM ${DOCKER_REGISTRY}/maven:3.9.11-eclipse-temurin-17 AS build
WORKDIR /build

COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -DskipTests dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -DskipTests package

FROM ${DOCKER_REGISTRY}/eclipse-temurin:17-jre-jammy
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 sethub \
    && useradd --system --uid 10001 --gid sethub --home-dir /app sethub

WORKDIR /app
COPY --from=build /build/target/*.jar /app/sethub.jar
RUN mkdir -p /app/data && chown -R sethub:sethub /app

USER sethub
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/sethub.jar"]
