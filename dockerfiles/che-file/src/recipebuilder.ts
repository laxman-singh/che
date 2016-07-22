export class RecipeBuilder {

    DEFAULT_DOCKERFILE_CONTENT: String = 'FROM codenvy/ubuntu_jdk8';
    path: any;
    fs: any;

    constructor() {
        this.path = require('path');
        this.fs = require('fs');
    }


    getDockerContent() : String {

        // build path to the Dockerfile in current directory
        var dockerFilePath = this.path.resolve('./Dockerfile');

        // use synchronous API
        try {
            var stats = this.fs.statSync(dockerFilePath);
            console.log('Using a custom project Dockerfile \'' + dockerFilePath + '\' for the setup of the workspace.');
            var content = this.fs.readFileSync(dockerFilePath, 'utf8');
            return content;
        } catch (e) {
            // file does not exist, return default
            return this.DEFAULT_DOCKERFILE_CONTENT;
        }

    }

}