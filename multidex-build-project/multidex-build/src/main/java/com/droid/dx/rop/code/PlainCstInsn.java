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
 * but which cannot throw an exception.
 */
public final class PlainCstInsn
        extends CstInsn {
    /**
     * Constructs an instance.
     *
     * @param opcode {@code non-null;} the opcode
     * @param position {@code non-null;} source position
     * @param result {@code null-ok;} spec for the result, if any
     * @param sources {@code non-null;} specs for all the sources
     * @param cst {@code non-null;} the constant
     */
    public PlainCstInsn(com.droid.dx.rop.code.Rop opcode, com.droid.dx.rop.code.SourcePosition position,
                        com.droid.dx.rop.code.RegisterSpec result, com.droid.dx.rop.code.RegisterSpecList sources,
                        Constant cst) {
        super(opcode, position, result, sources, cst);

        if (opcode.getBranchingness() != com.droid.dx.rop.code.Rop.BRANCH_NONE) {
            throw new IllegalArgumentException("bogus branchingness");
        }
    }

    /** {@inheritDoc} */
    @Override
    public com.droid.dx.rop.type.TypeList getCatches() {
        return com.droid.dx.rop.type.StdTypeList.EMPTY;
    }

    /** {@inheritDoc} */
    @Override
    public void accept(Visitor visitor) {
        visitor.visitPlainCstInsn(this);
    }

    /** {@inheritDoc} */
    @Override
    public com.droid.dx.rop.code.Insn withAddedCatch(Type type) {
        throw new UnsupportedOperationException("unsupported");
    }

    /** {@inheritDoc} */
    @Override
    public com.droid.dx.rop.code.Insn withRegisterOffset(int delta) {
        return new PlainCstInsn(getOpcode(), getPosition(),
                                getResult().withOffset(delta),
                                getSources().withOffset(delta),
                                getConstant());
    }

    /** {@inheritDoc} */
    @Override
    public com.droid.dx.rop.code.Insn withNewRegisters(com.droid.dx.rop.code.RegisterSpec result,
                                                       com.droid.dx.rop.code.RegisterSpecList sources) {

        return new PlainCstInsn(getOpcode(), getPosition(),
                                result,
                                sources,
                                getConstant());

    }
}
