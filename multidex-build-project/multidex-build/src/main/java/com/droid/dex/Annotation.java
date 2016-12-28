/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.droid.dex;

import static com.droid.dex.EncodedValueReader.ENCODED_ANNOTATION;

/**
 * An annotation.
 */
public final class Annotation implements Comparable<Annotation> {
    private final com.droid.dex.Dex dex;
    private final byte visibility;
    private final EncodedValue encodedAnnotation;

    public Annotation(com.droid.dex.Dex dex, byte visibility, EncodedValue encodedAnnotation) {
        this.dex = dex;
        this.visibility = visibility;
        this.encodedAnnotation = encodedAnnotation;
    }

    public byte getVisibility() {
        return visibility;
    }

    public com.droid.dex.EncodedValueReader getReader() {
        return new com.droid.dex.EncodedValueReader(encodedAnnotation, ENCODED_ANNOTATION);
    }

    public int getTypeIndex() {
        com.droid.dex.EncodedValueReader reader = getReader();
        reader.readAnnotation();
        return reader.getAnnotationType();
    }

    public void writeTo(com.droid.dex.Dex.Section out) {
        out.writeByte(visibility);
        encodedAnnotation.writeTo(out);
    }

    @Override public int compareTo(Annotation other) {
        return encodedAnnotation.compareTo(other.encodedAnnotation);
    }

    @Override public String toString() {
        return dex == null
                ? visibility + " " + getTypeIndex()
                : visibility + " " + dex.typeNames().get(getTypeIndex());
    }
}
