export class RemoteIp {

    ip: String;

    constructor() {
        var execSync = require('child_process').execSync;
        this.ip = execSync('docker run --net host --rm codenvy/che-ip').toString().replace(/[\n\r]/g, '');
    }


   getIp() : String {
       return this.ip;
   }

}