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

package com.droid.dx.rop.cst;

import com.droid.dx.rop.type.Type;

/**
 * Utility for turning types into zeroes.
 */
public final class Zeroes {
    /**
     * This class is uninstantiable.
     */
    private Zeroes() {
        // This space intentionally left blank.
    }

    /**
     * Gets the "zero" (or {@code null}) value for the given type.
     *
     * @param type {@code non-null;} the type in question
     * @return {@code non-null;} its "zero" value
     */
    public static Constant zeroFor(Type type) {
        switch (type.getBasicType()) {
            case Type.BT_BOOLEAN: return com.droid.dx.rop.cst.CstBoolean.VALUE_FALSE;
            case Type.BT_BYTE:    return com.droid.dx.rop.cst.CstByte.VALUE_0;
            case Type.BT_CHAR:    return com.droid.dx.rop.cst.CstChar.VALUE_0;
            case Type.BT_DOUBLE:  return com.droid.dx.rop.cst.CstDouble.VALUE_0;
            case Type.BT_FLOAT:   return CstFloat.VALUE_0;
            case Type.BT_INT:     return com.droid.dx.rop.cst.CstInteger.VALUE_0;
            case Type.BT_LONG:    return CstLong.VALUE_0;
            case Type.BT_SHORT:   return com.droid.dx.rop.cst.CstShort.VALUE_0;
            case Type.BT_OBJECT:  return com.droid.dx.rop.cst.CstKnownNull.THE_ONE;
            default: {
                throw new UnsupportedOperationException("no zero for type: " +
                        type.toHuman());
            }
        }
    }
}
