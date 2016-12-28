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

package com.droid.dx.dex.code;

import com.droid.dx.rop.code.SourcePosition;

/**
 * Combination instruction which turns into a variable number of
 * {@code move*} instructions to move a set of registers into
 * registers starting at {@code 0} sequentially. This is used
 * in translating an instruction whose register requirements cannot
 * be met using a straightforward choice of a single opcode.
 */
public final class HighRegisterPrefix extends VariableSizeInsn {
    /** {@code null-ok;} cached instructions, if constructed */
    private com.droid.dx.dex.code.SimpleInsn[] insns;

    /**
     * Constructs an instance. The output address of this instance is initially
     * unknown ({@code -1}).
     *
     * @param position {@code non-null;} source position
     * @param registers {@code non-null;} source registers
     */
    public HighRegisterPrefix(SourcePosition position,
                              com.droid.dx.rop.code.RegisterSpecList registers) {
        super(position, registers);

        if (registers.size() == 0) {
            throw new IllegalArgumentException("registers.size() == 0");
        }

        insns = null;
    }

    /** {@inheritDoc} */
    @Override
    public int codeSize() {
        int result = 0;

        calculateInsnsIfNecessary();

        for (com.droid.dx.dex.code.SimpleInsn insn : insns) {
            result += insn.codeSize();
        }

        return result;
    }

    /** {@inheritDoc} */
    @Override
    public void writeTo(com.droid.dx.util.AnnotatedOutput out) {
        calculateInsnsIfNecessary();

        for (com.droid.dx.dex.code.SimpleInsn insn : insns) {
            insn.writeTo(out);
        }
    }

    /**
     * Helper for {@link #codeSize} and {@link #writeTo} which sets up
     * {@link #insns} if not already done.
     */
    private void calculateInsnsIfNecessary() {
        if (insns != null) {
            return;
        }

        com.droid.dx.rop.code.RegisterSpecList registers = getRegisters();
        int sz = registers.size();

        insns = new com.droid.dx.dex.code.SimpleInsn[sz];

        for (int i = 0, outAt = 0; i < sz; i++) {
          com.droid.dx.rop.code.RegisterSpec src = registers.get(i);
          insns[i] = moveInsnFor(src, outAt);
          outAt += src.getCategory();
        }
    }

    /** {@inheritDoc} */
    @Override
    public DalvInsn withRegisters(com.droid.dx.rop.code.RegisterSpecList registers) {
        return new HighRegisterPrefix(getPosition(), registers);
    }

    /** {@inheritDoc} */
    @Override
    protected String argString() {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    protected String listingString0(boolean noteIndices) {
        com.droid.dx.rop.code.RegisterSpecList registers = getRegisters();
        int sz = registers.size();
        StringBuffer sb = new StringBuffer(100);

        for (int i = 0, outAt = 0; i < sz; i++) {
            com.droid.dx.rop.code.RegisterSpec src = registers.get(i);
            com.droid.dx.dex.code.SimpleInsn insn = moveInsnFor(src, outAt);

            if (i != 0) {
                sb.append('\n');
            }

            sb.append(insn.listingString0(noteIndices));

            outAt += src.getCategory();
        }

        return sb.toString();
    }

    /**
     * Returns the proper move instruction for the given source spec
     * and destination index.
     *
     * @param src {@code non-null;} the source register spec
     * @param destIndex {@code >= 0;} the destination register index
     * @return {@code non-null;} the appropriate move instruction
     */
    private static com.droid.dx.dex.code.SimpleInsn moveInsnFor(com.droid.dx.rop.code.RegisterSpec src, int destIndex) {
        return DalvInsn.makeMove(SourcePosition.NO_INFO,
                com.droid.dx.rop.code.RegisterSpec.make(destIndex, src.getType()),
                src);
    }
}
