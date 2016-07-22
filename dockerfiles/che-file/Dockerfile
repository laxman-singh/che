# Copyright (c) 2016 Codenvy, S.A.
# All rights reserved. This program and the accompanying materials
# are made available under the terms of the Eclipse Public License v1.0
# which accompanies this distribution, and is available at
# http://www.eclipse.org/legal/epl-v10.html
#
# To build, in this directory:
#  first, if there are changes in typescript, call `build.sh` or:
#  `tsc --outDir lib src/che.ts`
#  `docker build -t codenvy/che-file .`
#
# To use:
#  `docker run -v /var/run/docker.sock:/var/run/docker.sock \
#              -v "$PWD":"$PWD" --rm codenvy/che-file \
#              /bin/che $PWD [COMMAND]`
# 
#  where [COMMAND]:
#    init -- Initialize configuration files for this directory
#    up   -- Launch workspace, starting Che server, if necessary 
#
FROM mhart/alpine-node

ENV DOCKER_BUCKET get.docker.com
ENV DOCKER_VERSION 1.6.0

RUN set -x \
    && apk add --no-cache \
    ca-certificates \
    curl \
    openssl \
    && curl -sL "https://${DOCKER_BUCKET}/builds/Linux/x86_64/docker-$DOCKER_VERSION" \
    > /usr/bin/docker; chmod +x /usr/bin/docker

COPY lib /lib
COPY bin /bin

CMD ["node", "lib/che-file.js"]
