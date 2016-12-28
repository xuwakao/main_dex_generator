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

import com.droid.dx.rop.type.Type;
import com.droid.dx.util.IntList;

/**
 * Instruction which contains switch cases.
 */
public final class SwitchInsn
        extends com.droid.dx.rop.code.Insn {
    /** {@code non-null;} list of switch cases */
    private final IntList cases;

    /**
     * Constructs an instance.
     *
     * @param opcode {@code non-null;} the opcode
     * @param position {@code non-null;} source position
     * @param result {@code null-ok;} spec for the result, if any
     * @param sources {@code non-null;} specs for all the sources
     * @param cases {@code non-null;} list of switch cases
     */
    public SwitchInsn(com.droid.dx.rop.code.Rop opcode, SourcePosition position, com.droid.dx.rop.code.RegisterSpec result,
                      com.droid.dx.rop.code.RegisterSpecList sources, IntList cases) {
        super(opcode, position, result, sources);

        if (opcode.getBranchingness() != com.droid.dx.rop.code.Rop.BRANCH_SWITCH) {
            throw new IllegalArgumentException("bogus branchingness");
        }

        if (cases == null) {
            throw new NullPointerException("cases == null");
        }

        this.cases = cases;
    }

    /** {@inheritDoc} */
    @Override
    public String getInlineString() {
        return cases.toString();
    }

    /** {@inheritDoc} */
    @Override
    public com.droid.dx.rop.type.TypeList getCatches() {
        return com.droid.dx.rop.type.StdTypeList.EMPTY;
    }

    /** {@inheritDoc} */
    @Override
    public void accept(Visitor visitor) {
        visitor.visitSwitchInsn(this);
    }

    /** {@inheritDoc} */
    @Override
    public com.droid.dx.rop.code.Insn withAddedCatch(Type type) {
        throw new UnsupportedOperationException("unsupported");
    }

    /** {@inheritDoc} */
    @Override
    public com.droid.dx.rop.code.Insn withRegisterOffset(int delta) {
        return new SwitchInsn(getOpcode(), getPosition(),
                              getResult().withOffset(delta),
                              getSources().withOffset(delta),
                              cases);
    }

    /**
     * {@inheritDoc}
     *
     * <p> SwitchInsn always compares false. The current use for this method
     * never encounters {@code SwitchInsn}s
     */
    @Override
    public boolean contentEquals(com.droid.dx.rop.code.Insn b) {
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public com.droid.dx.rop.code.Insn withNewRegisters(com.droid.dx.rop.code.RegisterSpec result,
                                                       com.droid.dx.rop.code.RegisterSpecList sources) {

        return new SwitchInsn(getOpcode(), getPosition(),
                              result,
                              sources,
                              cases);
    }

    /**
     * Gets the list of switch cases.
     *
     * @return {@code non-null;} the case list
     */
    public IntList getCases() {
        return cases;
    }
}
