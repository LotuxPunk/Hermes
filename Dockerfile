# Corretto 21.0.13.1.2 (pushed under the 21.0.13 tags on 2026-11-17) is the first build that fixes
# CVE-2026-114938 "Java4Shell" in the Base64 decoder. Do not go back to an older 21 image.
FROM amazoncorretto:21.0.13-alpine3.24 AS build
COPY . /src
WORKDIR /src
RUN ./gradlew buildFatJar --no-daemon

FROM amazoncorretto:21.0.13-alpine3.24
EXPOSE 8080:8080
RUN mkdir /app
COPY --from=build /src/build/libs/*.jar /app/hermes.jar
ENV CONTACT_FORM_CONFIGS_FOLDER=
ENV MAIL_CONFIGS_FOLDER=
ENV TEMPLATES_FOLDER=
ENTRYPOINT ["java","-jar","/app/hermes.jar"]
LABEL org.opencontainers.image.source=https://github.com/LotuxPunk/Hermes
