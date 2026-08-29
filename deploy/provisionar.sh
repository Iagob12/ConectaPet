#!/usr/bin/env bash
#
# Prepara um servidor Ubuntu novo para rodar o ConectaPet.
#
# Roda uma vez, como root, num servidor recém-criado:
#   curl -fsSL https://raw.githubusercontent.com/<voce>/ConectaPet/main/deploy/provisionar.sh | bash
#
# Ou, mais seguro, leia antes e execute do repositório já clonado:
#   sudo bash deploy/provisionar.sh
#
# O que ele NÃO faz: criar a máquina, registrar o domínio, apontar o DNS ou
# preencher o .env. Essas quatro coisas são suas.

set -euo pipefail

dizer() { printf '\n\033[1;36m==> %s\033[0m\n' "$1"; }
erro()  { printf '\n\033[1;31m!! %s\033[0m\n' "$1" >&2; exit 1; }

[ "$(id -u)" -eq 0 ] || erro "Rode como root (use sudo)."

dizer "Atualizando o sistema"
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get upgrade -y -qq

dizer "Instalando Docker"
if ! command -v docker >/dev/null 2>&1; then
  curl -fsSL https://get.docker.com | sh
else
  echo "Docker já está instalado."
fi

# A construção da imagem da API roda Maven, que pede bem mais memória do que a
# aplicação em si. Numa máquina de 2 GB o build morre por falta de memória e o
# erro que aparece — "Killed" — não diz o motivo. A troca cobre esse pico sem
# exigir uma máquina maior o ano inteiro.
dizer "Criando área de troca, se não houver"
if [ "$(swapon --show | wc -l)" -eq 0 ]; then
  fallocate -l 2G /swapfile
  chmod 600 /swapfile
  mkswap /swapfile >/dev/null
  swapon /swapfile
  grep -q '/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
  echo "2 GB de troca criados."
else
  echo "Já existe área de troca."
fi

dizer "Abrindo o firewall"
# Só o que o proxy precisa. O banco e as aplicações não publicam porta: elas
# conversam pela rede interna do compose.
if command -v ufw >/dev/null 2>&1; then
  ufw allow OpenSSH >/dev/null
  ufw allow 80/tcp   >/dev/null
  ufw allow 443/tcp  >/dev/null
  ufw --force enable >/dev/null
  echo "Liberadas: SSH, 80 e 443."
else
  echo "ufw não encontrado; configure o firewall do provedor para 22, 80 e 443."
fi

dizer "Backup diário do banco"
# O volume do Docker sobrevive a redeploy, não a perder a máquina. Este é o
# mínimo: um dump por dia, com 14 dias de histórico, no próprio servidor.
# Levar as cópias para FORA dele continua sendo necessário.
mkdir -p /var/backups/conectapet
cat > /usr/local/bin/conectapet-backup <<'FIM'
#!/usr/bin/env bash
set -euo pipefail
cd /opt/conectapet
arquivo="/var/backups/conectapet/conectapet-$(date +%F-%H%M).sql.gz"
docker compose exec -T banco \
  mysqldump -u root -p"${MYSQL_ROOT_PASSWORD}" --single-transaction --routines conectapet \
  | gzip > "$arquivo"
# Guarda 14 dias. Mais que isso ocupa a máquina; menos não cobre um problema
# que só se percebe na segunda-feira.
find /var/backups/conectapet -name '*.sql.gz' -mtime +14 -delete
FIM
chmod +x /usr/local/bin/conectapet-backup

cat > /etc/cron.d/conectapet-backup <<'FIM'
# 03:20, fora do horário de pico de leitura de tag.
20 3 * * * root . /opt/conectapet/.env && /usr/local/bin/conectapet-backup
FIM

dizer "Pronto"
cat <<'FIM'

O servidor está preparado. Faltam três passos, e eles são seus:

  1. Clonar o projeto em /opt/conectapet
       git clone <seu-repositorio> /opt/conectapet

  2. Preencher o .env
       cd /opt/conectapet
       cp .env.producao.example .env
       nano .env

     Gere os segredos, não invente:
       openssl rand -base64 48   # JWT_SEGREDO
       openssl rand -base64 32   # IP_PIMENTA
       openssl rand -base64 24   # as senhas do MySQL

  3. Subir
       docker compose up -d --build

Antes do passo 3, confirme que o DNS já aponta para este servidor:

       dig +short conectapet.com.br
       dig +short api.conectapet.com.br

Os dois precisam devolver o IP desta máquina. Sem isso o Caddy não consegue
emitir o certificado e fica tentando em laço.

FIM
