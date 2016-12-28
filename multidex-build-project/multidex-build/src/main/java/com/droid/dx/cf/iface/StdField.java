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

package com.droid.dx.cf.iface;

/**
 * Standard implementation of {@link Field}, which directly stores
 * all the associated data.
 */
public final class StdField extends StdMember implements Field {
    /**
     * Constructs an instance.
     *
     * @param definingClass {@code non-null;} the defining class
     * @param accessFlags access flags
     * @param nat {@code non-null;} member name and type (descriptor)
     * @param attributes {@code non-null;} list of associated attributes
     */
    public StdField(com.droid.dx.rop.cst.CstType definingClass, int accessFlags, com.droid.dx.rop.cst.CstNat nat,
                    AttributeList attributes) {
        super(definingClass, accessFlags, nat, attributes);
    }

    /** {@inheritDoc} */
    public com.droid.dx.rop.cst.TypedConstant getConstantValue() {
        AttributeList attribs = getAttributes();
        com.droid.dx.cf.attrib.AttConstantValue cval = (com.droid.dx.cf.attrib.AttConstantValue)
            attribs.findFirst(com.droid.dx.cf.attrib.AttConstantValue.ATTRIBUTE_NAME);

        if (cval == null) {
            return null;
        }

        return cval.getConstantValue();
    }
}
