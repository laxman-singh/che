docker run  -v /var/run/docker.sock:/var/run/docker.sock -v "$PWD":"$PWD" --rm codenvy/che-file /bin/che $PWD $1

