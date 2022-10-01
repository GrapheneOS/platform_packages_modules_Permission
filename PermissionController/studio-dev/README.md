## Permission Controller in Android Studio

```sh
$ ./packages/modules/Permission/PermissionController/studio-dev/studiow
```

### Setup
- Run this command to start android studio
```sh
$ ./packages/modules/Permission/PermissionController/studio-dev/studiow
```
- Make changes and run `PermissionController` run configuration from Android Studio.

### First run
- Make sure to have the rooted device connected. The script either use compiled local artifacts (preferred over pulling files from device) or pulls the dex files from the device and decompiles them to create an SDK with hidden APIs.
- If import settings dialog shows up in Android Studio, select do-not import.
- If sdk wizard shows up, cancel and select never run in future.
- If the project do not open (happens when sdk wizard shows up), close Android Studio and run `studiow` again.

### Updating SDK
If after a sync, you are unable to compile, it's probably because some API in framework changed and
you need to update your SDK. you should pass the `--update-sdk` flag.

```sh
$ ./packages/modules/Permission/PermissionController/studio-dev/studiow --update-sdk
```

For a platform checkout, you have the option to use the built SDK from your tree's out/ directory.
This SDK exists if you've previously built with `m`. The script will prefer to use the built SDK if
it exists, otherwise it will attempt to pull the SDK from the attached device. You can pass
`--pull-sdk` to override this behavior and _always_ pull the SDK from the attached device, whether
or not the built SDK exists.

For example:
- If you are using a platform checkout which you've never built, it will pull the SDK from the
  attached device.
- If you are using a platform checkout which you've built with `m`, it will use the SDK from the
  out/ directory. However, in this scenario, if you wanted to use the SDK from the attached device
  instead you can pass `--pull-sdk`.


## FAQ / Helpful info

This project is using android studio + gradle as a build system, and as such it can be tricky to bootstrap for a first time set up or after a sync. This section is for some common problem/solution pairs we run into and also some general things to try.

#### Make sure the sdk is updated after a sync

`./studiow --update-sdk` is your friend.

We pull the framework.jar (&friends) from the device and do some `<magic>` to compile against hidden framework APIs. Therefore, you have to build and flash the device to be current, and then pull the resulting jars from the device using the above command.

#### Android sdk choice

Android Studio shouldn't ask you to choose which Android SDK to use and will instead select the
right SDK automatically. But, [if it does ask](https://screenshot.googleplex.com/AtA62tTRyKWiSWg),
choose the **project** SDK (likely in the `.../<branchname>/out/gradle/MockSdk` directory),
**not** the Android SDK (likely in the `.../<username>/Android/Sdk` directory).

#### Some other things to think about:

1. Build > clean project
2. File > Invalidate caches & restart

#### Android Studio is not launching

If Android Studio fails to start when running studio wrapper you can try to launch the binary directly to see more logs.

After running studio wrapper once you can find the binary in `~/.AndroidStudioPC` directory.
