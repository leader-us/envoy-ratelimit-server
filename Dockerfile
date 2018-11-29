#docker build -t mycat/ratelimit-hpe .
FROM openjdk:8-jre
ADD hperateserver-0.1.0.jar app.jar
ADD log4j2.xml log4j2.xml
EXPOSE 8081
ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-Dlog4j.configurationFile=file:/log4j2.xml","-jar","app.jar"]
