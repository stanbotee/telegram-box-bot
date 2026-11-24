# ---------- 构建阶段：Maven 打包 ----------
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
# 先离线拉依赖，利用 Docker 层缓存
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q package -DskipTests

# ---------- 运行阶段：JRE 17 slim ----------
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/telegram-box-bot-*-jar-with-dependencies.jar /app/bot.jar
# Render 会注入 $PORT；本地默认 3000
ENV PORT=3000
EXPOSE 3000
CMD ["java", "-jar", "bot.jar"]
