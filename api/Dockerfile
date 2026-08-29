# Duas etapas: compila numa imagem com Maven, roda numa só com o runtime.
# A imagem final não leva compilador nem código-fonte — menos superfície e
# menos peso para subir a cada deploy.
FROM maven:3.9-eclipse-temurin-21 AS compilacao
WORKDIR /construcao

# As dependências entram antes do código: enquanto o pom não muda, esta camada
# fica em cache e o build seguinte não rebaixa a internet inteira de novo.
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
# Sem "-o" de propósito. `go-offline` não resolve tudo o que o `package`
# precisa — o plugin de repackage do Spring Boot e o surefire buscam artefato
# na hora — e o build offline falharia logo depois de baixar as dependências.
#
# Os testes ficam para a esteira de integração contínua, que tem banco.
# Rodá-los aqui exigiria MySQL dentro do build da imagem.
RUN mvn -B package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Usuário sem privilégio: se algum dia houver execução de código pela aplicação,
# ela não começa como root dentro do container.
RUN addgroup -S conectapet && adduser -S conectapet -G conectapet
USER conectapet

COPY --from=compilacao --chown=conectapet:conectapet /construcao/target/*.jar app.jar

EXPOSE 8080

# O container é saudável quando o Actuator responde. Sem isso, o orquestrador
# manda tráfego para uma aplicação que ainda está subindo o Flyway.
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget -qO- http://127.0.0.1:8080/actuator/health || exit 1

# MaxRAMPercentage em vez de -Xmx fixo: o container pode ganhar ou perder
# memória sem alguém lembrar de acertar um número aqui.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
