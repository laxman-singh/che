"use strict";
var RemoteIp = (function () {
    function RemoteIp() {
        var execSync = require('child_process').execSync;
        this.ip = execSync('docker run --net host --rm codenvy/che-ip').toString().replace(/[\n\r]/g, '');
    }
    RemoteIp.prototype.getIp = function () {
        return this.ip;
    };
    return RemoteIp;
}());
exports.RemoteIp = RemoteIp;
