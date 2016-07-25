docker run --rm -v $(pwd):/home/developer/workspace jare/typescript tsc --outDir /home/developer/workspace/lib /home/developer/workspace/src/che-file.ts
docker build -t codenvy/che-file .
