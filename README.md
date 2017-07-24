# Shrinker

`Shrinker` will remove all R.class and R\$\*\*.class (except R\$styleable.class) and all constant integer fields will be inlined by [`asm`](http://asm.ow2.org/) and [`transform-api`](http://tools.android.com/tech-docs/new-build-system/transform-api). 

As of ADT 14, library project's R class are no longer declared resource as constant filelds. So that we will build a huge android project output apk with as many fields in dex as number of the android library dependencies.

## Usage 

![version](https://jitpack.io/v/net.yrom/shrinker.svg?style=flat-square) You can get `shrinker` from [jitpack](https://jitpack.io)

To apply `shrinker` to your android application:

**Step1.** Add it in your `buildscript` section of app's build.gradle
```
buildscript {
    repositories {
        //...
        maven { url 'https://jitpack.io' }
    }

    dependencies {
        classpath 'net.yrom:shrinker:0.1.5'
    }
}
```

**Step2.** Apply it **after** the Android plugin
```
apply plugin: 'com.android.application'
//...
apply plugin: 'net.yrom.shrinker'
```

**NOTE** `shrinker` plugin requires android gradle build tools version at least 2.3.0

## Showcase
Enable [`shrink code` option of proguard](https://developer.android.com/studio/build/shrink-code.html), and count methods by [dexcount-gradle-plugin](https://github.com/KeepSafe/dexcount-gradle-plugin)

Before:

> Total methods in app-release.apk: 124159 (189.45% used)  
> Total fields in app-release.apk:  **104996 (160.21% used)**  
> Methods remaining in app-release.apk: 0  
> Fields remaining in app-release.apk:  0

After:

> Total methods in app-release.apk: 124113 (189.38% used)  
> Total fields in app-release.apk:  **54093 (82.54% used)**  
> Methods remaining in app-release.apk: 0  
> Fields remaining in app-release.apk:  11442


## License
```
Copyright 2017 Yrom

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```