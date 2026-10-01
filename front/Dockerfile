ARG DOCKER_REGISTRY=docker.io/library
FROM ${DOCKER_REGISTRY}/node:22-alpine AS build
WORKDIR /build

COPY package.json yarn.lock ./
RUN --mount=type=cache,target=/usr/local/share/.cache/yarn \
    corepack enable && yarn install --frozen-lockfile

COPY . .
RUN yarn build

FROM ${DOCKER_REGISTRY}/nginx:1.28-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist /usr/share/nginx/html
EXPOSE 80
