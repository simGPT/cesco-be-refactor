FROM eclipse-temurin:21-jre

# cd.yaml에서 빌드한 jar를 이미지에 복사
COPY build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]