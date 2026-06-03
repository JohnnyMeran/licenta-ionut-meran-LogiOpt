# Deploy LogiOpt pe server

Acest pachet folosește nume și porturi separate ca să nu se încurce cu proiectele existente:

- `logiopt-frontend` pe `127.0.0.1:3010`
- `logiopt-backend` pe `127.0.0.1:8090`
- rețea Docker separată: `logiopt-network`
- domeniu nginx: `licenta-meran-ionut.site`

## 1. Copiere proiect pe server

Exemplu:

```bash
mkdir -p /opt/logiopt
cd /opt/logiopt
# dezarhivezi aici conținutul proiectului
```

Structura trebuie să fie:

```text
/opt/logiopt/docker-compose.prod.yml
/opt/logiopt/frontend/Dockerfile
/opt/logiopt/backend/Dockerfile
/opt/logiopt/deploy/nginx/licenta-meran-ionut.site.conf
```

## 2. Build și pornire containere

```bash
cd /opt/logiopt
docker compose -f docker-compose.prod.yml up -d --build
```

Verificare:

```bash
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
curl http://127.0.0.1:3010
curl http://127.0.0.1:8090/api/scenario/demo
```

## 3. Configurare nginx host

```bash
sudo cp /opt/logiopt/deploy/nginx/licenta-meran-ionut.site.conf /etc/nginx/sites-available/licenta-meran-ionut.site
sudo ln -s /etc/nginx/sites-available/licenta-meran-ionut.site /etc/nginx/sites-enabled/licenta-meran-ionut.site
sudo nginx -t
sudo systemctl reload nginx
```

## 4. SSL cu Certbot

```bash
sudo certbot --nginx -d licenta-meran-ionut.site -d www.licenta-meran-ionut.site
```

## 5. Update ulterior

```bash
cd /opt/logiopt
git pull # dacă folosești git, altfel copiezi noile fișiere
docker compose -f docker-compose.prod.yml up -d --build
```

## 6. Stop fără să afectezi alte proiecte

```bash
cd /opt/logiopt
docker compose -f docker-compose.prod.yml down
```

Nu folosește porturile tale existente `3001`, `3002`, `3003`, `5432`, `5433`, `8081`.
