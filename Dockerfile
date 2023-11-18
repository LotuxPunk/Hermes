FROM gradle:7-jdk17 AS build
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src
RUN gradle buildFatJar --no-daemon

FROM amazoncorretto:17.0.8-alpine3.18
EXPOSE 8080:8080
RUN mkdir /app
COPY --from=build /home/gradle/src/build/libs/*.jar /app/hermes.jar
ENV CONTACT_FORM_CONFIGS_FOLDER=
ENV MAIL_CONFIGS_FOLDER=
ENV TEMPLATES_FOLDER=
ENV GOOGLE_RECAPTCHA_SECRET=
ENV SENDGRID_API_KEY=
ENTRYPOINT ["java","-jar","/app/hermes.jar"]
