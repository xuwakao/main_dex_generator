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

package com.droid.dx.ssa;

import com.droid.dx.rop.code.Insn;
import com.droid.dx.rop.code.PlainCstInsn;
import com.droid.dx.rop.code.PlainInsn;
import com.droid.dx.rop.code.RegisterSpec;
import com.droid.dx.rop.code.RegisterSpecList;
import com.droid.dx.rop.code.Rop;
import com.droid.dx.rop.code.Rops;
import com.droid.dx.rop.cst.Constant;
import com.droid.dx.rop.cst.CstLiteralBits;
import com.droid.dx.rop.type.Type;
import com.droid.dx.rop.type.TypeBearer;

import java.util.ArrayList;
import java.util.List;

/**
 * Upgrades insn to their literal (constant-immediate) equivalent if possible.
 * Also switches IF instructions that compare with a constant zero or null
 * to be their IF_*Z equivalents.
 */
public class LiteralOpUpgrader {

    /** method we're processing */
    private final SsaMethod ssaMeth;

    /**
     * Process a method.
     *
     * @param ssaMethod {@code non-null;} method to process
     */
    public static void process(SsaMethod ssaMethod) {
        LiteralOpUpgrader dc;

        dc = new LiteralOpUpgrader(ssaMethod);

        dc.run();
    }

    private LiteralOpUpgrader(SsaMethod ssaMethod) {
        this.ssaMeth = ssaMethod;
    }

    /**
     * Returns true if the register contains an integer 0 or a known-null
     * object reference
     *
     * @param spec non-null spec
     * @return true for 0 or null type bearers
     */
    private static boolean isConstIntZeroOrKnownNull(RegisterSpec spec) {
        TypeBearer tb = spec.getTypeBearer();
        if (tb instanceof CstLiteralBits) {
            CstLiteralBits clb = (CstLiteralBits) tb;
            return (clb.getLongBits() == 0);
        }
        return false;
    }

    /**
     * Run the literal op upgrader
     */
    private void run() {
        final com.droid.dx.rop.code.TranslationAdvice advice = Optimizer.getAdvice();

        ssaMeth.forEachInsn(new com.droid.dx.ssa.SsaInsn.Visitor() {
            public void visitMoveInsn(com.droid.dx.ssa.NormalSsaInsn insn) {
                // do nothing
            }

            public void visitPhiInsn(PhiInsn insn) {
                // do nothing
            }

            public void visitNonMoveInsn(com.droid.dx.ssa.NormalSsaInsn insn) {

                Insn originalRopInsn = insn.getOriginalRopInsn();
                Rop opcode = originalRopInsn.getOpcode();
                RegisterSpecList sources = insn.getSources();

                // Replace insns with constant results with const insns
                if (tryReplacingWithConstant(insn)) return;

                if (sources.size() != 2 ) {
                    // We're only dealing with two-source insns here.
                    return;
                }

                if (opcode.getBranchingness() == Rop.BRANCH_IF) {
                    /*
                     * An if instruction can become an if-*z instruction.
                     */
                    if (isConstIntZeroOrKnownNull(sources.get(0))) {
                        replacePlainInsn(insn, sources.withoutFirst(),
                              com.droid.dx.rop.code.RegOps.flippedIfOpcode(opcode.getOpcode()), null);
                    } else if (isConstIntZeroOrKnownNull(sources.get(1))) {
                        replacePlainInsn(insn, sources.withoutLast(),
                              opcode.getOpcode(), null);
                    }
                } else if (advice.hasConstantOperation(
                        opcode, sources.get(0), sources.get(1))) {
                    insn.upgradeToLiteral();
                } else  if (opcode.isCommutative()
                        && advice.hasConstantOperation(
                        opcode, sources.get(1), sources.get(0))) {
                    /*
                     * An instruction can be commuted to a literal operation
                     */

                    insn.setNewSources(
                            RegisterSpecList.make(
                                    sources.get(1), sources.get(0)));

                    insn.upgradeToLiteral();
                }
            }
        });
    }

    /**
     * Tries to replace an instruction with a const instruction. The given
     * instruction must have a constant result for it to be replaced.
     *
     * @param insn {@code non-null;} instruction to try to replace
     * @return true if the instruction was replaced
     */
    private boolean tryReplacingWithConstant(com.droid.dx.ssa.NormalSsaInsn insn) {
        Insn originalRopInsn = insn.getOriginalRopInsn();
        Rop opcode = originalRopInsn.getOpcode();
        RegisterSpec result = insn.getResult();

        if (result != null && !ssaMeth.isRegALocal(result) &&
                opcode.getOpcode() != com.droid.dx.rop.code.RegOps.CONST) {
            TypeBearer type = insn.getResult().getTypeBearer();
            if (type.isConstant() && type.getBasicType() == Type.BT_INT) {
                // Replace the instruction with a constant
                replacePlainInsn(insn, RegisterSpecList.EMPTY,
                        com.droid.dx.rop.code.RegOps.CONST, (Constant) type);

                // Remove the source as well if this is a move-result-pseudo
                if (opcode.getOpcode() == com.droid.dx.rop.code.RegOps.MOVE_RESULT_PSEUDO) {
                    int pred = insn.getBlock().getPredecessors().nextSetBit(0);
                    ArrayList<com.droid.dx.ssa.SsaInsn> predInsns =
                            ssaMeth.getBlocks().get(pred).getInsns();
                    com.droid.dx.ssa.NormalSsaInsn sourceInsn =
                            (com.droid.dx.ssa.NormalSsaInsn) predInsns.get(predInsns.size()-1);
                    replacePlainInsn(sourceInsn, RegisterSpecList.EMPTY,
                            com.droid.dx.rop.code.RegOps.GOTO, null);
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Replaces an SsaInsn containing a PlainInsn with a new PlainInsn. The
     * new PlainInsn is constructed with a new RegOp and new sources.
     *
     * TODO move this somewhere else.
     *
     * @param insn {@code non-null;} an SsaInsn containing a PlainInsn
     * @param newSources {@code non-null;} new sources list for new insn
     * @param newOpcode A RegOp from {@link com.droid.dx.rop.code.RegOps}
     * @param cst {@code null-ok;} constant for new instruction, if any
     */
    private void replacePlainInsn(com.droid.dx.ssa.NormalSsaInsn insn,
                                  RegisterSpecList newSources, int newOpcode, Constant cst) {

        Insn originalRopInsn = insn.getOriginalRopInsn();
        Rop newRop = Rops.ropFor(newOpcode, insn.getResult(), newSources, cst);
        Insn newRopInsn;
        if (cst == null) {
            newRopInsn = new PlainInsn(newRop, originalRopInsn.getPosition(),
                    insn.getResult(), newSources);
        } else {
            newRopInsn = new PlainCstInsn(newRop, originalRopInsn.getPosition(),
                    insn.getResult(), newSources, cst);
        }
        com.droid.dx.ssa.NormalSsaInsn newInsn = new com.droid.dx.ssa.NormalSsaInsn(newRopInsn, insn.getBlock());

        List<com.droid.dx.ssa.SsaInsn> insns = insn.getBlock().getInsns();

        ssaMeth.onInsnRemoved(insn);
        insns.set(insns.lastIndexOf(insn), newInsn);
        ssaMeth.onInsnAdded(newInsn);
    }
}
