# Shrinker

`Shrinker` will remove all R.class and R\$\*\*.class (except R\$styleable.class) and all constant integer fields will be inlined by [`asm`](http://asm.ow2.org/) and [`transform-api`](http://tools.android.com/tech-docs/new-build-system/transform-api). 

As of ADT 14, library project's R class are no longer declared resource as constant filelds. So that we will build a huge android project output apk with as many fields in dex as number of the android library dependencies.

## Usage 

To apply `shrinker` to your android application:

**Step1.** Add it in your `buildscript` section of app's build.gradle
```
buildscript {
    repositories {
        //...
        maven { url 'https://jitpack.io' }
    }

    dependencies {
        classpath 'net.yrom:shrinker:0.1.1'
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
