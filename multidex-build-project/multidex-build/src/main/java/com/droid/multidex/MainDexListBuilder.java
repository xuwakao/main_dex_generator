/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.droid.multidex;

import com.droid.dx.cf.iface.Attribute;

import java.io.*;
import java.util.HashSet;
import java.util.Set;

/**
 * This is a command line tool used by mainDexClasses script to build a main dex classes list. First
 * argument of the command line is an archive, each class file contained in this archive is used to
 * identify a class that can be used during secondary dex installation, those class files
 * are not opened by this tool only their names matter. Other arguments must be zip files or
 * directories, they constitute in a classpath in with the classes named by the first argument
 * will be searched. Each searched class must be found. On each of this classes are searched for
 * their dependencies to other classes. The tool also browses for classes annotated by runtime
 * visible annotations and adds them to the list/ Finally the tools prints on standard output a list
 * of class files names suitable as content of the file argument --main-dex-list of dx.
 */
public class MainDexListBuilder {
    public static final String TAG = "[MainDexGen]";

    private static final String CLASS_EXTENSION = ".class";

    private static final int STATUS_ERROR = 1;

    private static final String EOL = System.getProperty("line.separator");

    private static String USAGE_MESSAGE =
            "Usage:" + EOL + EOL +
                    "Short version: Don't use this." + EOL + EOL +
                    "Slightly longer version: This tool is used by mainDexClasses script to build" + EOL +
                    "the main dex list." + EOL;

    /**
     * By default we force all classes annotated with runtime annotation to be kept in the
     * main dex list. This option disable the workaround, limiting the index pressure in the main
     * dex but exposing to the Dalvik resolution bug. The resolution bug occurs when accessing
     * annotations of a class that is not in the main dex and one of the annotations as an enum
     * parameter.
     *
     * @see <a href="https://code.google.com/p/android/issues/detail?id=78144">bug discussion</a>
     */
    private static final String DISABLE_ANNOTATION_RESOLUTION_WORKAROUND =
            "--disable-annotation-resolution-workaround";

    private Set<String> filesToKeep = new HashSet<String>();

    private static String outputPath;
    private static String inputFilter;

    public static void main(String[] args) {

        int argIndex = 0;
        boolean keepAnnotated = true;
        while (argIndex < args.length - 3) {
            if (args[argIndex].equals(DISABLE_ANNOTATION_RESOLUTION_WORKAROUND)) {
                keepAnnotated = false;
            } else {
                System.err.println("Invalid option " + args[argIndex]);
                printUsage();
                System.exit(STATUS_ERROR);
            }
            argIndex++;
        }
        if (args.length - argIndex != 3) {
            printUsage();
            System.exit(STATUS_ERROR);
        }

        try {
            inputFilter = args[argIndex + 1];
            outputPath = args[argIndex + 2];
            MainDexListBuilder builder = new MainDexListBuilder(keepAnnotated,
                    args[argIndex]);
            System.out.println("----------------" + TAG + "generate main dex internal begin----------------------");
            System.out.println("--" + TAG + "generate main dex internal classesJar " + args[argIndex] + " -------");
            System.out.println("--" + TAG + "generate main dex internal inputFilter " + inputFilter + " -------");
            System.out.println("--" + TAG + "generate main dex internal outputPath " + outputPath + " -------");
            System.out.println("----------------" + TAG + "generate main dex internal end-------------------------");
            Set<String> toKeep = builder.getMainDexList();
            printList(toKeep);
        } catch (IOException e) {
            System.err.println(TAG + "A fatal error occured: " + e.getMessage());
            System.exit(STATUS_ERROR);
            return;
        }
    }

    public MainDexListBuilder(boolean keepAnnotated, String pathString)
            throws IOException {
        Path path = null;
        try {
            path = new Path(pathString);

            ClassReferenceListBuilder mainListBuilder = new ClassReferenceListBuilder(path);
            mainListBuilder.addRoots(inputFilter);
            for (String className : mainListBuilder.getClassNames()) {
                filesToKeep.add(className + CLASS_EXTENSION);
            }
            if (keepAnnotated) {
                keepAnnotated(path);
            }
        } finally {
        }
        if (path != null) {
            for (ClassPathElement element : path.elements) {
                try {
                    element.close();
                } catch (IOException e) {
                    // keep going, lets do our best.
                }
            }
        }
    }

    /**
     * Returns a list of classes to keep. This can be passed to dx as a file with --main-dex-list.
     */
    public Set<String> getMainDexList() {
        return filesToKeep;
    }

    private static void printUsage() {
        System.err.print(USAGE_MESSAGE);
    }

    private static void printList(Set<String> fileNames) throws IOException {
        File file = new File(outputPath);
        if (file.exists())
            file.delete();
        file.createNewFile();
        BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(file));
        for (String fileName : fileNames) {
//            System.out.println(fileName);
            bos.write((fileName + "\n").getBytes());
        }
        bos.flush();
        bos.close();
        System.out.println(TAG + "keep file size " + fileNames.size());
    }

    /**
     * Keep classes annotated with runtime annotations.
     */
    private void keepAnnotated(Path path) throws FileNotFoundException {
        for (ClassPathElement element : path.getElements()) {
            forClazz:
            for (String name : element.list()) {
                if (name.endsWith(CLASS_EXTENSION)) {
                    com.droid.dx.cf.direct.DirectClassFile clazz = path.getClass(name);
                    if (hasRuntimeVisibleAnnotation(clazz)) {
                        filesToKeep.add(name);
                    } else {
                        com.droid.dx.cf.iface.MethodList methods = clazz.getMethods();
                        for (int i = 0; i < methods.size(); i++) {
                            if (hasRuntimeVisibleAnnotation(methods.get(i))) {
                                filesToKeep.add(name);
                                continue forClazz;
                            }
                        }
                        com.droid.dx.cf.iface.FieldList fields = clazz.getFields();
                        for (int i = 0; i < fields.size(); i++) {
                            if (hasRuntimeVisibleAnnotation(fields.get(i))) {
                                filesToKeep.add(name);
                                continue forClazz;
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean hasRuntimeVisibleAnnotation(com.droid.dx.cf.iface.HasAttribute element) {
        Attribute att = element.getAttributes().findFirst(
                com.droid.dx.cf.attrib.AttRuntimeVisibleAnnotations.ATTRIBUTE_NAME);
        return (att != null && ((com.droid.dx.cf.attrib.AttRuntimeVisibleAnnotations) att).getAnnotations().size() > 0);
    }
}
