# 后端：Maven 构建 → JRE 运行
#
# 分两个阶段是为了不让构建工具链（Maven + 本地仓库几百 MB）进入运行镜像。
# 先单独 COPY pom.xml 并预下载依赖，这样只改业务代码时不会重新拉一遍依赖。
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

COPY pom.xml ./
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q package -DskipTests \
    && find target -maxdepth 1 -name 'ck-plm-*.jar' ! -name '*.original' -exec cp {} /build/app.jar \;

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# 上传件（图纸 / 附件 / 媒体）统一落在挂载卷上，容器重建不丢
ENV PLM_STORAGE_BASE=/data/ck-plm

COPY --from=build /build/app.jar /app/app.jar
RUN mkdir -p /data/ck-plm

VOLUME /data/ck-plm
EXPOSE 8082

# JAVA_OPTS 走环境变量，方便按机器内存给不同堆大小（小内存机器可降到 -Xmx512m）
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
