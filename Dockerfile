# 1. Fase de build
FROM eclipse-temurin:21-jdk AS build

WORKDIR /app

# Copia código Java
COPY src ./src

# Compila tudo (ServerMain e ProxyMain)
RUN mkdir -p bin && javac -d bin $(find src -name "*.java")

# =======================================================
# 2. Runtime da imagem do proxy
# =======================================================
FROM eclipse-temurin:21-jre AS proxy

# Instalar ferramentas de rede
RUN apt-get update && apt-get install -y iproute2 net-tools && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=build /app/bin ./bin

# Criar diretórios para logs
RUN mkdir -p logs pcap output

EXPOSE 8000
EXPOSE 8001

CMD ["java", "-cp", "bin", "proxy.ProxyMain"]

# =======================================================
# 3. Runtime da imagem do servidor
# =======================================================
FROM eclipse-temurin:21-jre AS server

# Instalar iproute2
RUN apt-get update && apt-get install -y iproute2 && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=build /app/bin ./bin

EXPOSE 9000
CMD ["java", "-cp", "bin", "network.ServerMain"]