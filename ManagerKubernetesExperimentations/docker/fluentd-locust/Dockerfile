# fluentd/Dockerfile
FROM fluent/fluentd:v1.7-debian-1
USER root
MAINTAINER Guillaume Rosinosky <guillaumerosinosky@yahoo.fr>

RUN buildDeps="sudo make gcc g++ libc-dev default-libmysqlclient-dev libmariadbclient18" \
 && apt-get update \
 && apt-get install -y --no-install-recommends $buildDeps \
 && sudo gem install tzinfo -v 1.1 \
 && sudo gem install fluent-plugin-mongo \
 && sudo gem install mysql2-cs-bind \
 && sudo gem install fluent-plugin-sql \
 && sudo gem sources --clear-all \
 && SUDO_FORCE_REMOVE=yes \
    apt-get purge -y --auto-remove \
                  -o APT::AutoRemove::RecommendsImportant=false \
                  $buildDeps \
 && rm -rf /var/lib/apt/lists/* \
 && rm -rf /tmp/* /var/tmp/* /usr/lib/ruby/gems/*/cache/*.gem


USER fluent
