FROM java:8-alpine
MAINTAINER Your Name <you@example.com>

ADD target/uberjar/optiontrader.jar /optiontrader/app.jar

EXPOSE 3000

CMD ["java", "-jar", "/optiontrader/app.jar"]
