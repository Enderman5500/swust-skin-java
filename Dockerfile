FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
ENV TZ=Asia/Shanghai
COPY --from=build /app/target/swust-skin.jar ./swust-skin.jar
COPY public ./public
COPY plugins ./plugins
COPY data ./data
VOLUME ["/app/data", "/app/plugins"]
EXPOSE 3000
ENTRYPOINT ["java", "--enable-native-access=ALL-UNNAMED", "-jar", "/app/swust-skin.jar"]
