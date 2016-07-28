---
title: 异步加载multidex
date: 2016-04-22 01:52:16
tags: Android
---

文章写了很久很久，今天再次看到一个相关的项目的[Android-Easy-MultiDex](https://github.com/TangXiaoLv/Android-Easy-MultiDex)，那么我也把自己的方案分享一下，代码待放。。。

---------------------------------------------------------------------------

# Multidex背景
[官方文档](http://developer.android.com/tools/building/multidex.html)已经对这个做了比较详述的说明。
简单总结就是：**早期dex执行文件的方法数限制在65536范围之内，如果超出这个限制，构建就会失败**。

然而，为什么会构建失败，这个65536限制究竟是在哪里？既然dex文件构建失败，首先想到肯定就是去dx.jar找原因。
构建失败一般会有以下的日志：

```
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

```

那么就去搜索“method ID not in”和根据错误信息提示的堆栈，果然在DexMerger的方法：

![mergeMethodIds](/images/0xffffff-limit.png)。

中搜索到这段。然后顺藤摸瓜，找到调用栈：

![调用栈](/images/dex-merger-hier.png)

最后红框的就是dx.jar的入口main函数，而且也和错误日志是一致的。

另外，虽然从这个堆栈看，65536的问题找到了，而事实上，关于65535这个限制的地方不止这一处，上面的限制是在多个dex merger的情况下发生的，然而，有些时候，即使只有一个dex文件，也同样出现这个问题，但是出错的日志并不相同，类似下面：
{% codeblock %}
Dx trouble writing output: Too many method references: 107085; max is 65536.
You may try using --multi-dex option.
{% endcodeblock %}

这种情况的出现，原因不详述，可以参考[这篇文章](http://jayfeng.com/2016/03/10/%E7%94%B1Android-65K%E6%96%B9%E6%B3%95%E6%95%B0%E9%99%90%E5%88%B6%E5%BC%95%E5%8F%91%E7%9A%84%E6%80%9D%E8%80%83/)，同样也是出现在dx.jar中。

针对这个，google官方推出了multidex的方案来解决这个构建的问题。

# 又爱又恨的Multidex
虽然google推出了multidex，然而，这个一个令人头疼的方案。对此，官方明确指出了方案的limitation：

```
{% blockquote %}
**1.**The installation of .dex files during startup onto a device's data partition is complex and can result in **Application Not Responding (ANR)** errors if the secondary dex files are large. In this case, you should apply code shrinking techniques with ProGuard to minimize the size of dex files and remove unused portions of code.
**2.**Applications that use multidex may not start on devices that run versions of the platform earlier than Android 4.0 (API level 14) due to a Dalvik linearAlloc bug ([Issue 22586](https://code.google.com/p/android/issues/detail?id=22586)). If you are targeting API levels earlier than 14, make sure to perform testing with these versions of the platform as your application can have issues at startup or when particular groups of classes are loaded. Code shrinking can reduce or possibly eliminate these potential issues.
**3.**Applications using a multidex configuration that make very large memory allocation requests may crash during run time due to a Dalvik linearAlloc limit ([Issue 78035](https://code.google.com/p/android/issues/detail?id=78035)). The allocation limit was increased in Android 4.0 (API level 14), but apps may still run into this limit on Android versions prior to Android 5.0 (API level 21).
**4.**There are complex requirements regarding what classes are needed in the primary dex file when executing in the Dalvik runtime. The Android build tooling updates handle the Android requirements, but it is possible that other included libraries have additional dependency requirements including the use of introspection or invocation of Java methods from native code. Some libraries may not be able to be used until the multidex build tools are updated to allow you to specify classes that must be included in the primary dex file.
{% endblockquote %}

```

从上面提到的第一点可以知道，dex的install过程比较复杂，容易引起ANR的发生。ANR发生的原因很简单，莫非就是在UI线程作耗时操作。那为什么不在非UI线程做，如果在非UI线程进行dex的install过程，这个问题不就迎刃而解。理想很丰满，现实很骨感。

当尝试另起线程进行dex文件合并时，运行时如无意外就会发生ClassNotFoundExecption。原因很简单，当简单应用multidex方案的时候，dex文件只是进行简单拆分，不同classes会被分到不同的dex文件中。当你异步install dex文件时，应用初始只加载了第一个dex，也就是所谓的main dex，其他dex文件什么时候加载完成，不得而知，如果main dex中引用某个class，而这个class却在另一个没有install完成的dex文件中，自然就dalvik虚拟机中并不存在这个class。

那么该如何解决这个问题？业界自然会有方案。例如美团[这个方案](http://tech.meituan.com/mt-android-auto-split-dex.html)，例如腾讯的[方案](http://bugly.qq.com/bbs/forum.php?mod=viewthread&tid=193)。可惜，方案都停留在这篇文章上，并没有开源出来分享。
但是，其实莫非就一个关键点：**按需生成main dex**。
如果能够按自己的要求，把特定的某些classes放到main dex中，例如程序启动部分代码类，一级页面类等，其他类可以放到另外dex，然后通过程序控制，保证在使用其他dex文件中的classes之前，dex的install过程完成，那么整个方案就pass了。

# 异步multidex实现（基于gradle构建）

首先，要深入认识apk打包的整个流程：

![APK打包流程](/images/android_packaging.png)

既然，需要定制dex生成，就必须要搞清楚dx命令，因为它就是提供multidex支持的最根本的地方：

![dx命令](/images/dx-command.png)

从dx命令的图看出，multidex的支持就是dx提供的。仔细看参数:
```
{% codeblock %}
--main-dex-list=<file>
{% endcodeblock %}
```
该参数实际意义就是，可以指定main dex的里面包含什么classes文件，那么它就是实现方案的基础。

既然dx支持按需生成main dex，那么，如何产生main dex的classes列表就是**最最最核心的问题**了。

这个问题困扰了很久，实现这个有两种办法：
1. 每次打包，都运行一次，然后通过程序，找出程序启动后固定时间内（例如直到第一个界面resume）的所有classes。类似这里提到的[方案](https://medium.com/groupon-eng/android-s-multidex-slows-down-app-startup-d9f10b46770f#.ogmk4ytsu)。

2. 编译过程，就通过程序或者脚本，分析出依赖来生成main dex。

第一个方案可行，但是很不理想，自动化太差。毕竟特别对于release版本牵涉到proguard的问题，要进行重新mapping的处理，才能找到所需class文件，而且需要二次打包，显得过于笨拙。

**第二个方案**，无疑更加优秀。那么，怎样通过程序或者脚本生成main dex文件，或者说，怎么生成main dex list clases来产生main dex呢？

然后就想到，既然android gralde plugin是集成支持multidex的，那么它自然就有整个类似的过程。

然后就不断反复查看构建的日志![构建日志](/images/find-build-log.png)，根据字面意思，发现几个关键的task:
```
{% codeblock %}
collectReleaseMultiDexComponents
packageAllReleaseClassesForMultiDex
shrinkReleaseMultiDexComponents
createReleaseMainDexClassList
{% endcodeblock %}
```

然后一个个task来分析。
### 1.collectReleaseMultiDexComponents
不断搜索，看到[这篇文章](http://blog.osom.info/2014/12/too-many-methods-in-main-dex.html)，然后清楚了该task对应的源码在[这里](https://android.googlesource.com/platform/tools/base/+/master/build-system/gradle-core/src/main/groovy/com/android/build/gradle/internal/tasks/multidex/CreateManifestKeepList.groovy)

从源码确认，该task的任务是，根据manifest，keep住activity, service,broadcastreceiver,provider,instrumentation,application等，文件输出在：
build\intermediates\multi-dex\release\manifest_keep.txt

### 2.packageAllReleaseClassesForMultiDex

这个task很简单，从日志看，就是打包所有的jar，输出在：
build\intermediates\multi-dex\release\allclasses.jar

这个task的源码在[这里](https://android.googlesource.com/platform/tools/base/+/master/build-system/gradle-core/src/main/groovy/com/android/build/gradle/internal/tasks/multidex/JarMergingTask.groovy)

### 3.shrinkReleaseMultiDexComponents

从日志看：
![shrinkReleaseMultiDexComponents日志](/images/shrink-classes.png)
该task实际执行的就是proguard的shrink的过程。

task的输出是build\intermediates\multi-dex\release\componentClasses.jar，这个jar的生成，是根据上面的task 1生成的manifest_keep.txt和task 2生成的classes.jar，可能还加上proguard文件，通过proguard shrink生成的。关于proguard shrink的详细内容就不展开叙述，可以参看相关资料，例如[android官网](http://developer.android.com/tools/help/proguard.html)、[proguard](http://proguard.sourceforge.net/index.html#manual/usage.html)。

### 4.createReleaseMainDexClassList

这个task的[源码](https://android.googlesource.com/platform/tools/base/+/master/build-system/gradle-core/src/main/groovy/com/android/build/gradle/internal/tasks/multidex/CreateMainDexList.groovy)。
它的主要作用是，根据上面task 3产生的componentsClasses.jar和task 2产生的allclasses.jar，最终生成main dex list，包含了所有main dex的class文件。输出在build\intermediates\multi-dex\release\maindexlist.txt

到此，就搞清楚了整个android gradle plugin关于multidex的流程了。
那么问题来了，怎么能够按照自己的需要，产生自己的main dex list呢？
其实产生main dex list的关键在task 3和task 4，因为main dex中究竟keep住哪些类，是根据task3产生的componentClasses.jar来的，而componentClasses.jar的class是作为root class存在，然后在task 4中找出这些root class相关联的classes。
那么这个分析过程是如何的呢？再看日志。
```
{% codeblock %}
15:08:28.098 [DEBUG] [org.gradle.api.internal.tasks.execution.ExecuteAtMostOnceTaskExecuter] Starting to execute task ':floor:createReleaseMainDexClassList'
15:08:28.099 [DEBUG] [org.gradle.api.internal.tasks.execution.SkipUpToDateTaskExecuter] Determining if task ':floor:createReleaseMainDexClassList' is up-to-date
15:08:28.100 [INFO] [org.gradle.api.internal.tasks.execution.SkipUpToDateTaskExecuter] Executing task ':floor:createReleaseMainDexClassList' (up-to-date check took 0.001 secs) due to:
  No history is available.
15:08:28.100 [DEBUG] [org.gradle.api.internal.tasks.execution.ExecuteActionsTaskExecuter] Executing actions for task ':floor:createReleaseMainDexClassList'.
15:08:28.101 [INFO] [org.gradle.process.internal.DefaultExecHandle] Starting process 'command 'C:\Program Files\Java\jdk1.7.0_79\bin\java.exe''. Working directory: D:\xxxx\xxxxx\floor Command: C:\Program Files\Java\jdk1.7.0_79\bin\java.exe -Dfile.encoding=UTF-8 -Duser.country=CN -Duser.language=zh -Duser.variant -cp C:\Users\Administrator\AppData\Local\Android\android-sdk\build-tools\23.0.1\lib\dx.jar com.android.multidex.ClassReferenceListBuilder D:\xxxx\xxxx\floor\build\intermediates\multi-dex\release\componentClasses.jar D:\xxxx\xxxx\floor\build\intermediates\multi-dex\release\allclasses.jar
{% endcodeblock %}
```

从日志得知，分析的过程是执行了dx.jar里面的[ClassReferenceListBuilder](https://android.googlesource.com/platform/dalvik/+/master/dx/src/com/android/multidex/ClassReferenceListBuilder.java)。经过一番查看，看到下面这段关键代码：
```
{% codeblock %}
        /**
     * @param jarOfRoots Archive containing the class files resulting of the tracing, typically
     * this is the result of running ProGuard.
     */
    public void addRoots(ZipFile jarOfRoots) throws IOException {
        // keep roots
        for (Enumeration<? extends ZipEntry> entries = jarOfRoots.entries();
                entries.hasMoreElements();) {
            ZipEntry entry = entries.nextElement();
            String name = entry.getName();
            if (name.endsWith(CLASS_EXTENSION)) {
                classNames.add(name.substring(0, name.length() - CLASS_EXTENSION.length()));
            }
        }
        // keep direct references of roots (+ direct references hierarchy)
        for (Enumeration<? extends ZipEntry> entries = jarOfRoots.entries();
                entries.hasMoreElements();) {
            ZipEntry entry = entries.nextElement();
            String name = entry.getName();
            if (name.endsWith(CLASS_EXTENSION)) {
                DirectClassFile classFile;
                try {
                    classFile = path.getClass(name);
                } catch (FileNotFoundException e) {
                    throw new IOException("Class " + name +
                            " is missing form original class path " + path, e);
                }
                addDependencies(classFile);
            }
        }
    }
    Set<String> getClassNames() {
        return classNames;
    }
    private void addDependencies(DirectClassFile classFile) {
        for (Constant constant : classFile.getConstantPool().getEntries()) {
            if (constant instanceof CstType) {
                checkDescriptor(((CstType) constant).getClassType().getDescriptor());
            } else if (constant instanceof CstFieldRef) {
                checkDescriptor(((CstFieldRef) constant).getType().getDescriptor());
            } else if (constant instanceof CstBaseMethodRef) {
                checkPrototype(((CstBaseMethodRef) constant).getPrototype());
            }
        }
        FieldList fields = classFile.getFields();
        int nbField = fields.size();
        for (int i = 0; i < nbField; i++) {
          checkDescriptor(fields.get(i).getDescriptor().getString());
        }
        MethodList methods = classFile.getMethods();
        int nbMethods = methods.size();
        for (int i = 0; i < nbMethods; i++) {
          checkPrototype(Prototype.intern(methods.get(i).getDescriptor().getString()));
        }
    }

{% endcodeblock %}
```

通过查看dx.jar源码，最后确定task 4的依赖分析过程是，根据[class字节码](https://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html)的[constant pool](https://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.4)，找出类依赖，这些依赖包括super class，fields，methods，interfaces中出现的类依赖。而仔细看，这里的依赖分析仅仅是分析root classes的依赖，而root classes依赖的class的依赖是不包含在分析结果中，这就是我们异步加载multidex的时候出现ClassNotFounedException的**主要原因**。

那怎么解决呢，很简单，我们只需要循环执行依赖分析，那么这个问题就迎刃而解。循环执行就是说，把root classes的依赖classes又作为root classes去分析，如此循环，直到形成闭环。当然，这会存在极端情况就是，工程内所有classes都存在相互依赖，但这个不是坏处，而是说明程序写的太完美，一点多余的classes都不存在，而事实上，这种情况是基本不可能存在。

循着这个想法，就把dx.jar的相关源码拉下来，然后基于dx.jar实现自己的依赖分析。
而且，由于是循环依赖分析，所以输入根本不需要componentsClasses.jar作为输入，只需要指定几个**入口类**（写在配置文件中，作为依赖分析的input file），就可以完成整个分析过程，例如application，homeactivity等入口类。

在我自己的工程中，通过这个简单方法，就已经把main dex的方法数，控制在65535之下，而且，基于此，就可以实现multidex的异步install了。

当然，这个依赖分析很简单，仅仅是把一些程序中没有引用到的类（**不一定是完全没有引用到的类，有可能是反射访问的，有可能是通过xml定义的自定义layout等情况**）剔除到main dex外，没有做到完全的按需分配main dex。
但是，对此改进也很简单，参考美团的方案，我们可以把一级activity放到maindex，其他的activity放到其他dex中，我们只需要配置一个过滤列表，例如，非一级activity的activity类不能作为root classes去分析，然后代码上的activity跳转不要使用类似Intent(mContext, xxxx.class)的方法，而改为使用字符串。
或者，更直接的就是，可以在代码上做修改，直接不要引用二级activity。

依赖分析的dx.jar修改完成，然后就着手修改gradle的script。。。。
脚本修改完，就是程序代码修改。

代码修改主要关注下面的几个点：
### 1.当然是要有异步install dex；
### 2.参考美团的实现，在二级页面没有加载完成之前，跳转一个中转的activity，直到二级页面加载完成；(这里使了点坏，替换了ActivityThread的mInstrument为自己的自定义对象，从而实现activity的跳转拦截)
### 3.不参考美团实现，像QQ（据了解，貌似新安装qq时，先跳转一个loading的界面，事实执行的就是multidex的install，待确认？）那样，新安装应用时，在splash activity的时候显示进度条，等待install完成

上面的这些修改，都会在工程中体现出来，**工程地址**：xxxxxxxxxxx。

至此，整个方案就算完成了，基本实现了异步加载multidex的想法。

下面还有些问题是需要注意的：
### 1.xml布局文件如果使用到某些自定义类，最好是在程序中引用一下，不然，无法分析出依赖，或者直接把该类添加到依赖分析的input file中；
### 2.一级页面中通过反射调用的类，也要添加到依赖分析的input file中；
### 3.manifest中的receiver最好都添加到依赖分析的,因为receiver有可能拉起App；
### 4.参考美团实现中，hack Instrumentation的过程中发现，可能存在兼容性问题（实际测试了十多款手机，只有在小米2s上出现问题）。例如，在小米系统（api=16， 4.1.1）上，重载Instrucmentation的execStartActivity不被调用，发现Activity的mInstrucmentation field的类根本就不是Instrucmentation，所以导致没调用，甚至于用instanceof判断该对象是不是Instrumenttation对象都是true，简直不忍直视。。。证据:
![suck](/images/xiaomi-suck-1.png)
![suck](/images/xiaomi-suck-3.png)
![suck](/images/xiaomi-suck-2.png)
### 5.改进dx.jar的依赖分析，可以完全地实现按需分配classes到main dex中；
### 6.关注gradle plugin的版本（这里使用1.3.1），如果后续的版本有修改，可能gradle脚本也要进行相应修改；
### 7.该方案不能解决Dalvik linearAlloc bug的issue，但是如果把main dex list控制得好，也就不是问题;

参考：
http://blog.osom.info/2014/10/generating-main-dex-list-file.html
[dex opt](https://android.googlesource.com/platform/dalvik/+/lollipop-release/docs/dexopt.html)
[facebook指引](https://www.facebook.com/notes/facebook-engineering/under-the-hood-dalvik-patch-for-facebook-for-android/10151345597798920/)
http://bugly.qq.com/bbs/forum.php?mod=viewthread&tid=193
http://mp.weixin.qq.com/s?__biz=MzA4MjU5NTY0NA==&mid=405574783&idx=1&sn=6ff49fda8a7229bf6b2692fddcf23e04&scene=4#wechat_redirect
http://tech.meituan.com/mt-android-auto-split-dex.html
https://medium.com/groupon-eng/android-s-multidex-slows-down-app-startup-d9f10b46770f#.im2boothq
