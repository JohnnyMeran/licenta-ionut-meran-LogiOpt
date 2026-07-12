# runtime stage
# dist/ se construiește pe host (`npm run build`) — npm pică în container cu "Exit handler never called".
# Bundle-ul apelează calea relativă /api, pe care nginx-ul de mai jos o proxează către backend.
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY dist /usr/share/nginx/html
EXPOSE 80
