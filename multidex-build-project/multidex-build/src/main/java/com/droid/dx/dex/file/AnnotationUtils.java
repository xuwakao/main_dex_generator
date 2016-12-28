/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.droid.dx.dex.file;

import com.droid.dx.rop.annotation.Annotation;
import com.droid.dx.rop.annotation.NameValuePair;
import com.droid.dx.rop.cst.CstKnownNull;
import com.droid.dx.rop.annotation.AnnotationVisibility;

import java.util.ArrayList;

/**
 * Utility class for dealing with annotations.
 */
public final class AnnotationUtils {

    /** {@code non-null;} type for {@code AnnotationDefault} annotations */
    private static final com.droid.dx.rop.cst.CstType ANNOTATION_DEFAULT_TYPE =
        com.droid.dx.rop.cst.CstType.intern(com.droid.dx.rop.type.Type.intern("Ldalvik/annotation/AnnotationDefault;"));

    /** {@code non-null;} type for {@code EnclosingClass} annotations */
    private static final com.droid.dx.rop.cst.CstType ENCLOSING_CLASS_TYPE =
        com.droid.dx.rop.cst.CstType.intern(com.droid.dx.rop.type.Type.intern("Ldalvik/annotation/EnclosingClass;"));

    /** {@code non-null;} type for {@code EnclosingMethod} annotations */
    private static final com.droid.dx.rop.cst.CstType ENCLOSING_METHOD_TYPE =
        com.droid.dx.rop.cst.CstType.intern(com.droid.dx.rop.type.Type.intern("Ldalvik/annotation/EnclosingMethod;"));

    /** {@code non-null;} type for {@code InnerClass} annotations */
    private static final com.droid.dx.rop.cst.CstType INNER_CLASS_TYPE =
        com.droid.dx.rop.cst.CstType.intern(com.droid.dx.rop.type.Type.intern("Ldalvik/annotation/InnerClass;"));

    /** {@code non-null;} type for {@code MemberClasses} annotations */
    private static final com.droid.dx.rop.cst.CstType MEMBER_CLASSES_TYPE =
        com.droid.dx.rop.cst.CstType.intern(com.droid.dx.rop.type.Type.intern("Ldalvik/annotation/MemberClasses;"));

    /** {@code non-null;} type for {@code Signature} annotations */
    private static final com.droid.dx.rop.cst.CstType SIGNATURE_TYPE =
        com.droid.dx.rop.cst.CstType.intern(com.droid.dx.rop.type.Type.intern("Ldalvik/annotation/Signature;"));

    /** {@code non-null;} type for {@code Throws} annotations */
    private static final com.droid.dx.rop.cst.CstType THROWS_TYPE =
        com.droid.dx.rop.cst.CstType.intern(com.droid.dx.rop.type.Type.intern("Ldalvik/annotation/Throws;"));

    /** {@code non-null;} the UTF-8 constant {@code "accessFlags"} */
    private static final com.droid.dx.rop.cst.CstString ACCESS_FLAGS_STRING = new com.droid.dx.rop.cst.CstString("accessFlags");

    /** {@code non-null;} the UTF-8 constant {@code "name"} */
    private static final com.droid.dx.rop.cst.CstString NAME_STRING = new com.droid.dx.rop.cst.CstString("name");

    /** {@code non-null;} the UTF-8 constant {@code "value"} */
    private static final com.droid.dx.rop.cst.CstString VALUE_STRING = new com.droid.dx.rop.cst.CstString("value");

    /**
     * This class is uninstantiable.
     */
    private AnnotationUtils() {
        // This space intentionally left blank.
    }

    /**
     * Constructs a standard {@code AnnotationDefault} annotation.
     *
     * @param defaults {@code non-null;} the defaults, itself as an annotation
     * @return {@code non-null;} the constructed annotation
     */
    public static Annotation makeAnnotationDefault(Annotation defaults) {
        Annotation result = new Annotation(ANNOTATION_DEFAULT_TYPE, AnnotationVisibility.SYSTEM);

        result.put(new NameValuePair(VALUE_STRING, new com.droid.dx.rop.cst.CstAnnotation(defaults)));
        result.setImmutable();
        return result;
    }

    /**
     * Constructs a standard {@code EnclosingClass} annotation.
     *
     * @param clazz {@code non-null;} the enclosing class
     * @return {@code non-null;} the annotation
     */
    public static Annotation makeEnclosingClass(com.droid.dx.rop.cst.CstType clazz) {
        Annotation result = new Annotation(ENCLOSING_CLASS_TYPE, AnnotationVisibility.SYSTEM);

        result.put(new NameValuePair(VALUE_STRING, clazz));
        result.setImmutable();
        return result;
    }

    /**
     * Constructs a standard {@code EnclosingMethod} annotation.
     *
     * @param method {@code non-null;} the enclosing method
     * @return {@code non-null;} the annotation
     */
    public static Annotation makeEnclosingMethod(com.droid.dx.rop.cst.CstMethodRef method) {
        Annotation result = new Annotation(ENCLOSING_METHOD_TYPE, AnnotationVisibility.SYSTEM);

        result.put(new NameValuePair(VALUE_STRING, method));
        result.setImmutable();
        return result;
    }

    /**
     * Constructs a standard {@code InnerClass} annotation.
     *
     * @param name {@code null-ok;} the original name of the class, or
     * {@code null} to represent an anonymous class
     * @param accessFlags the original access flags
     * @return {@code non-null;} the annotation
     */
    public static Annotation makeInnerClass(com.droid.dx.rop.cst.CstString name, int accessFlags) {
        Annotation result = new Annotation(INNER_CLASS_TYPE, AnnotationVisibility.SYSTEM);
        com.droid.dx.rop.cst.Constant nameCst = (name != null) ? name : CstKnownNull.THE_ONE;

        result.put(new NameValuePair(NAME_STRING, nameCst));
        result.put(new NameValuePair(ACCESS_FLAGS_STRING,
                        com.droid.dx.rop.cst.CstInteger.make(accessFlags)));
        result.setImmutable();
        return result;
    }

    /**
     * Constructs a standard {@code MemberClasses} annotation.
     *
     * @param types {@code non-null;} the list of (the types of) the member classes
     * @return {@code non-null;} the annotation
     */
    public static Annotation makeMemberClasses(com.droid.dx.rop.type.TypeList types) {
        com.droid.dx.rop.cst.CstArray array = makeCstArray(types);
        Annotation result = new Annotation(MEMBER_CLASSES_TYPE, AnnotationVisibility.SYSTEM);
        result.put(new NameValuePair(VALUE_STRING, array));
        result.setImmutable();
        return result;
    }

    /**
     * Constructs a standard {@code Signature} annotation.
     *
     * @param signature {@code non-null;} the signature string
     * @return {@code non-null;} the annotation
     */
    public static Annotation makeSignature(com.droid.dx.rop.cst.CstString signature) {
        Annotation result = new Annotation(SIGNATURE_TYPE, AnnotationVisibility.SYSTEM);

        /*
         * Split the string into pieces that are likely to be common
         * across many signatures and the rest of the file.
         */

        String raw = signature.getString();
        int rawLength = raw.length();
        ArrayList<String> pieces = new ArrayList<String>(20);

        for (int at = 0; at < rawLength; /*at*/) {
            char c = raw.charAt(at);
            int endAt = at + 1;
            if (c == 'L') {
                // Scan to ';' or '<'. Consume ';' but not '<'.
                while (endAt < rawLength) {
                    c = raw.charAt(endAt);
                    if (c == ';') {
                        endAt++;
                        break;
                    } else if (c == '<') {
                        break;
                    }
                    endAt++;
                }
            } else {
                // Scan to 'L' without consuming it.
                while (endAt < rawLength) {
                    c = raw.charAt(endAt);
                    if (c == 'L') {
                        break;
                    }
                    endAt++;
                }
            }

            pieces.add(raw.substring(at, endAt));
            at = endAt;
        }

        int size = pieces.size();
        com.droid.dx.rop.cst.CstArray.List list = new com.droid.dx.rop.cst.CstArray.List(size);

        for (int i = 0; i < size; i++) {
            list.set(i, new com.droid.dx.rop.cst.CstString(pieces.get(i)));
        }

        list.setImmutable();

        result.put(new NameValuePair(VALUE_STRING, new com.droid.dx.rop.cst.CstArray(list)));
        result.setImmutable();
        return result;
    }

    /**
     * Constructs a standard {@code Throws} annotation.
     *
     * @param types {@code non-null;} the list of thrown types
     * @return {@code non-null;} the annotation
     */
    public static Annotation makeThrows(com.droid.dx.rop.type.TypeList types) {
        com.droid.dx.rop.cst.CstArray array = makeCstArray(types);
        Annotation result = new Annotation(THROWS_TYPE, AnnotationVisibility.SYSTEM);
        result.put(new NameValuePair(VALUE_STRING, array));
        result.setImmutable();
        return result;
    }

    /**
     * Converts a {@link com.droid.dx.rop.type.TypeList} to a {@link com.droid.dx.rop.cst.CstArray}.
     *
     * @param types {@code non-null;} the type list
     * @return {@code non-null;} the corresponding array constant
     */
    private static com.droid.dx.rop.cst.CstArray makeCstArray(com.droid.dx.rop.type.TypeList types) {
        int size = types.size();
        com.droid.dx.rop.cst.CstArray.List list = new com.droid.dx.rop.cst.CstArray.List(size);

        for (int i = 0; i < size; i++) {
            list.set(i, com.droid.dx.rop.cst.CstType.intern(types.getType(i)));
        }

        list.setImmutable();
        return new com.droid.dx.rop.cst.CstArray(list);
    }
}
