/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.droid.dx.cf.direct;

import com.droid.dx.rop.annotation.NameValuePair;

import java.io.IOException;

/**
 * Parser for annotations.
 */
public final class AnnotationParser {
    /** {@code non-null;} class file being parsed */
    private final DirectClassFile cf;

    /** {@code non-null;} constant pool to use */
    private final com.droid.dx.rop.cst.ConstantPool pool;

    /** {@code non-null;} bytes of the attribute data */
    private final com.droid.dx.util.ByteArray bytes;

    /** {@code null-ok;} parse observer, if any */
    private final com.droid.dx.cf.iface.ParseObserver observer;

    /** {@code non-null;} input stream to parse from */
    private final com.droid.dx.util.ByteArray.MyDataInputStream input;

    /**
     * {@code non-null;} cursor for use when informing the observer of what
     * was parsed
     */
    private int parseCursor;

    /**
     * Constructs an instance.
     *
     * @param cf {@code non-null;} class file to parse from
     * @param offset {@code >= 0;} offset into the class file data to parse at
     * @param length {@code >= 0;} number of bytes left in the attribute data
     * @param observer {@code null-ok;} parse observer to notify, if any
     */
    public AnnotationParser(DirectClassFile cf, int offset, int length,
            com.droid.dx.cf.iface.ParseObserver observer) {
        if (cf == null) {
            throw new NullPointerException("cf == null");
        }

        this.cf = cf;
        this.pool = cf.getConstantPool();
        this.observer = observer;
        this.bytes = cf.getBytes().slice(offset, offset + length);
        this.input = bytes.makeDataInputStream();
        this.parseCursor = 0;
    }

    /**
     * Parses an annotation value ({@code element_value}) attribute.
     *
     * @return {@code non-null;} the parsed constant value
     */
    public com.droid.dx.rop.cst.Constant parseValueAttribute() {
        com.droid.dx.rop.cst.Constant result;

        try {
            result = parseValue();

            if (input.available() != 0) {
                throw new com.droid.dx.cf.iface.ParseException("extra data in attribute");
            }
        } catch (IOException ex) {
            // ByteArray.MyDataInputStream should never throw.
            throw new RuntimeException("shouldn't happen", ex);
        }

        return result;
    }

    /**
     * Parses a parameter annotation attribute.
     *
     * @param visibility {@code non-null;} visibility of the parsed annotations
     * @return {@code non-null;} the parsed list of lists of annotations
     */
    public com.droid.dx.rop.annotation.AnnotationsList parseParameterAttribute(
            com.droid.dx.rop.annotation.AnnotationVisibility visibility) {
        com.droid.dx.rop.annotation.AnnotationsList result;

        try {
            result = parseAnnotationsList(visibility);

            if (input.available() != 0) {
                throw new com.droid.dx.cf.iface.ParseException("extra data in attribute");
            }
        } catch (IOException ex) {
            // ByteArray.MyDataInputStream should never throw.
            throw new RuntimeException("shouldn't happen", ex);
        }

        return result;
    }

    /**
     * Parses an annotation attribute, per se.
     *
     * @param visibility {@code non-null;} visibility of the parsed annotations
     * @return {@code non-null;} the list of annotations read from the attribute
     * data
     */
    public com.droid.dx.rop.annotation.Annotations parseAnnotationAttribute(
            com.droid.dx.rop.annotation.AnnotationVisibility visibility) {
        com.droid.dx.rop.annotation.Annotations result;

        try {
            result = parseAnnotations(visibility);

            if (input.available() != 0) {
                throw new com.droid.dx.cf.iface.ParseException("extra data in attribute");
            }
        } catch (IOException ex) {
            // ByteArray.MyDataInputStream should never throw.
            throw new RuntimeException("shouldn't happen", ex);
        }

        return result;
    }

    /**
     * Parses a list of annotation lists.
     *
     * @param visibility {@code non-null;} visibility of the parsed annotations
     * @return {@code non-null;} the list of annotation lists read from the attribute
     * data
     */
    private com.droid.dx.rop.annotation.AnnotationsList parseAnnotationsList(
            com.droid.dx.rop.annotation.AnnotationVisibility visibility) throws IOException {
        int count = input.readUnsignedByte();

        if (observer != null) {
            parsed(1, "num_parameters: " + com.droid.dx.util.Hex.u1(count));
        }

        com.droid.dx.rop.annotation.AnnotationsList outerList = new com.droid.dx.rop.annotation.AnnotationsList(count);

        for (int i = 0; i < count; i++) {
            if (observer != null) {
                parsed(0, "parameter_annotations[" + i + "]:");
                changeIndent(1);
            }

            com.droid.dx.rop.annotation.Annotations annotations = parseAnnotations(visibility);
            outerList.set(i, annotations);

            if (observer != null) {
                observer.changeIndent(-1);
            }
        }

        outerList.setImmutable();
        return outerList;
    }

    /**
     * Parses an annotation list.
     *
     * @param visibility {@code non-null;} visibility of the parsed annotations
     * @return {@code non-null;} the list of annotations read from the attribute
     * data
     */
    private com.droid.dx.rop.annotation.Annotations parseAnnotations(com.droid.dx.rop.annotation.AnnotationVisibility visibility)
            throws IOException {
        int count = input.readUnsignedShort();

        if (observer != null) {
            parsed(2, "num_annotations: " + com.droid.dx.util.Hex.u2(count));
        }

        com.droid.dx.rop.annotation.Annotations annotations = new com.droid.dx.rop.annotation.Annotations();

        for (int i = 0; i < count; i++) {
            if (observer != null) {
                parsed(0, "annotations[" + i + "]:");
                changeIndent(1);
            }

            com.droid.dx.rop.annotation.Annotation annotation = parseAnnotation(visibility);
            annotations.add(annotation);

            if (observer != null) {
                observer.changeIndent(-1);
            }
        }

        annotations.setImmutable();
        return annotations;
    }

    /**
     * Parses a single annotation.
     *
     * @param visibility {@code non-null;} visibility of the parsed annotation
     * @return {@code non-null;} the parsed annotation
     */
    private com.droid.dx.rop.annotation.Annotation parseAnnotation(com.droid.dx.rop.annotation.AnnotationVisibility visibility)
            throws IOException {
        requireLength(4);

        int typeIndex = input.readUnsignedShort();
        int numElements = input.readUnsignedShort();
        com.droid.dx.rop.cst.CstString typeString = (com.droid.dx.rop.cst.CstString) pool.get(typeIndex);
        com.droid.dx.rop.cst.CstType type = new com.droid.dx.rop.cst.CstType(com.droid.dx.rop.type.Type.intern(typeString.getString()));

        if (observer != null) {
            parsed(2, "type: " + type.toHuman());
            parsed(2, "num_elements: " + numElements);
        }

        com.droid.dx.rop.annotation.Annotation annotation = new com.droid.dx.rop.annotation.Annotation(type, visibility);

        for (int i = 0; i < numElements; i++) {
            if (observer != null) {
                parsed(0, "elements[" + i + "]:");
                changeIndent(1);
            }

            NameValuePair element = parseElement();
            annotation.add(element);

            if (observer != null) {
                changeIndent(-1);
            }
        }

        annotation.setImmutable();
        return annotation;
    }

    /**
     * Parses a {@link NameValuePair}.
     *
     * @return {@code non-null;} the parsed element
     */
    private NameValuePair parseElement() throws IOException {
        requireLength(5);

        int elementNameIndex = input.readUnsignedShort();
        com.droid.dx.rop.cst.CstString elementName = (com.droid.dx.rop.cst.CstString) pool.get(elementNameIndex);

        if (observer != null) {
            parsed(2, "element_name: " + elementName.toHuman());
            parsed(0, "value: ");
            changeIndent(1);
        }

        com.droid.dx.rop.cst.Constant value = parseValue();

        if (observer != null) {
            changeIndent(-1);
        }

        return new NameValuePair(elementName, value);
    }

    /**
     * Parses an annotation value.
     *
     * @return {@code non-null;} the parsed value
     */
    private com.droid.dx.rop.cst.Constant parseValue() throws IOException {
        int tag = input.readUnsignedByte();

        if (observer != null) {
            com.droid.dx.rop.cst.CstString humanTag = new com.droid.dx.rop.cst.CstString(Character.toString((char) tag));
            parsed(1, "tag: " + humanTag.toQuoted());
        }

        switch (tag) {
            case 'B': {
                com.droid.dx.rop.cst.CstInteger value = (com.droid.dx.rop.cst.CstInteger) parseConstant();
                return com.droid.dx.rop.cst.CstByte.make(value.getValue());
            }
            case 'C': {
                com.droid.dx.rop.cst.CstInteger value = (com.droid.dx.rop.cst.CstInteger) parseConstant();
                int intValue = value.getValue();
                return com.droid.dx.rop.cst.CstChar.make(value.getValue());
            }
            case 'D': {
                com.droid.dx.rop.cst.CstDouble value = (com.droid.dx.rop.cst.CstDouble) parseConstant();
                return value;
            }
            case 'F': {
                com.droid.dx.rop.cst.CstFloat value = (com.droid.dx.rop.cst.CstFloat) parseConstant();
                return value;
            }
            case 'I': {
                com.droid.dx.rop.cst.CstInteger value = (com.droid.dx.rop.cst.CstInteger) parseConstant();
                return value;
            }
            case 'J': {
                com.droid.dx.rop.cst.CstLong value = (com.droid.dx.rop.cst.CstLong) parseConstant();
                return value;
            }
            case 'S': {
                com.droid.dx.rop.cst.CstInteger value = (com.droid.dx.rop.cst.CstInteger) parseConstant();
                return com.droid.dx.rop.cst.CstShort.make(value.getValue());
            }
            case 'Z': {
                com.droid.dx.rop.cst.CstInteger value = (com.droid.dx.rop.cst.CstInteger) parseConstant();
                return com.droid.dx.rop.cst.CstBoolean.make(value.getValue());
            }
            case 'c': {
                int classInfoIndex = input.readUnsignedShort();
                com.droid.dx.rop.cst.CstString value = (com.droid.dx.rop.cst.CstString) pool.get(classInfoIndex);
                com.droid.dx.rop.type.Type type = com.droid.dx.rop.type.Type.internReturnType(value.getString());

                if (observer != null) {
                    parsed(2, "class_info: " + type.toHuman());
                }

                return new com.droid.dx.rop.cst.CstType(type);
            }
            case 's': {
                return parseConstant();
            }
            case 'e': {
                requireLength(4);

                int typeNameIndex = input.readUnsignedShort();
                int constNameIndex = input.readUnsignedShort();
                com.droid.dx.rop.cst.CstString typeName = (com.droid.dx.rop.cst.CstString) pool.get(typeNameIndex);
                com.droid.dx.rop.cst.CstString constName = (com.droid.dx.rop.cst.CstString) pool.get(constNameIndex);

                if (observer != null) {
                    parsed(2, "type_name: " + typeName.toHuman());
                    parsed(2, "const_name: " + constName.toHuman());
                }

                return new com.droid.dx.rop.cst.CstEnumRef(new com.droid.dx.rop.cst.CstNat(constName, typeName));
            }
            case '@': {
                com.droid.dx.rop.annotation.Annotation annotation =
                    parseAnnotation(com.droid.dx.rop.annotation.AnnotationVisibility.EMBEDDED);
                return new com.droid.dx.rop.cst.CstAnnotation(annotation);
            }
            case '[': {
                requireLength(2);

                int numValues = input.readUnsignedShort();
                com.droid.dx.rop.cst.CstArray.List list = new com.droid.dx.rop.cst.CstArray.List(numValues);

                if (observer != null) {
                    parsed(2, "num_values: " + numValues);
                    changeIndent(1);
                }

                for (int i = 0; i < numValues; i++) {
                    if (observer != null) {
                        changeIndent(-1);
                        parsed(0, "element_value[" + i + "]:");
                        changeIndent(1);
                    }
                    list.set(i, parseValue());
                }

                if (observer != null) {
                    changeIndent(-1);
                }

                list.setImmutable();
                return new com.droid.dx.rop.cst.CstArray(list);
            }
            default: {
                throw new com.droid.dx.cf.iface.ParseException("unknown annotation tag: " +
                        com.droid.dx.util.Hex.u1(tag));
            }
        }
    }

    /**
     * Helper for {@link #parseValue}, which parses a constant reference
     * and returns the referred-to constant value.
     *
     * @return {@code non-null;} the parsed value
     */
    private com.droid.dx.rop.cst.Constant parseConstant() throws IOException {
        int constValueIndex = input.readUnsignedShort();
        com.droid.dx.rop.cst.Constant value = (com.droid.dx.rop.cst.Constant) pool.get(constValueIndex);

        if (observer != null) {
            String human = (value instanceof com.droid.dx.rop.cst.CstString)
                ? ((com.droid.dx.rop.cst.CstString) value).toQuoted()
                : value.toHuman();
            parsed(2, "constant_value: " + human);
        }

        return value;
    }

    /**
     * Helper which will throw an exception if the given number of bytes
     * is not available to be read.
     *
     * @param requiredLength the number of required bytes
     */
    private void requireLength(int requiredLength) throws IOException {
        if (input.available() < requiredLength) {
            throw new com.droid.dx.cf.iface.ParseException("truncated annotation attribute");
        }
    }

    /**
     * Helper which indicates that some bytes were just parsed. This should
     * only be used (for efficiency sake) if the parse is known to be
     * observed.
     *
     * @param length {@code >= 0;} number of bytes parsed
     * @param message {@code non-null;} associated message
     */
    private void parsed(int length, String message) {
        observer.parsed(bytes, parseCursor, length, message);
        parseCursor += length;
    }

    /**
     * Convenience wrapper that simply calls through to
     * {@code observer.changeIndent()}.
     *
     * @param indent the amount to change the indent by
     */
    private void changeIndent(int indent) {
        observer.changeIndent(indent);
    }
}
