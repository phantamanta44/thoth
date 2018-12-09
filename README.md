# THOTH
Android file synchronization tool over ADB

## Building
You'll need to locally install [JADB from here](https://github.com/phantamanta44/jadb) first. Then you can just use Gradle to build it like any other app.

## Usage
First you'll want to create a `manifest.toml` file somewhere and give it two properties:

```toml
local = "path/to/some/dir"
remote = "another/path"
```

These properties define the folders to be synchronized on respectively the local machine and the Android device.

Next, plug in the device and start the application using the manifest. This can be done either by specifying the manifest file as a command-line argument or by running the application in the same working directory as the manifest.

Once the application opens, you'll see a dropdown box at the top listing all connected devices. Select the one you want to sync with and hit "diff". The file list in the middle of the app will now be populated with all the differences between your local folder and the remote folder.

Next, you'll want to look through the diff tree and resolve all the differences. This is done by selecting the check boxes next to each file to choose whether you want to keep the local or remote copy. You'll notice that some entries are highlighted in red; these denote files that are missing from either the local or remote folder. Selecting a check box in a red entry will cause the file to be deleted from the other side.

Once you've made all your selections, hit the "Apply Resolution" button and the folders will be automagically synchronized. 

## Caveats

* Sometimes hangs for no reason
* Sometimes fails to connect over ADB for no reason
* Currently only accounts for missing files and not different files
