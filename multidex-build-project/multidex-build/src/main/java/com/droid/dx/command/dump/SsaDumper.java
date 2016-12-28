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

package com.droid.dx.command.dump;

import com.droid.dx.rop.code.DexTranslationAdvice;
import com.droid.dx.util.Hex;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.EnumSet;

/**
 * Dumper for the SSA-translated blocks of a method.
 */
public class SsaDumper extends BlockDumper {
    /**
     * Does the dump.
     *
     * @param bytes {@code non-null;} bytes of the original class file
     * @param out {@code non-null;} where to dump to
     * @param filePath the file path for the class, excluding any base
     * directory specification
     * @param args commandline parsedArgs
     */
    public static void dump(byte[] bytes, PrintStream out,
            String filePath, Args args) {
        SsaDumper sd = new SsaDumper(bytes, out, filePath, args);
        sd.dump();
    }

    /**
     * Constructs an instance.
     *
     * @param bytes {@code non-null;} bytes of the original class file
     * @param out {@code non-null;} where to dump to
     * @param filePath the file path for the class, excluding any base
     * directory specification
     * @param args commandline parsedArgs
     */
    private SsaDumper(byte[] bytes, PrintStream out, String filePath,
            Args args) {
        super(bytes, out, filePath, true, args);
    }

    /** {@inheritDoc} */
    @Override
    public void endParsingMember(com.droid.dx.util.ByteArray bytes, int offset, String name,
                                 String descriptor, com.droid.dx.cf.iface.Member member) {
        if (!(member instanceof com.droid.dx.cf.iface.Method)) {
            return;
        }

        if (!shouldDumpMethod(name)) {
            return;
        }

        if ((member.getAccessFlags() & (com.droid.dx.rop.code.AccessFlags.ACC_ABSTRACT |
                com.droid.dx.rop.code.AccessFlags.ACC_NATIVE)) != 0) {
            return;
        }

        com.droid.dx.cf.code.ConcreteMethod meth =
            new com.droid.dx.cf.code.ConcreteMethod((com.droid.dx.cf.iface.Method) member, classFile, true, true);
        com.droid.dx.rop.code.TranslationAdvice advice = DexTranslationAdvice.THE_ONE;
        com.droid.dx.rop.code.RopMethod rmeth = com.droid.dx.cf.code.Ropper.convert(meth, advice, classFile.getMethods());
        com.droid.dx.ssa.SsaMethod ssaMeth = null;
        boolean isStatic = com.droid.dx.rop.code.AccessFlags.isStatic(meth.getAccessFlags());
        int paramWidth = computeParamWidth(meth, isStatic);

        if (args.ssaStep == null) {
            ssaMeth = com.droid.dx.ssa.Optimizer.debugNoRegisterAllocation(rmeth,
                    paramWidth, isStatic, true, advice,
                    EnumSet.allOf(com.droid.dx.ssa.Optimizer.OptionalStep.class));
        } else if ("edge-split".equals(args.ssaStep)) {
            ssaMeth = com.droid.dx.ssa.Optimizer.debugEdgeSplit(rmeth, paramWidth,
                    isStatic, true, advice);
        } else if ("phi-placement".equals(args.ssaStep)) {
            ssaMeth = com.droid.dx.ssa.Optimizer.debugPhiPlacement(
                    rmeth, paramWidth, isStatic, true, advice);
        } else if ("renaming".equals(args.ssaStep)) {
            ssaMeth = com.droid.dx.ssa.Optimizer.debugRenaming(
                    rmeth, paramWidth, isStatic, true, advice);
        } else if ("dead-code".equals(args.ssaStep)) {
            ssaMeth = com.droid.dx.ssa.Optimizer.debugDeadCodeRemover(
                    rmeth, paramWidth, isStatic,true, advice);
        }

        StringBuffer sb = new StringBuffer(2000);

        sb.append("first ");
        sb.append(Hex.u2(
                ssaMeth.blockIndexToRopLabel(ssaMeth.getEntryBlockIndex())));
        sb.append('\n');

        ArrayList<com.droid.dx.ssa.SsaBasicBlock> blocks = ssaMeth.getBlocks();
        ArrayList<com.droid.dx.ssa.SsaBasicBlock> sortedBlocks =
            (ArrayList<com.droid.dx.ssa.SsaBasicBlock>) blocks.clone();
        Collections.sort(sortedBlocks, com.droid.dx.ssa.SsaBasicBlock.LABEL_COMPARATOR);

        for (com.droid.dx.ssa.SsaBasicBlock block : sortedBlocks) {
            sb.append("block ")
                    .append(Hex.u2(block.getRopLabel())).append('\n');

            BitSet preds = block.getPredecessors();

            for (int i = preds.nextSetBit(0); i >= 0;
                 i = preds.nextSetBit(i+1)) {
                sb.append("  pred ");
                sb.append(Hex.u2(ssaMeth.blockIndexToRopLabel(i)));
                sb.append('\n');
            }

            sb.append("  live in:" + block.getLiveInRegs());
            sb.append("\n");

            for (com.droid.dx.ssa.SsaInsn insn : block.getInsns()) {
                sb.append("  ");
                sb.append(insn.toHuman());
                sb.append('\n');
            }

            if (block.getSuccessors().cardinality() == 0) {
                sb.append("  returns\n");
            } else {
                int primary = block.getPrimarySuccessorRopLabel();

                com.droid.dx.util.IntList succLabelList = block.getRopLabelSuccessorList();

                int szSuccLabels = succLabelList.size();

                for (int i = 0; i < szSuccLabels; i++) {
                    sb.append("  next ");
                    sb.append(Hex.u2(succLabelList.get(i)));

                    if (szSuccLabels != 1 && primary == succLabelList.get(i)) {
                        sb.append(" *");
                    }
                    sb.append('\n');
                }
            }

            sb.append("  live out:" + block.getLiveOutRegs());
            sb.append("\n");
        }

        suppressDump = false;
        setAt(bytes, 0);
        parsed(bytes, 0, bytes.size(), sb.toString());
        suppressDump = true;
    }
}
