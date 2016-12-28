/*
 * Copyright (C) 2013 The Android Open Source Project
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

import com.droid.dx.cf.iface.FieldList;
import com.droid.dx.cf.iface.MethodList;
import com.droid.dx.rop.cst.Constant;
import com.droid.dx.rop.cst.CstBaseMethodRef;
import com.droid.dx.rop.cst.CstFieldRef;
import com.droid.dx.rop.cst.CstType;
import com.droid.dx.rop.type.Prototype;
import com.droid.dx.rop.type.StdTypeList;
import com.droid.dx.rop.type.TypeList;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Tool to find direct class references to other classes.
 */
public class ClassReferenceListBuilder {
    private static final String CLASS_EXTENSION = ".class";

    private final Path path;
    private final Set<String> classNames = new HashSet<String>();
    private final List<String> keepActivityList = new ArrayList<String>();

    public ClassReferenceListBuilder(Path path) {
        this.path = path;
    }

    /**
     * Kept for compatibility with the gradle integration, this method just forwards to
     * {@link com.droid.multidex.MainDexListBuilder#main(String[])}.
     *
     * @deprecated use {@link com.droid.multidex.MainDexListBuilder#main(String[])} instead.
     */
    @Deprecated
    public static void main(String[] args) {
        com.droid.multidex.MainDexListBuilder.main(args);
    }

    /**
     * @param filterFile Archive containing the class files resulting of the tracing, typically
     *                   this is the result of running ProGuard.
     */
    public void addRoots(String filterFile) throws IOException {
        File inputFilter = new File(filterFile);
        if (!inputFilter.exists())
            throw new FileNotFoundException("input filter file not found");

        List<String> classJarofRoots = new ArrayList<String>();
        BufferedReader reader = new BufferedReader(new FileReader(inputFilter));
        String classLine;
        while ((classLine = reader.readLine()) != null) {
            if (classLine != null) {
                classJarofRoots.add(classLine);
            }
        }
        reader.close();

        for (String name : classJarofRoots) {
            String className = name + CLASS_EXTENSION;
            com.droid.dx.cf.direct.DirectClassFile classFile = null;
            try {
                classFile = path.getClass(className);
            } catch (FileNotFoundException e) {
            }
            if (isActivity(classFile)) {
                getSuper(classFile, keepActivityList);
                keepActivityList.add(className);
            }
        }
        classNames.addAll(classJarofRoots);

        for (String keep : keepActivityList) {
            System.out.println(com.droid.multidex.MainDexListBuilder.TAG + "keep activity in main dex [ " + keep + "]");
        }

//        recursiveFind(classNames);
        while (true) {
            if (recursiveFind(classNames)) {
//                List<String> removed = new ArrayList<String>();
//                for (String name : classNames) {
//                    String className = name + CLASS_EXTENSION;
//                    DirectClassFile classFile;
//                    try {
//                        classFile = path.getClass(className);
//                    } catch (FileNotFoundException e) {
//                        continue;
//                    }
//                    if (isActivity(classFile) && !keepActivityList.contains(className)) {
//                        removed.add(name);
//                    }
//                }
//                classNames.removeAll(removed);
                break;
            }
        }

        /**
         * 查找匿名内部类
         */
        Set<String> noNameClass = new HashSet<String>();
        for (com.droid.multidex.ClassPathElement element : path.elements) {
            for (String className : element.list()) {
                if (className.indexOf(CLASS_EXTENSION) >= 0) {
                    for (String classFind : classNames) {
                        if (className.indexOf(classFind) >= 0) {
//                            System.out.println(MainDexListBuilder.TAG + "iterator classFind " + classFind + ", class " + className);
                            noNameClass.add(className.substring(0, className.indexOf(CLASS_EXTENSION)));
                        }
                    }
                } else {
                    System.out.println(com.droid.multidex.MainDexListBuilder.TAG + "iterator classFind not class " + className);
                }
            }
        }
        classNames.addAll(noNameClass);

        while (true) {
            if (recursiveFind(classNames)) {
                break;
            }
        }
    }

    private void getSuper(com.droid.dx.cf.direct.DirectClassFile classFile, List<String> supers) {
        if (classFile == null)
            return;

        CstType superClass = classFile.getSuperclass();
        if (superClass != null) {
            supers.add(superClass.getClassType().getClassName() + CLASS_EXTENSION);
            com.droid.dx.cf.direct.DirectClassFile superClassFile;
            try {
                superClassFile = path.getClass(superClass.getClassType().getClassName() + CLASS_EXTENSION);
            } catch (FileNotFoundException e) {
                return;
            }
            getSuper(superClassFile, supers);
        }
    }

    private boolean isActivity(com.droid.dx.cf.direct.DirectClassFile classFile) {
        if (classFile == null)
            return false;

        CstType superClass = classFile.getSuperclass();
        if (superClass != null) {
            if (superClass.getClassType().getClassName().indexOf("android/app/Activity") >= 0) {
                return true;
            } else {
                com.droid.dx.cf.direct.DirectClassFile superClassFile;
                try {
                    superClassFile = path.getClass(superClass.getClassType().getClassName() + CLASS_EXTENSION);
                } catch (FileNotFoundException e) {
                    return false;
                }
                return isActivity(superClassFile);
            }
        }
        return false;
    }

    private boolean recursiveFind(Set<String> keepClass) throws IOException {
        int size = classNames.size();
        List<String> keepList = new ArrayList<String>(keepClass);
        for (String name : keepList) {
            String className = name + CLASS_EXTENSION;
            com.droid.dx.cf.direct.DirectClassFile classFile;
            try {
                classFile = path.getClass(className);
            } catch (FileNotFoundException e) {
                throw new IOException("Class " + name +
                        " is missing form original class path " + path, e);
            }
//            if (isActivity(classFile)) {
//                keepActivityList.add(className);
//            }

            if (isActivity(classFile) && !keepActivityList.contains(className)) {
//                System.out.println("don't keep activity " + className);
                continue;
            }
            addDependencies(classFile);
        }
        return size == classNames.size();
    }

    Set<String> getClassNames() {
        return classNames;
    }

    private void addDependencies(com.droid.dx.cf.direct.DirectClassFile classFile) {
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

    private void checkPrototype(Prototype proto) {
        checkDescriptor(proto.getReturnType().getDescriptor());
        StdTypeList args = proto.getParameterTypes();
        for (int i = 0; i < args.size(); i++) {
            checkDescriptor(args.get(i).getDescriptor());
        }
    }

    private void checkDescriptor(String typeDescriptor) {
        if (typeDescriptor.endsWith(";")) {
            int lastBrace = typeDescriptor.lastIndexOf('[');
            if (lastBrace < 0) {
                addClassWithHierachy(typeDescriptor.substring(1, typeDescriptor.length() - 1));
            } else {
                assert typeDescriptor.length() > lastBrace + 3
                        && typeDescriptor.charAt(lastBrace + 1) == 'L';
                addClassWithHierachy(typeDescriptor.substring(lastBrace + 2,
                        typeDescriptor.length() - 1));
            }
        }
    }

    private void addClassWithHierachy(String classBinaryName) {
        if (classNames.contains(classBinaryName)) {
            return;
        }

        try {
            com.droid.dx.cf.direct.DirectClassFile classFile = path.getClass(classBinaryName + CLASS_EXTENSION);
            classNames.add(classBinaryName);
            CstType superClass = classFile.getSuperclass();
            if (superClass != null) {
                addClassWithHierachy(superClass.getClassType().getClassName());
            }

            TypeList interfaceList = classFile.getInterfaces();
            int interfaceNumber = interfaceList.size();
            for (int i = 0; i < interfaceNumber; i++) {
                addClassWithHierachy(interfaceList.getType(i).getClassName());
            }
        } catch (FileNotFoundException e) {
            // Ignore: The referenced type is not in the path it must be part of the libraries.
        }
    }

}
