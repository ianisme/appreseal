FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /src
COPY pom.xml mvnw ./
COPY .mvn .mvn
COPY src src
RUN chmod +x mvnw && ./mvnw -B -DskipTests package

FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# 脱壳链路随镜像分发：appget.sh、FairPlay 解密引擎、Linux ipatool。
RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
        bash ca-certificates gcc libc6-dev python3 python3-venv \
    && rm -rf /var/lib/apt/lists/*

COPY docker/ipatool-linux-arm64 docker/ipatool-linux-amd64 /opt/ipatool/
RUN set -eux; \
    case "$(uname -m)" in \
      aarch64) cp /opt/ipatool/ipatool-linux-arm64 /opt/ipatool/ipatool ;; \
      x86_64)  cp /opt/ipatool/ipatool-linux-amd64 /opt/ipatool/ipatool ;; \
      *) echo "unsupported arch: $(uname -m)" >&2; exit 1 ;; \
    esac; \
    rm -f /opt/ipatool/ipatool-linux-arm64 /opt/ipatool/ipatool-linux-amd64; \
    chmod 755 /opt/ipatool/ipatool

COPY docker/fairplay /opt/fairplay
RUN set -eux; \
    cd /opt/fairplay/fairplay-decrypt; \
    gcc -std=c11 -O2 -o transform transform.c; \
    python3 -m venv .venv; \
    .venv/bin/pip install --no-cache-dir --upgrade pip; \
    .venv/bin/pip install --no-cache-dir -r requirements.txt; \
    chmod 755 /opt/fairplay/fairplay-tools/appget.sh /opt/fairplay/fairplay-tools/accounts.py /opt/fairplay/fairplay-decrypt/decrypt.py

COPY --from=build /src/target/appreseal-server-0.1.0.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
