FROM gcr.io/distroless/java17-debian12:latest
COPY /build/libs/hm-oppgave-sink-all.jar /app.jar
ENV TZ="Europe/Oslo"
CMD ["/app.jar"]
