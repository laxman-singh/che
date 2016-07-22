#!/usr/bin/env node
/// <reference path='./typings/tsd.d.ts' />

// imports
import {RemoteIp} from './remoteip';
import {RecipeBuilder} from './recipebuilder';


// grab default hostname from the remote ip component
var DEFAULT_HOSTNAME: String = new RemoteIp().getIp();


var debug: boolean = false;
var times: number = 10;

// gloabl var
var waitDone = false;
var che: { hostname: String, server: any } = {hostname: DEFAULT_HOSTNAME, server: 'tmp'};
var dockerContent;

// requirements
var path = require('path');
var http = require('http');
var fs = require('fs');
var vm = require('vm');
var readline = require('readline');
var exec = require('child_process').exec;

function startsWith(value:String, searchString: String) : Boolean {
   return value.substr(0, searchString.length) === searchString;
}


// init folder/files variables
var currentFolder: String = path.resolve('./');
var folderName = path.basename(currentFolder);
var cheFile = path.resolve(currentFolder, 'chefile');
var dotCheFolder = path.resolve(currentFolder, '.che');
var confFolder = path.resolve(dotCheFolder, 'conf');
var workspacesFolder = path.resolve(dotCheFolder, 'workspaces');
var chePropertiesFile = path.resolve(confFolder, 'che.properties');

var mode;
var args = process.argv.slice(2);

analyzeArgs(args);

function analyzeArgs(args) {
  if (args.length == 0) {
  console.log('only init and up commands are supported.');
  return;
} else if ('init' === args[0]) {
  init();
} else if ('up' === args[0]) {
  up();
} else {
  console.log('Invalid arguments ' + args +': Only init and up commands are supported.');
  return;
}

}

function parse() {

    try {
        fs.statSync(cheFile);
        // we have a file
    } catch (e) {
        console.log('No chefile defined, use default settings');
        return;
    }

    // load the chefile script if defined
    var script_code = fs.readFileSync(cheFile);

    // setup the bindings for the script
    che.server =  {};
    che.server.ip = che.hostname;

    // create sandboxed object
    var sandbox = { "che": che, "console": console};

    var script = vm.createScript(script_code);
    script.runInNewContext(sandbox);

  if (debug) {
    console.log('Che file parsing object is ', che);
  }
}


function init() {
  // needs to create folders
  initCheFolders();
  setupConfigFile();

  console.log('Che configuration initialized in ' + dotCheFolder );
}

function up() {
    parse();

    // test if conf is existing
    try {
        var statsPropertiesFile = fs.statSync(chePropertiesFile);
    } catch (e) {
        console.log('No che configured. che init has been done ?');
        return;
    }

    console.log('Starting che');
    // needs to invoke docker run
    cheBoot();

    dockerContent = new RecipeBuilder().getDockerContent();

    // loop to check startup (during 30seconds)
    waitCheBoot();
}



// Create workspace based on the remote hostname and workspacename
// if custom docker content is provided, use it
function createWorkspace(remoteHostname, workspaceName, dockerContent) {
  var options = {
    hostname: remoteHostname,
    port: 8080,
    path: '/api/workspace?account=',
    method: 'POST',
    headers: {
      'Accept': 'application/json, text/plain, */*',
      'Content-Type': 'application/json;charset=UTF-8'
    }
  };
  var req = http.request(options, function(res) {
    res.on('data', function (body) {

      if (res.statusCode == 201) {
        // workspace created, continue
        displayUrlWorkspace(JSON.parse(body));
      } else {
        // error
        console.log('Invalid response from the server side. Aborting');
        console.log('response was ' + body);
      }
    });
  });
  req.on('error', function(e) {
    console.log('problem with request: ' + e.message);
  });

  var workspace = {
    "defaultEnv": "default",
    "commands": [],
    "projects": [],
    "environments": [{
      "machineConfigs": [{
        "dev": true,
        "servers": [],
        "envVariables": {},
        "limits": {"ram": 2500},
        "source": {"type": "dockerfile", "content": dockerContent},
        "name": "default",
        "type": "docker",
        "links": []
      }], "name": "default"
    }],
    "name": workspaceName,
    "links": [],
    "description": null
  };


  req.write(JSON.stringify(workspace));
  req.end();

}

function displayUrlWorkspace(workspace) {

  var found = false;
  var i = 0;
  var links = workspace.links;
  while (i < links.length && !found) {
    // display the ide url link
    var link = links[i];
    if (link.rel === 'ide url') {
      found = true;
      console.log('Open browser to ' + link.href);
    }
    i++;

  }

  if (!found) {
    console.log('Workspace successfully started but unable to find workspace link');
  }


}


function initCheFolders() {

  // create .che folder
  try {
    fs.mkdirSync(dotCheFolder, 0o744);
  } catch (e) {
    // already exist
  }

  // create .che/workspaces folder
  try {
    fs.mkdirSync(workspacesFolder, 0o744);
  } catch (e) {
    // already exist
  }

  // create .che/conf folder

  try {
    fs.mkdirSync(confFolder, 0o744);
  } catch (e) {
    // already exist
  }


  // copy configuration file

  try {
    var stats = fs.statSync(chePropertiesFile);
  } catch (e) {
    // file does not exist, copy it
    fs.writeFileSync(chePropertiesFile, fs.readFileSync(path.resolve(__dirname, 'che.properties')));
  }

}


function setupConfigFile() {
  // need to setup che.properties file with workspaces folder

  // update che.user.workspaces.storage
  updateConfFile('che.user.workspaces.storage', workspacesFolder);

  // update extra volumes
  updateConfFile('machine.server.extra.volume', currentFolder + ':/projects/' + folderName);

}


function updateConfFile(propertyName, propertyValue) {

  var content = '';
  var foundLine = false;
  fs.readFileSync(chePropertiesFile).toString().split('\n').forEach(function (line) {

    var updatedLine;



    if (startsWith(line, propertyName)) {
      foundLine = true;
      updatedLine = propertyName + '=' + propertyValue + '\n';
    } else {
      updatedLine = line  + '\n';
    }

    content += updatedLine;
  });

  // add property if not present
  if (!foundLine) {
    content += propertyName + '=' + propertyValue + '\n';
  }

  fs.writeFileSync(chePropertiesFile, content);

}

//  ' -e CHE_CONF_FOLDER=' + confFolder +

function cheBoot() {

  var commandLine: String = 'docker run ' +
      ' -v /var/run/docker.sock:/var/run/docker.sock' +
      ' -e CHE_DATA_FOLDER=' + workspacesFolder +
      ' -e CHE_CONF_FOLDER=' + confFolder +
      ' codenvy/che-launcher:nightly start';

  if (debug) {
    console.log('Executing command line', commandLine);
  }
  var child = exec(commandLine , function callback(error, stdout, stderr) {
    //console.log('error is ' + error, stdout, stderr);
      }
  );

  //if (debug) {
    child.stdout.on('data', function (data) {
      console.log(data.toString());
    });
  //}

}


// test if can connect on port 8080
function waitCheBoot() {

  if(times < 1) {
    return;
  }
  //console.log('wait che on boot', times);
  var options = {
    hostname: che.hostname,
    port: 8080,
    path: '/api/workspace',
    method: 'GET',
    headers: {
      'Accept': 'application/json, text/plain, */*',
      'Content-Type': 'application/json;charset=UTF-8'
    }
  };
  if (debug) {
    console.log('using che ping options', options, 'and docker content', dockerContent);
  }

  var req = http.request(options, function(res) {
    res.on('data', function (body) {

      if (res.statusCode === 200 && !waitDone) {
        waitDone = true;
        if (debug) {
          console.log('status code is 200, creating workspace');
        }
        createWorkspace(che.hostname, 'local', dockerContent);
      }
    });
  });
  req.on('error', function(e) {
    if (debug) {
      console.log('with request: ' + e.message);
    }
  });


  req.end();


  times--;
  if (times > 0 && !waitDone) {
    setTimeout(waitCheBoot, 5000);
  }


}
