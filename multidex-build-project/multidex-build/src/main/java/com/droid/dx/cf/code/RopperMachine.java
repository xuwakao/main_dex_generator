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

package com.droid.dx.cf.code;

import com.droid.dx.cf.iface.Method;
import com.droid.dx.cf.iface.MethodList;
import com.droid.dx.rop.code.FillArrayDataInsn;
import com.droid.dx.rop.code.Insn;
import com.droid.dx.rop.code.PlainCstInsn;
import com.droid.dx.rop.code.PlainInsn;
import com.droid.dx.rop.code.RegOps;
import com.droid.dx.rop.code.RegisterSpec;
import com.droid.dx.rop.code.RegisterSpecList;
import com.droid.dx.rop.code.Rop;
import com.droid.dx.rop.code.Rops;
import com.droid.dx.rop.code.SourcePosition;
import com.droid.dx.rop.code.SwitchInsn;
import com.droid.dx.rop.code.ThrowingCstInsn;
import com.droid.dx.rop.code.ThrowingInsn;
import com.droid.dx.rop.code.TranslationAdvice;
import com.droid.dx.rop.cst.Constant;
import com.droid.dx.rop.cst.CstFieldRef;
import com.droid.dx.rop.cst.CstInteger;
import com.droid.dx.rop.cst.CstType;
import com.droid.dx.rop.type.Type;
import com.droid.dx.rop.type.TypeBearer;
import com.droid.dx.util.IntList;
import java.util.ArrayList;

/**
 * Machine implementation for use by {@link com.droid.dx.cf.code.Ropper}.
 */
/*package*/ final class RopperMachine extends ValueAwareMachine {
    /** {@code non-null;} array reflection class */
    private static final CstType ARRAY_REFLECT_TYPE =
        new CstType(Type.internClassName("java/lang/reflect/Array"));

    /**
     * {@code non-null;} method constant for use in converting
     * {@code multianewarray} instructions
     */
    private static final com.droid.dx.rop.cst.CstMethodRef MULTIANEWARRAY_METHOD =
        new com.droid.dx.rop.cst.CstMethodRef(ARRAY_REFLECT_TYPE,
                         new com.droid.dx.rop.cst.CstNat(new com.droid.dx.rop.cst.CstString("newInstance"),
                                    new com.droid.dx.rop.cst.CstString("(Ljava/lang/Class;[I)" +
                                                "Ljava/lang/Object;")));

    /** {@code non-null;} {@link com.droid.dx.cf.code.Ropper} controlling this instance */
    private final com.droid.dx.cf.code.Ropper ropper;

    /** {@code non-null;} method being converted */
    private final ConcreteMethod method;

    /** {@code non-null:} list of methods from the class whose method is being converted */
    private final MethodList methods;

    /** {@code non-null;} translation advice */
    private final TranslationAdvice advice;

    /** max locals of the method */
    private final int maxLocals;

    /** {@code non-null;} instructions for the rop basic block in-progress */
    private final ArrayList<Insn> insns;

    /** {@code non-null;} catches for the block currently being processed */
    private com.droid.dx.rop.type.TypeList catches;

    /** whether the catches have been used in an instruction */
    private boolean catchesUsed;

    /** whether the block contains a {@code return} */
    private boolean returns;

    /** primary successor index */
    private int primarySuccessorIndex;

    /** {@code >= 0;} number of extra basic blocks required */
    private int extraBlockCount;

    /** true if last processed block ends with a jsr or jsr_W*/
    private boolean hasJsr;

    /** true if an exception can be thrown by the last block processed */
    private boolean blockCanThrow;

    /**
     * If non-null, the ReturnAddress that was used by the terminating ret
     * instruction. If null, there was no ret instruction encountered.
     */

    private com.droid.dx.cf.code.ReturnAddress returnAddress;

    /**
     * {@code null-ok;} the appropriate {@code return} op or {@code null}
     * if it is not yet known
     */
    private Rop returnOp;

    /**
     * {@code null-ok;} the source position for the return block or {@code null}
     * if it is not yet known
     */
    private SourcePosition returnPosition;

    /**
     * Constructs an instance.
     *
     * @param ropper {@code non-null;} ropper controlling this instance
     * @param method {@code non-null;} method being converted
     * @param advice {@code non-null;} translation advice to use
     * @param methods {@code non-null;} list of methods defined by the class
     *     that defines {@code method}.
     */
    public RopperMachine(com.droid.dx.cf.code.Ropper ropper, ConcreteMethod method,
                         TranslationAdvice advice, MethodList methods) {
        super(method.getEffectiveDescriptor());

        if (methods == null) {
            throw new NullPointerException("methods == null");
        }

        if (ropper == null) {
            throw new NullPointerException("ropper == null");
        }

        if (advice == null) {
            throw new NullPointerException("advice == null");
        }

        this.ropper = ropper;
        this.method = method;
        this.methods = methods;
        this.advice = advice;
        this.maxLocals = method.getMaxLocals();
        this.insns = new ArrayList<Insn>(25);
        this.catches = null;
        this.catchesUsed = false;
        this.returns = false;
        this.primarySuccessorIndex = -1;
        this.extraBlockCount = 0;
        this.blockCanThrow = false;
        this.returnOp = null;
        this.returnPosition = null;
    }

    /**
     * Gets the instructions array. It is shared and gets modified by
     * subsequent calls to this instance.
     *
     * @return {@code non-null;} the instructions array
     */
    public ArrayList<Insn> getInsns() {
        return insns;
    }

    /**
     * Gets the return opcode encountered, if any.
     *
     * @return {@code null-ok;} the return opcode
     */
    public Rop getReturnOp() {
        return returnOp;
    }

    /**
     * Gets the return position, if known.
     *
     * @return {@code null-ok;} the return position
     */
    public SourcePosition getReturnPosition() {
        return returnPosition;
    }

    /**
     * Gets ready to start working on a new block. This will clear the
     * {@link #insns} list, set {@link #catches}, reset whether it has
     * been used, reset whether the block contains a
     * {@code return}, and reset {@link #primarySuccessorIndex}.
     */
    public void startBlock(com.droid.dx.rop.type.TypeList catches) {
        this.catches = catches;

        insns.clear();
        catchesUsed = false;
        returns = false;
        primarySuccessorIndex = 0;
        extraBlockCount = 0;
        blockCanThrow = false;
        hasJsr = false;
        returnAddress = null;
    }

    /**
     * Gets whether {@link #catches} was used. This indicates that the
     * last instruction in the block is one of the ones that can throw.
     *
     * @return whether {@code catches} has been used
     */
    public boolean wereCatchesUsed() {
        return catchesUsed;
    }

    /**
     * Gets whether the block just processed ended with a
     * {@code return}.
     *
     * @return whether the block returns
     */
    public boolean returns() {
        return returns;
    }

    /**
     * Gets the primary successor index. This is the index into the
     * successors list where the primary may be found or
     * {@code -1} if there are successors but no primary
     * successor. This may return something other than
     * {@code -1} in the case of an instruction with no
     * successors at all (primary or otherwise).
     *
     * @return {@code >= -1;} the primary successor index
     */
    public int getPrimarySuccessorIndex() {
        return primarySuccessorIndex;
    }

    /**
     * Gets how many extra blocks will be needed to represent the
     * block currently being translated. Each extra block should consist
     * of one instruction from the end of the original block.
     *
     * @return {@code >= 0;} the number of extra blocks needed
     */
    public int getExtraBlockCount() {
        return extraBlockCount;
    }

    /**
     * @return true if at least one of the insn processed since the last
     * call to startBlock() can throw.
     */
    public boolean canThrow() {
        return blockCanThrow;
    }

    /**
     * @return true if a JSR has ben encountered since the last call to
     * startBlock()
     */
    public boolean hasJsr() {
        return hasJsr;
    }

    /**
     * @return {@code true} if a {@code ret} has ben encountered since
     * the last call to {@code startBlock()}
     */
    public boolean hasRet() {
        return returnAddress != null;
    }

    /**
     * @return {@code null-ok;} return address of a {@code ret}
     * instruction if encountered since last call to startBlock().
     * {@code null} if no ret instruction encountered.
     */
    public com.droid.dx.cf.code.ReturnAddress getReturnAddress() {
        return returnAddress;
    }

    /** {@inheritDoc} */
    @Override
    public void run(Frame frame, int offset, int opcode) {
        /*
         * This is the stack pointer after the opcode's arguments have been
         * popped.
         */
        int stackPointer = maxLocals + frame.getStack().size();

        // The sources have to be retrieved before super.run() gets called.
        RegisterSpecList sources = getSources(opcode, stackPointer);
        int sourceCount = sources.size();

        super.run(frame, offset, opcode);

        SourcePosition pos = method.makeSourcePosistion(offset);
        RegisterSpec localTarget = getLocalTarget(opcode == com.droid.dx.cf.code.ByteOps.ISTORE);
        int destCount = resultCount();
        RegisterSpec dest;

        if (destCount == 0) {
            dest = null;
            switch (opcode) {
                case com.droid.dx.cf.code.ByteOps.POP:
                case com.droid.dx.cf.code.ByteOps.POP2: {
                    // These simply don't appear in the rop form.
                    return;
                }
            }
        } else if (localTarget != null) {
            dest = localTarget;
        } else if (destCount == 1) {
            dest = RegisterSpec.make(stackPointer, result(0));
        } else {
            /*
             * This clause only ever applies to the stack manipulation
             * ops that have results (that is, dup* and swap but not
             * pop*).
             *
             * What we do is first move all the source registers into
             * the "temporary stack" area defined for the method, and
             * then move stuff back down onto the main "stack" in the
             * arrangement specified by the stack op pattern.
             *
             * Note: This code ends up emitting a lot of what will
             * turn out to be superfluous moves (e.g., moving back and
             * forth to the same local when doing a dup); however,
             * that makes this code a bit easier (and goodness knows
             * it doesn't need any extra complexity), and all the SSA
             * stuff is going to want to deal with this sort of
             * superfluous assignment anyway, so it should be a wash
             * in the end.
             */
            int scratchAt = ropper.getFirstTempStackReg();
            RegisterSpec[] scratchRegs = new RegisterSpec[sourceCount];

            for (int i = 0; i < sourceCount; i++) {
                RegisterSpec src = sources.get(i);
                TypeBearer type = src.getTypeBearer();
                RegisterSpec scratch = src.withReg(scratchAt);
                insns.add(new PlainInsn(Rops.opMove(type), pos, scratch, src));
                scratchRegs[i] = scratch;
                scratchAt += src.getCategory();
            }

            for (int pattern = getAuxInt(); pattern != 0; pattern >>= 4) {
                int which = (pattern & 0x0f) - 1;
                RegisterSpec scratch = scratchRegs[which];
                TypeBearer type = scratch.getTypeBearer();
                insns.add(new PlainInsn(Rops.opMove(type), pos,
                                        scratch.withReg(stackPointer),
                                        scratch));
                stackPointer += type.getType().getCategory();
            }
            return;
        }

        TypeBearer destType = (dest != null) ? dest : Type.VOID;
        Constant cst = getAuxCst();
        int ropOpcode;
        Rop rop;
        Insn insn;

        if (opcode == com.droid.dx.cf.code.ByteOps.MULTIANEWARRAY) {
            blockCanThrow = true;

            // Add the extra instructions for handling multianewarray.

            extraBlockCount = 6;

            /*
             * Add an array constructor for the int[] containing all the
             * dimensions.
             */
            RegisterSpec dimsReg =
                RegisterSpec.make(dest.getNextReg(), Type.INT_ARRAY);
            rop = Rops.opFilledNewArray(Type.INT_ARRAY, sourceCount);
            insn = new ThrowingCstInsn(rop, pos, sources, catches,
                    CstType.INT_ARRAY);
            insns.add(insn);

            // Add a move-result for the new-filled-array
            rop = Rops.opMoveResult(Type.INT_ARRAY);
            insn = new PlainInsn(rop, pos, dimsReg, RegisterSpecList.EMPTY);
            insns.add(insn);

            /*
             * Add a const-class instruction for the specified array
             * class.
             */

            /*
             * Remove as many dimensions from the originally specified
             * class as are given in the explicit list of dimensions,
             * so as to pass the right component class to the standard
             * Java library array constructor.
             */
            Type componentType = ((CstType) cst).getClassType();
            for (int i = 0; i < sourceCount; i++) {
                componentType = componentType.getComponentType();
            }

            RegisterSpec classReg =
                RegisterSpec.make(dest.getReg(), Type.CLASS);

            if (componentType.isPrimitive()) {
                /*
                 * The component type is primitive (e.g., int as opposed
                 * to Integer), so we have to fetch the corresponding
                 * TYPE class.
                 */
                CstFieldRef typeField =
                    CstFieldRef.forPrimitiveType(componentType);
                insn = new ThrowingCstInsn(Rops.GET_STATIC_OBJECT, pos,
                                           RegisterSpecList.EMPTY,
                                           catches, typeField);
            } else {
                /*
                 * The component type is an object type, so just make a
                 * normal class reference.
                 */
                insn = new ThrowingCstInsn(Rops.CONST_OBJECT, pos,
                                           RegisterSpecList.EMPTY, catches,
                                           new CstType(componentType));
            }

            insns.add(insn);

            // Add a move-result-pseudo for the get-static or const
            rop = Rops.opMoveResultPseudo(classReg.getType());
            insn = new PlainInsn(rop, pos, classReg, RegisterSpecList.EMPTY);
            insns.add(insn);

            /*
             * Add a call to the "multianewarray method," that is,
             * Array.newInstance(class, dims). Note: The result type
             * of newInstance() is Object, which is why the last
             * instruction in this sequence is a cast to the right
             * type for the original instruction.
             */

            RegisterSpec objectReg =
                RegisterSpec.make(dest.getReg(), Type.OBJECT);

            insn = new ThrowingCstInsn(
                    Rops.opInvokeStatic(MULTIANEWARRAY_METHOD.getPrototype()),
                    pos, RegisterSpecList.make(classReg, dimsReg),
                    catches, MULTIANEWARRAY_METHOD);
            insns.add(insn);

            // Add a move-result.
            rop = Rops.opMoveResult(MULTIANEWARRAY_METHOD.getPrototype()
                    .getReturnType());
            insn = new PlainInsn(rop, pos, objectReg, RegisterSpecList.EMPTY);
            insns.add(insn);

            /*
             * And finally, set up for the remainder of this method to
             * add an appropriate cast.
             */

            opcode = com.droid.dx.cf.code.ByteOps.CHECKCAST;
            sources = RegisterSpecList.make(objectReg);
        } else if (opcode == com.droid.dx.cf.code.ByteOps.JSR) {
            // JSR has no Rop instruction
            hasJsr = true;
            return;
        } else if (opcode == com.droid.dx.cf.code.ByteOps.RET) {
            try {
                returnAddress = (com.droid.dx.cf.code.ReturnAddress)arg(0);
            } catch (ClassCastException ex) {
                throw new RuntimeException(
                        "Argument to RET was not a ReturnAddress", ex);
            }
            // RET has no Rop instruction.
            return;
        }

        ropOpcode = jopToRopOpcode(opcode, cst);
        rop = Rops.ropFor(ropOpcode, destType, sources, cst);

        Insn moveResult = null;
        if (dest != null && rop.isCallLike()) {
            /*
             * We're going to want to have a move-result in the next
             * basic block.
             */
            extraBlockCount++;

            moveResult = new PlainInsn(
                    Rops.opMoveResult(((com.droid.dx.rop.cst.CstMethodRef) cst).getPrototype()
                    .getReturnType()), pos, dest, RegisterSpecList.EMPTY);

            dest = null;
        } else if (dest != null && rop.canThrow()) {
            /*
             * We're going to want to have a move-result-pseudo in the
             * next basic block.
             */
            extraBlockCount++;

            moveResult = new PlainInsn(
                    Rops.opMoveResultPseudo(dest.getTypeBearer()),
                    pos, dest, RegisterSpecList.EMPTY);

            dest = null;
        }
        if (ropOpcode == RegOps.NEW_ARRAY) {
            /*
             * In the original bytecode, this was either a primitive
             * array constructor "newarray" or an object array
             * constructor "anewarray". In the former case, there is
             * no explicit constant, and in the latter, the constant
             * is for the element type and not the array type. The rop
             * instruction form for both of these is supposed to be
             * the resulting array type, so we initialize / alter
             * "cst" here, accordingly. Conveniently enough, the rop
             * opcode already gets constructed with the proper array
             * type.
             */
            cst = CstType.intern(rop.getResult());
        } else if ((cst == null) && (sourceCount == 2)) {
            TypeBearer firstType = sources.get(0).getTypeBearer();
            TypeBearer lastType = sources.get(1).getTypeBearer();

            if ((lastType.isConstant() || firstType.isConstant()) &&
                 advice.hasConstantOperation(rop, sources.get(0),
                                             sources.get(1))) {

                if (lastType.isConstant()) {
                    /*
                     * The target architecture has an instruction that can
                     * build in the constant found in the second argument,
                     * so pull it out of the sources and just use it as a
                     * constant here.
                     */
                    cst = (Constant) lastType;
                    sources = sources.withoutLast();

                    // For subtraction, change to addition and invert constant
                    if (rop.getOpcode() == RegOps.SUB) {
                        ropOpcode = RegOps.ADD;
                        CstInteger cstInt = (CstInteger) lastType;
                        cst = CstInteger.make(-cstInt.getValue());
                    }
                } else {
                    /*
                     * The target architecture has an instruction that can
                     * build in the constant found in the first argument,
                     * so pull it out of the sources and just use it as a
                     * constant here.
                     */
                    cst = (Constant) firstType;
                    sources = sources.withoutFirst();
                }

                rop = Rops.ropFor(ropOpcode, destType, sources, cst);
            }
        }

        SwitchList cases = getAuxCases();
        ArrayList<Constant> initValues = getInitValues();
        boolean canThrow = rop.canThrow();

        blockCanThrow |= canThrow;

        if (cases != null) {
            if (cases.size() == 0) {
                // It's a default-only switch statement. It can happen!
                insn = new PlainInsn(Rops.GOTO, pos, null,
                                     RegisterSpecList.EMPTY);
                primarySuccessorIndex = 0;
            } else {
                IntList values = cases.getValues();
                insn = new SwitchInsn(rop, pos, dest, sources, values);
                primarySuccessorIndex = values.size();
            }
        } else if (ropOpcode == RegOps.RETURN) {
            /*
             * Returns get turned into the combination of a move (if
             * non-void and if the return doesn't already mention
             * register 0) and a goto (to the return block).
             */
            if (sources.size() != 0) {
                RegisterSpec source = sources.get(0);
                TypeBearer type = source.getTypeBearer();
                if (source.getReg() != 0) {
                    insns.add(new PlainInsn(Rops.opMove(type), pos,
                                            RegisterSpec.make(0, type),
                                            source));
                }
            }
            insn = new PlainInsn(Rops.GOTO, pos, null, RegisterSpecList.EMPTY);
            primarySuccessorIndex = 0;
            updateReturnOp(rop, pos);
            returns = true;
        } else if (cst != null) {
            if (canThrow) {
                insn =
                    new ThrowingCstInsn(rop, pos, sources, catches, cst);
                catchesUsed = true;
                primarySuccessorIndex = catches.size();
            } else {
                insn = new PlainCstInsn(rop, pos, dest, sources, cst);
            }
        } else if (canThrow) {
            insn = new ThrowingInsn(rop, pos, sources, catches);
            catchesUsed = true;
            if (opcode == com.droid.dx.cf.code.ByteOps.ATHROW) {
                /*
                 * The op athrow is the only one where it's possible
                 * to have non-empty successors and yet not have a
                 * primary successor.
                 */
                primarySuccessorIndex = -1;
            } else {
                primarySuccessorIndex = catches.size();
            }
        } else {
            insn = new PlainInsn(rop, pos, dest, sources);
        }

        insns.add(insn);

        if (moveResult != null) {
            insns.add(moveResult);
        }

        /*
         * If initValues is non-null, it means that the parser has
         * seen a group of compatible constant initialization
         * bytecodes that are applied to the current newarray. The
         * action we take here is to convert these initialization
         * bytecodes into a single fill-array-data ROP which lays out
         * all the constant values in a table.
         */
        if (initValues != null) {
            extraBlockCount++;
            insn = new FillArrayDataInsn(Rops.FILL_ARRAY_DATA, pos,
                    RegisterSpecList.make(moveResult.getResult()), initValues,
                    cst);
            insns.add(insn);
        }
    }

    /**
     * Helper for {@link #run}, which gets the list of sources for the.
     * instruction.
     *
     * @param opcode the opcode being translated
     * @param stackPointer {@code >= 0;} the stack pointer after the
     * instruction's arguments have been popped
     * @return {@code non-null;} the sources
     */
    private RegisterSpecList getSources(int opcode, int stackPointer) {
        int count = argCount();

        if (count == 0) {
            // We get an easy out if there aren't any sources.
            return RegisterSpecList.EMPTY;
        }

        int localIndex = getLocalIndex();
        RegisterSpecList sources;

        if (localIndex >= 0) {
            // The instruction is operating on a local variable.
            sources = new RegisterSpecList(1);
            sources.set(0, RegisterSpec.make(localIndex, arg(0)));
        } else {
            sources = new RegisterSpecList(count);
            int regAt = stackPointer;
            for (int i = 0; i < count; i++) {
                RegisterSpec spec = RegisterSpec.make(regAt, arg(i));
                sources.set(i, spec);
                regAt += spec.getCategory();
            }

            switch (opcode) {
                case com.droid.dx.cf.code.ByteOps.IASTORE: {
                    /*
                     * The Java argument order for array stores is
                     * (array, index, value), but the rop argument
                     * order is (value, array, index). The following
                     * code gets the right arguments in the right
                     * places.
                     */
                    if (count != 3) {
                        throw new RuntimeException("shouldn't happen");
                    }
                    RegisterSpec array = sources.get(0);
                    RegisterSpec index = sources.get(1);
                    RegisterSpec value = sources.get(2);
                    sources.set(0, value);
                    sources.set(1, array);
                    sources.set(2, index);
                    break;
                }
                case com.droid.dx.cf.code.ByteOps.PUTFIELD: {
                    /*
                     * Similar to above: The Java argument order for
                     * putfield is (object, value), but the rop
                     * argument order is (value, object).
                     */
                    if (count != 2) {
                        throw new RuntimeException("shouldn't happen");
                    }
                    RegisterSpec obj = sources.get(0);
                    RegisterSpec value = sources.get(1);
                    sources.set(0, value);
                    sources.set(1, obj);
                    break;
                }
            }
        }

        sources.setImmutable();
        return sources;
    }

    /**
     * Sets or updates the information about the return block.
     *
     * @param op {@code non-null;} the opcode to use
     * @param pos {@code non-null;} the position to use
     */
    private void updateReturnOp(Rop op, SourcePosition pos) {
        if (op == null) {
            throw new NullPointerException("op == null");
        }

        if (pos == null) {
            throw new NullPointerException("pos == null");
        }

        if (returnOp == null) {
            returnOp = op;
            returnPosition = pos;
        } else {
            if (returnOp != op) {
                throw new com.droid.dx.cf.code.SimException("return op mismatch: " + op + ", " +
                                       returnOp);
            }

            if (pos.getLine() > returnPosition.getLine()) {
                // Pick the largest line number to be the "canonical" return.
                returnPosition = pos;
            }
        }
    }

    /**
     * Gets the register opcode for the given Java opcode.
     *
     * @param jop {@code >= 0;} the Java opcode
     * @param cst {@code null-ok;} the constant argument, if any
     * @return {@code >= 0;} the corresponding register opcode
     */
    private int jopToRopOpcode(int jop, Constant cst) {
        switch (jop) {
            case com.droid.dx.cf.code.ByteOps.POP:
            case com.droid.dx.cf.code.ByteOps.POP2:
            case com.droid.dx.cf.code.ByteOps.DUP:
            case com.droid.dx.cf.code.ByteOps.DUP_X1:
            case com.droid.dx.cf.code.ByteOps.DUP_X2:
            case com.droid.dx.cf.code.ByteOps.DUP2:
            case com.droid.dx.cf.code.ByteOps.DUP2_X1:
            case com.droid.dx.cf.code.ByteOps.DUP2_X2:
            case com.droid.dx.cf.code.ByteOps.SWAP:
            case com.droid.dx.cf.code.ByteOps.JSR:
            case com.droid.dx.cf.code.ByteOps.RET:
            case com.droid.dx.cf.code.ByteOps.MULTIANEWARRAY: {
                // These need to be taken care of specially.
                break;
            }
            case com.droid.dx.cf.code.ByteOps.NOP: {
                return RegOps.NOP;
            }
            case com.droid.dx.cf.code.ByteOps.LDC:
            case com.droid.dx.cf.code.ByteOps.LDC2_W: {
                return RegOps.CONST;
            }
            case com.droid.dx.cf.code.ByteOps.ILOAD:
            case com.droid.dx.cf.code.ByteOps.ISTORE: {
                return RegOps.MOVE;
            }
            case com.droid.dx.cf.code.ByteOps.IALOAD: {
                return RegOps.AGET;
            }
            case com.droid.dx.cf.code.ByteOps.IASTORE: {
                return RegOps.APUT;
            }
            case com.droid.dx.cf.code.ByteOps.IADD:
            case com.droid.dx.cf.code.ByteOps.IINC: {
                return RegOps.ADD;
            }
            case com.droid.dx.cf.code.ByteOps.ISUB: {
                return RegOps.SUB;
            }
            case com.droid.dx.cf.code.ByteOps.IMUL: {
                return RegOps.MUL;
            }
            case com.droid.dx.cf.code.ByteOps.IDIV: {
                return RegOps.DIV;
            }
            case com.droid.dx.cf.code.ByteOps.IREM: {
                return RegOps.REM;
            }
            case com.droid.dx.cf.code.ByteOps.INEG: {
                return RegOps.NEG;
            }
            case com.droid.dx.cf.code.ByteOps.ISHL: {
                return RegOps.SHL;
            }
            case com.droid.dx.cf.code.ByteOps.ISHR: {
                return RegOps.SHR;
            }
            case com.droid.dx.cf.code.ByteOps.IUSHR: {
                return RegOps.USHR;
            }
            case com.droid.dx.cf.code.ByteOps.IAND: {
                return RegOps.AND;
            }
            case com.droid.dx.cf.code.ByteOps.IOR: {
                return RegOps.OR;
            }
            case com.droid.dx.cf.code.ByteOps.IXOR: {
                return RegOps.XOR;
            }
            case com.droid.dx.cf.code.ByteOps.I2L:
            case com.droid.dx.cf.code.ByteOps.I2F:
            case com.droid.dx.cf.code.ByteOps.I2D:
            case com.droid.dx.cf.code.ByteOps.L2I:
            case com.droid.dx.cf.code.ByteOps.L2F:
            case com.droid.dx.cf.code.ByteOps.L2D:
            case com.droid.dx.cf.code.ByteOps.F2I:
            case com.droid.dx.cf.code.ByteOps.F2L:
            case com.droid.dx.cf.code.ByteOps.F2D:
            case com.droid.dx.cf.code.ByteOps.D2I:
            case com.droid.dx.cf.code.ByteOps.D2L:
            case com.droid.dx.cf.code.ByteOps.D2F: {
                return RegOps.CONV;
            }
            case com.droid.dx.cf.code.ByteOps.I2B: {
                return RegOps.TO_BYTE;
            }
            case com.droid.dx.cf.code.ByteOps.I2C: {
                return RegOps.TO_CHAR;
            }
            case com.droid.dx.cf.code.ByteOps.I2S: {
                return RegOps.TO_SHORT;
            }
            case com.droid.dx.cf.code.ByteOps.LCMP:
            case com.droid.dx.cf.code.ByteOps.FCMPL:
            case com.droid.dx.cf.code.ByteOps.DCMPL: {
                return RegOps.CMPL;
            }
            case com.droid.dx.cf.code.ByteOps.FCMPG:
            case com.droid.dx.cf.code.ByteOps.DCMPG: {
                return RegOps.CMPG;
            }
            case com.droid.dx.cf.code.ByteOps.IFEQ:
            case com.droid.dx.cf.code.ByteOps.IF_ICMPEQ:
            case com.droid.dx.cf.code.ByteOps.IF_ACMPEQ:
            case com.droid.dx.cf.code.ByteOps.IFNULL: {
                return RegOps.IF_EQ;
            }
            case com.droid.dx.cf.code.ByteOps.IFNE:
            case com.droid.dx.cf.code.ByteOps.IF_ICMPNE:
            case com.droid.dx.cf.code.ByteOps.IF_ACMPNE:
            case com.droid.dx.cf.code.ByteOps.IFNONNULL: {
                return RegOps.IF_NE;
            }
            case com.droid.dx.cf.code.ByteOps.IFLT:
            case com.droid.dx.cf.code.ByteOps.IF_ICMPLT: {
                return RegOps.IF_LT;
            }
            case com.droid.dx.cf.code.ByteOps.IFGE:
            case com.droid.dx.cf.code.ByteOps.IF_ICMPGE: {
                return RegOps.IF_GE;
            }
            case com.droid.dx.cf.code.ByteOps.IFGT:
            case com.droid.dx.cf.code.ByteOps.IF_ICMPGT: {
                return RegOps.IF_GT;
            }
            case com.droid.dx.cf.code.ByteOps.IFLE:
            case com.droid.dx.cf.code.ByteOps.IF_ICMPLE: {
                return RegOps.IF_LE;
            }
            case com.droid.dx.cf.code.ByteOps.GOTO: {
                return RegOps.GOTO;
            }
            case com.droid.dx.cf.code.ByteOps.LOOKUPSWITCH: {
                return RegOps.SWITCH;
            }
            case com.droid.dx.cf.code.ByteOps.IRETURN:
            case com.droid.dx.cf.code.ByteOps.RETURN: {
                return RegOps.RETURN;
            }
            case com.droid.dx.cf.code.ByteOps.GETSTATIC: {
                return RegOps.GET_STATIC;
            }
            case com.droid.dx.cf.code.ByteOps.PUTSTATIC: {
                return RegOps.PUT_STATIC;
            }
            case com.droid.dx.cf.code.ByteOps.GETFIELD: {
                return RegOps.GET_FIELD;
            }
            case com.droid.dx.cf.code.ByteOps.PUTFIELD: {
                return RegOps.PUT_FIELD;
            }
            case com.droid.dx.cf.code.ByteOps.INVOKEVIRTUAL: {
                com.droid.dx.rop.cst.CstMethodRef ref = (com.droid.dx.rop.cst.CstMethodRef) cst;
                // The java bytecode specification does not explicitly disallow
                // invokevirtual calls to any instance method, though it
                // specifies that instance methods and private methods "should" be
                // called using "invokespecial" instead of "invokevirtual".
                // Several bytecode tools generate "invokevirtual" instructions for
                // invocation of private methods.
                //
                // The dalvik opcode specification on the other hand allows
                // invoke-virtual to be used only with "normal" virtual methods,
                // i.e, ones that are not private, static, final or constructors.
                // We therefore need to transform invoke-virtual calls to private
                // instance methods to invoke-direct opcodes.
                //
                // Note that it assumes that all methods for a given class are
                // defined in the same dex file.
                //
                // NOTE: This is a slow O(n) loop, and can be replaced with a
                // faster implementation (at the cost of higher memory usage)
                // if it proves to be a hot area of code.
                if (ref.getDefiningClass().equals(method.getDefiningClass())) {
                    for (int i = 0; i < methods.size(); ++i) {
                        final Method m = methods.get(i);
                        if (com.droid.dx.rop.code.AccessFlags.isPrivate(m.getAccessFlags()) &&
                                ref.getNat().equals(m.getNat())) {
                            return RegOps.INVOKE_DIRECT;
                        }
                    }
                }
                return RegOps.INVOKE_VIRTUAL;
            }
            case com.droid.dx.cf.code.ByteOps.INVOKESPECIAL: {
                /*
                 * Determine whether the opcode should be
                 * INVOKE_DIRECT or INVOKE_SUPER. See vmspec-2 section 6
                 * on "invokespecial" as well as section 4.8.2 (7th
                 * bullet point) for the gory details.
                 */
                com.droid.dx.rop.cst.CstMethodRef ref = (com.droid.dx.rop.cst.CstMethodRef) cst;
                if (ref.isInstanceInit() ||
                    (ref.getDefiningClass().equals(method.getDefiningClass())) ||
                    !method.getAccSuper()) {
                    return RegOps.INVOKE_DIRECT;
                }
                return RegOps.INVOKE_SUPER;
            }
            case com.droid.dx.cf.code.ByteOps.INVOKESTATIC: {
                return RegOps.INVOKE_STATIC;
            }
            case com.droid.dx.cf.code.ByteOps.INVOKEINTERFACE: {
                return RegOps.INVOKE_INTERFACE;
            }
            case com.droid.dx.cf.code.ByteOps.NEW: {
                return RegOps.NEW_INSTANCE;
            }
            case com.droid.dx.cf.code.ByteOps.NEWARRAY:
            case com.droid.dx.cf.code.ByteOps.ANEWARRAY: {
                return RegOps.NEW_ARRAY;
            }
            case com.droid.dx.cf.code.ByteOps.ARRAYLENGTH: {
                return RegOps.ARRAY_LENGTH;
            }
            case com.droid.dx.cf.code.ByteOps.ATHROW: {
                return RegOps.THROW;
            }
            case com.droid.dx.cf.code.ByteOps.CHECKCAST: {
                return RegOps.CHECK_CAST;
            }
            case com.droid.dx.cf.code.ByteOps.INSTANCEOF: {
                return RegOps.INSTANCE_OF;
            }
            case com.droid.dx.cf.code.ByteOps.MONITORENTER: {
                return RegOps.MONITOR_ENTER;
            }
            case com.droid.dx.cf.code.ByteOps.MONITOREXIT: {
                return RegOps.MONITOR_EXIT;
            }
        }

        throw new RuntimeException("shouldn't happen");
    }
}
