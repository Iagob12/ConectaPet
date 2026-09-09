# API no Azure

Esta pilha executa somente a API e o proxy HTTPS. O banco continua externo e
nenhuma porta do MySQL e publicada.

Arquivos sensiveis ficam fora do repositorio, em `/etc/conectapet/api.env`, com
permissao `600`. O nome DNS publico fica em `/opt/conectapet/deploy/azure/.env`.

Atualizacao segura:

```sh
cd /opt/conectapet
git pull --ff-only
sudo docker build -t conectapet-api:current ./api
cd deploy/azure
sudo docker compose up -d
sudo docker compose ps
```

O proxy somente passa trafego para a API depois que o `HEALTHCHECK` interno
indica que banco e migracoes terminaram de inicializar.
