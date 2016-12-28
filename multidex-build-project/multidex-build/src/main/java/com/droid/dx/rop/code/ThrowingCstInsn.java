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

package com.droid.dx.rop.code;

import com.droid.dx.rop.cst.Constant;
import com.droid.dx.rop.type.Type;

/**
 * Instruction which contains an explicit reference to a constant
 * and which might throw an exception.
 */
public final class ThrowingCstInsn
        extends CstInsn {
    /** {@code non-null;} list of exceptions caught */
    private final com.droid.dx.rop.type.TypeList catches;

    /**
     * Constructs an instance.
     *
     * @param opcode {@code non-null;} the opcode
     * @param position {@code non-null;} source position
     * @param sources {@code non-null;} specs for all the sources
     * @param catches {@code non-null;} list of exceptions caught
     * @param cst {@code non-null;} the constant
     */
    public ThrowingCstInsn(com.droid.dx.rop.code.Rop opcode, com.droid.dx.rop.code.SourcePosition position,
                           com.droid.dx.rop.code.RegisterSpecList sources,
                           com.droid.dx.rop.type.TypeList catches, Constant cst) {
        super(opcode, position, null, sources, cst);

        if (opcode.getBranchingness() != com.droid.dx.rop.code.Rop.BRANCH_THROW) {
            throw new IllegalArgumentException("bogus branchingness");
        }

        if (catches == null) {
            throw new NullPointerException("catches == null");
        }

        this.catches = catches;
    }

    /** {@inheritDoc} */
    @Override
    public String getInlineString() {
        Constant cst = getConstant();
        String constantString = cst.toHuman();
        if (cst instanceof com.droid.dx.rop.cst.CstString) {
            constantString = ((com.droid.dx.rop.cst.CstString) cst).toQuoted();
        }
        return constantString + " " + ThrowingInsn.toCatchString(catches);
    }

    /** {@inheritDoc} */
    @Override
    public com.droid.dx.rop.type.TypeList getCatches() {
        return catches;
    }

    /** {@inheritDoc} */
    @Override
    public void accept(Visitor visitor) {
        visitor.visitThrowingCstInsn(this);
    }

    /** {@inheritDoc} */
    @Override
    public com.droid.dx.rop.code.Insn withAddedCatch(Type type) {
        return new ThrowingCstInsn(getOpcode(), getPosition(),
                                   getSources(), catches.withAddedType(type),
                                   getConstant());
    }

    /** {@inheritDoc} */
    @Override
    public com.droid.dx.rop.code.Insn withRegisterOffset(int delta) {
        return new ThrowingCstInsn(getOpcode(), getPosition(),
                                   getSources().withOffset(delta),
                                   catches,
                                   getConstant());
    }

    /** {@inheritDoc} */
    @Override
    public com.droid.dx.rop.code.Insn withNewRegisters(com.droid.dx.rop.code.RegisterSpec result,
                                                       com.droid.dx.rop.code.RegisterSpecList sources) {

        return new ThrowingCstInsn(getOpcode(), getPosition(),
                                   sources,
                                   catches,
                                   getConstant());
    }


}
