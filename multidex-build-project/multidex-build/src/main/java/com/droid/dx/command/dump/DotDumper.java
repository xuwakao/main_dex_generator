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
import com.droid.dx.util.IntList;

/**
 * Dumps the pred/succ graph of methods into a format compatible
 * with the popular graph utility "dot".
 */
public class DotDumper implements com.droid.dx.cf.iface.ParseObserver {
    private com.droid.dx.cf.direct.DirectClassFile classFile;

    private final byte[] bytes;
    private final String filePath;
    private final boolean strictParse;
    private final boolean optimize;
    private final Args args;

    static void dump(byte[] bytes, String filePath, Args args) {
        new DotDumper(bytes, filePath, args).run();
    }

    DotDumper(byte[] bytes, String filePath, Args args) {
        this.bytes = bytes;
        this.filePath = filePath;
        this.strictParse = args.strictParse;
        this.optimize = args.optimize;
        this.args = args;
    }

    private void run() {
        com.droid.dx.util.ByteArray ba = new com.droid.dx.util.ByteArray(bytes);

        /*
         * First, parse the file completely, so we can safely refer to
         * attributes, etc.
         */
        classFile = new com.droid.dx.cf.direct.DirectClassFile(ba, filePath, strictParse);
        classFile.setAttributeFactory(com.droid.dx.cf.direct.StdAttributeFactory.THE_ONE);
        classFile.getMagic(); // Force parsing to happen.

        // Next, reparse it and observe the process.
        com.droid.dx.cf.direct.DirectClassFile liveCf =
            new com.droid.dx.cf.direct.DirectClassFile(ba, filePath, strictParse);
        liveCf.setAttributeFactory(com.droid.dx.cf.direct.StdAttributeFactory.THE_ONE);
        liveCf.setObserver(this);
        liveCf.getMagic(); // Force parsing to happen.
    }

    /**
     * @param name method name
     * @return true if this method should be dumped
     */
    protected boolean shouldDumpMethod(String name) {
        return args.method == null || args.method.equals(name);
    }

    public void changeIndent(int indentDelta) {
        // This space intentionally left blank.
    }

    public void parsed(com.droid.dx.util.ByteArray bytes, int offset, int len, String human) {
        // This space intentionally left blank.
    }

    /** {@inheritDoc} */
    public void startParsingMember(com.droid.dx.util.ByteArray bytes, int offset, String name,
                                   String descriptor) {
        // This space intentionally left blank.
    }

    public void endParsingMember(com.droid.dx.util.ByteArray bytes, int offset, String name,
                                 String descriptor, com.droid.dx.cf.iface.Member member) {
        if (!(member instanceof com.droid.dx.cf.iface.Method)) {
            return;
        }

        if (!shouldDumpMethod(name)) {
            return;
        }

        com.droid.dx.cf.code.ConcreteMethod meth = new com.droid.dx.cf.code.ConcreteMethod((com.droid.dx.cf.iface.Method) member, classFile,
                                                 true, true);

        com.droid.dx.rop.code.TranslationAdvice advice = DexTranslationAdvice.THE_ONE;
        com.droid.dx.rop.code.RopMethod rmeth =
            com.droid.dx.cf.code.Ropper.convert(meth, advice, classFile.getMethods());

        if (optimize) {
            boolean isStatic = com.droid.dx.rop.code.AccessFlags.isStatic(meth.getAccessFlags());
            rmeth = com.droid.dx.ssa.Optimizer.optimize(rmeth,
                    BaseDumper.computeParamWidth(meth, isStatic), isStatic,
                    true, advice);
        }

        System.out.println("digraph "  + name + "{");

        System.out.println("\tfirst -> n"
                + Hex.u2(rmeth.getFirstLabel()) + ";");

        com.droid.dx.rop.code.BasicBlockList blocks = rmeth.getBlocks();

        int sz = blocks.size();
        for (int i = 0; i < sz; i++) {
            com.droid.dx.rop.code.BasicBlock bb = blocks.get(i);
            int label = bb.getLabel();
            IntList successors = bb.getSuccessors();

            if (successors.size() == 0) {
                System.out.println("\tn" + Hex.u2(label) + " -> returns;");
            } else if (successors.size() == 1) {
                System.out.println("\tn" + Hex.u2(label) + " -> n"
                        + Hex.u2(successors.get(0)) + ";");
            } else {
                System.out.print("\tn" + Hex.u2(label) + " -> {");
                for (int j = 0; j < successors.size(); j++ ) {
                    int successor = successors.get(j);

                    if (successor != bb.getPrimarySuccessor()) {
                        System.out.print(" n" + Hex.u2(successor) + " ");
                    }

                }
                System.out.println("};");

                System.out.println("\tn" + Hex.u2(label) + " -> n"
                        + Hex.u2(bb.getPrimarySuccessor())
                        + " [label=\"primary\"];");


            }
        }

        System.out.println("}");
    }
}
