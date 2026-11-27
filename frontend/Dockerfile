# syntax=docker/dockerfile:1
# --- Build both apps (npm workspaces) --------------------------------------------------------------
FROM --platform=$BUILDPLATFORM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
COPY packages/shared/package.json packages/shared/
COPY apps/book/package.json apps/book/
COPY apps/admin/package.json apps/admin/
RUN npm ci --ignore-scripts
COPY . .
# Address of the public site, used by the administration's "Voir le site" link.
ARG PUBLIC_SITE_URL=/
ENV VITE_PUBLIC_SITE_URL=$PUBLIC_SITE_URL
RUN npm run build

# --- Serve: Nginx (book at /, administration at /admin/, API proxied to the backend) ---------------
FROM nginx:1.27-alpine
ENV ADMIN_ALLOW_FROM=all \
    MEDIA_MAX_BODY=110m \
    PUBLIC_HOST=portfolio-public \
    ADMIN_HOST=portfolio-admin
RUN rm /etc/nginx/conf.d/default.conf
COPY docker/templates /etc/nginx/templates
COPY docker/snippets /etc/nginx/snippets
COPY docker/40-admin-access.sh /docker-entrypoint.d/40-admin-access.sh
RUN chmod +x /docker-entrypoint.d/40-admin-access.sh
COPY --from=build /app/apps/book/dist /usr/share/nginx/book
COPY --from=build /app/apps/admin/dist /usr/share/nginx/admin
EXPOSE 80
HEALTHCHECK --interval=15s --timeout=3s --retries=5 CMD wget -qO- http://127.0.0.1/healthz || exit 1
