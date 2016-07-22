"use strict";
var RecipeBuilder = (function () {
    function RecipeBuilder() {
        this.DEFAULT_DOCKERFILE_CONTENT = 'FROM codenvy/ubuntu_jdk8';
        this.path = require('path');
        this.fs = require('fs');
    }
    RecipeBuilder.prototype.getDockerContent = function () {
        // build path to the Dockerfile in current directory
        var dockerFilePath = this.path.resolve('./Dockerfile');
        // use synchronous API
        try {
            var stats = this.fs.statSync(dockerFilePath);
            console.log('Using a custom project Dockerfile \'' + dockerFilePath + '\' for the setup of the workspace.');
            var content = this.fs.readFileSync(dockerFilePath, 'utf8');
            return content;
        }
        catch (e) {
            // file does not exist, return default
            return this.DEFAULT_DOCKERFILE_CONTENT;
        }
    };
    return RecipeBuilder;
}());
exports.RecipeBuilder = RecipeBuilder;
