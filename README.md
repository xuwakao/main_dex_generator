---
title: 异步加载multidex
date: 2016-04-22 01:52:16
tags: Android
---

这个东西想写很久了，一直忙，就放着不动，直到某天见到[这篇文章](http://mp.weixin.qq.com/s?__biz=MzA4MjU5NTY0NA==&mid=405574783&idx=1&sn=6ff49fda8a7229bf6b2692fddcf23e04&scene=4#wechat_redirect)，发觉整个琢磨的经过和实现，都如此的相似，然后又找文章的作者徐东聊了一下，然后就决定要把这个东西记录下来。

###Multidex背景
[官方文档](http://developer.android.com/tools/building/multidex.html)已经对这个做了比较详述的说明。
简单总结就是：早期dex执行文件的方法数限制在65536范围之内，如果超出这个限制，构建就会失败。

然而，为什么会构建失败，这个65536限制究竟是在哪里？既然dex文件构建失败，首先想到肯定就是去dx.jar找原因。
构建失败一般会有以下的日志：
{% codeblock %}
UNEXPECTED TOP-LEVEL EXCEPTION: java.lang.IllegalArgumentException: method ID not in [0, 0xffff]: 65536 
    at com.android.dx.merge.DexMerger$6.updateIndex(DexMerger.java:501) 
    at com.android.dx.merge.DexMerger$IdMerger.mergeSorted(DexMerger.java:276) 
    at com.android.dx.merge.DexMerger.mergeMethodIds(DexMerger.java:490) 
    at com.android.dx.merge.DexMerger.mergeDexes(DexMerger.java:167) 
    at com.android.dx.merge.DexMerger.merge(DexMerger.java:188) 
    at com.android.dx.command.dexer.Main.mergeLibraryDexBuffers(Main.java:439) 
    at com.android.dx.command.dexer.Main.runMonoDex(Main.java:287) 
    at com.android.dx.command.dexer.Main.run(Main.java:230) 
    at com.android.dx.command.dexer.Main.main(Main.java:199) 
    at com.android.dx.command.Main.main(Main.java:103):Derp:dexDerpDebug FAILED
{% endcodeblock %}

那么就去搜索“method ID not in”和根据错误信息提示的堆栈，果然在DexMerger的方法：

![mergeMethodIds](/images/multidex/0xffffff-limit.png)。

中搜索到这段。然后顺藤摸瓜，找到调用栈：

![调用栈](/images/multidex/dex-merger-hier.png)

最后红框的就是dx.jar的入口main函数，而且也和错误日志是一致的。

另外，虽然从这个堆栈看，65536的问题找到了，而事实上，关于65535这个限制的地方不止这一处，上面的限制是在多个dex merger的情况下发生的，然而，有些时候，即使只有一个dex文件，也同样出现这个问题，但是出错的日志并不相同，类似下面：
{% codeblock %}
Dx trouble writing output: Too many method references: 107085; max is 65536.
You may try using --multi-dex option.
{% endcodeblock %}

这种情况的出现，原因不详述，可以参考[这篇文章](http://jayfeng.com/2016/03/10/%E7%94%B1Android-65K%E6%96%B9%E6%B3%95%E6%95%B0%E9%99%90%E5%88%B6%E5%BC%95%E5%8F%91%E7%9A%84%E6%80%9D%E8%80%83/)，同样也是出现在dx.jar中。

针对这个，google官方推出了multidex的方案来解决这个构建的问题。

###又爱又恨的Multidex
虽然google推出了multidex，然而，这个一个令人头疼的方案。对此，官方明确指出了方案的limitation：
{% blockquote %}
1.The installation of .dex files during startup onto a device's data partition is complex and can result in **Application Not Responding (ANR)** errors if the secondary dex files are large. In this case, you should apply code shrinking techniques with ProGuard to minimize the size of dex files and remove unused portions of code.
2.Applications that use multidex may not start on devices that run versions of the platform earlier than Android 4.0 (API level 14) due to a Dalvik linearAlloc bug ([Issue 22586](https://code.google.com/p/android/issues/detail?id=22586)). If you are targeting API levels earlier than 14, make sure to perform testing with these versions of the platform as your application can have issues at startup or when particular groups of classes are loaded. Code shrinking can reduce or possibly eliminate these potential issues.
3.Applications using a multidex configuration that make very large memory allocation requests may crash during run time due to a Dalvik linearAlloc limit ([Issue 78035](https://code.google.com/p/android/issues/detail?id=78035)). The allocation limit was increased in Android 4.0 (API level 14), but apps may still run into this limit on Android versions prior to Android 5.0 (API level 21).
4.There are complex requirements regarding what classes are needed in the primary dex file when executing in the Dalvik runtime. The Android build tooling updates handle the Android requirements, but it is possible that other included libraries have additional dependency requirements including the use of introspection or invocation of Java methods from native code. Some libraries may not be able to be used until the multidex build tools are updated to allow you to specify classes that must be included in the primary dex file.
{% endblockquote %}


##[dex opt](https://android.googlesource.com/platform/dalvik/+/lollipop-release/docs/dexopt.html)

##[facebook指引](https://www.facebook.com/notes/facebook-engineering/under-the-hood-dalvik-patch-for-facebook-for-android/10151345597798920/)
