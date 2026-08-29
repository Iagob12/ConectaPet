# O endereço público entra no build, não na execução.
#
# O Astro resolve `site` em tempo de build, e é dele que sai a lista de hosts
# aceitos. Com o valor errado, o servidor sobe, as páginas abrem e TODO envio
# de formulário responde 403 — login, cadastro e ativação inclusive. Por isso
# URL_SITE é ARG e não variável de execução: passar depois não teria efeito.
ARG URL_SITE=https://conectapet.com.br

FROM node:22-alpine AS compilacao
WORKDIR /construcao
ARG URL_SITE
ENV URL_SITE=${URL_SITE}

# package-lock antes do código, pelo cache: enquanto as dependências não mudam,
# o npm ci não roda de novo.
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

FROM node:22-alpine
WORKDIR /app

RUN addgroup -S conectapet && adduser -S conectapet -G conectapet

# Só o que o servidor precisa: o build e as dependências de produção.
COPY --from=compilacao --chown=conectapet:conectapet /construcao/dist ./dist
COPY --from=compilacao --chown=conectapet:conectapet /construcao/package.json ./
COPY --from=compilacao --chown=conectapet:conectapet /construcao/node_modules ./node_modules

USER conectapet

# API_URL, ao contrário de URL_SITE, é lida em tempo de execução: o código
# consulta process.env, então o endereço da API muda sem reconstruir a imagem.
ENV HOST=0.0.0.0 PORT=4321
EXPOSE 4321

HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
  CMD wget -qO- http://127.0.0.1:4321/entrar >/dev/null || exit 1

CMD ["node", "dist/server/entry.mjs"]
