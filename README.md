# Shrinker

`Shrinker` will remove all R.class and R\$\*\*.class  and all constant integer fields will be inlined by [`asm`](http://asm.ow2.org/) and [`transform-api`](http://tools.android.com/tech-docs/new-build-system/transform-api). 

*I have post more details on my own blog (in Chinese), [click here](http://yrom.net/blog/2018/01/12/android-gradle-plugin-for-shrinking-fields-in-dex/) to check it out.*

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
        classpath 'net.yrom:shrinker:0.2.9'
    }
}
```

**Step2.** Apply it **after** the Android plugin
```
apply plugin: 'com.android.application'
//...
apply plugin: 'net.yrom.shrinker'
```

**NOTE** that `shrinker` plugin requires android gradle build tools version at least 3.0.0 and it will be disabled if run in debug build.

### Show case
There is a small [test](tree/master/test) application which depends on so many support libraries, would show how many fields `shrinked`. 

Run with `shrinker`:
```
./gradlew :test:assembleRelease -PENABLE_SHRINKER
```

Run with `removeUnusedCode`: 
```groovy
android {
    buildTypes {
        release {
            ...
            postprocessing {
                removeUnusedCode = true
            }
        }
    }
    ...
}
```

Content below counts by [dexcount-gradle-plugin](https://github.com/KeepSafe/dexcount-gradle-plugin)

| options                     | methods | fields | classes |
| --------------------------- | ------- | ------ | ------- |
| origin                      | 22164   | 14367  | 2563    |
| shrinker                    | 21979   | 7805   | 2392    |
| removeUnusedCode            | 11338   | 6655   | 1296    |
| shrinker & removeUnusedCode | 11335   | 3302   | 1274    |

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