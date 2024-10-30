package mars.simulator;

import mars.Application;
import mars.assembler.BasicStatement;
import mars.mips.hardware.*;
import mars.mips.instructions.Instruction;

import java.util.Arrays;

/*
Copyright (c) 2003-2006,  Pete Sanderson and Kenneth Vollmar

Developed by Pete Sanderson (psanderson@otterbein.edu)
and Kenneth Vollmar (kenvollmar@missouristate.edu)

Permission is hereby granted, free of charge, to any person obtaining 
a copy of this software and associated documentation files (the 
"Software"), to deal in the Software without restriction, including 
without limitation the rights to use, copy, modify, merge, publish, 
distribute, sublicense, and/or sell copies of the Software, and to 
permit persons to whom the Software is furnished to do so, subject 
to the following conditions:

The above copyright notice and this permission notice shall be 
included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, 
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF 
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. 
IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR 
ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF 
CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION 
WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

(MIT license, http://www.opensource.org/licenses/mit-license.html)
*/

/**
 * Used to "step backward" through execution, undoing each instruction.
 *
 * @author Pete Sanderson
 * @version February 2006
 */
public class BackStepper {
    // Flag to mark BackStep object as prepresenting specific situation: user manipulates
    // memory/register value via GUI after assembling program but before running it.
    private static final int NOT_PC_VALUE = -1;

    private boolean isEnabled;
    private final BackStepStack backSteps;

    // One can argue using java.util.Stack, given its clumsy implementation.
    // A homegrown linked implementation will be more streamlined, but
    // I anticipate that backstepping will only be used during timed
    // (currently max 30 instructions/second) or stepped execution, where
    // performance is not an issue.  Its Vector implementation may result
    // in quicker garbage collection than a pure linked list implementation.

    /**
     * Create a fresh BackStepper.  It is enabled, which means all
     * subsequent instruction executions will have their "undo" action
     * recorded here.
     */
    public BackStepper() {
        isEnabled = true;
        backSteps = new BackStepStack(Application.MAXIMUM_BACKSTEPS);
    }

    public void reset() {
        this.backSteps.clear();
    }

    /**
     * Determine whether execution "undo" steps are currently being recorded.
     *
     * @return true if undo steps being recorded, false if not.
     */
    public boolean isEnabled() {
        return isEnabled;
    }

    /**
     * Set enable status.
     *
     * @param state If true, will begin (or continue) recoding "undo" steps.  If false, will stop.
     */
    public void setEnabled(boolean state) {
        isEnabled = state;
    }

    /**
     * Test whether there are steps that can be undone.
     *
     * @return true if there are no steps to be undone, false otherwise.
     */
    public boolean isEmpty() {
        return backSteps.isEmpty();
    }

    /**
     * Determine whether the next back-step action occurred as the result of
     * an instruction that executed in the "delay slot" of a delayed branch.
     *
     * @return true if next backstep is instruction that executed in delay slot,
     *     false otherwise.
     */
    // Added 25 June 2007
    public boolean isInDelaySlot() {
        return !isEmpty() && backSteps.peek().isInDelaySlot;
    }

    /**
     * Carry out a "back step", which will undo the latest execution step.
     * Does nothing if backstepping not enabled or if there are no steps to undo.
     */
    // Note that there may be more than one "step" in an instruction execution; for
    // instance the multiply, divide, and double-precision floating point operations
    // all store their result in register pairs which results in two store operations.
    // Both must be undone transparently, so we need to detect that multiple steps happen
    // together and carry out all of them here.
    // Use a do-while loop based on the backstep's program statement reference.
    public void backStep() {
        if (isEnabled && !backSteps.isEmpty()) {
            BasicStatement statement = backSteps.peek().statement;
            isEnabled = false; // MUST DO THIS SO METHOD CALL IN SWITCH WILL NOT RESULT IN NEW ACTION ON STACK!
            do {
                BackStep step = backSteps.pop();
                if (step.programCounter != NOT_PC_VALUE) {
                    RegisterFile.setProgramCounter(step.programCounter);
                }
                try {
                    switch (step.action) {
                        case MEMORY_RESTORE_WORD -> Memory.getInstance().storeWord(step.param1, step.param2, true);
                        case MEMORY_RESTORE_HALF -> Memory.getInstance().storeHalfword(step.param1, step.param2, true);
                        case MEMORY_RESTORE_BYTE -> Memory.getInstance().storeByte(step.param1, step.param2, true);
                        case REGISTER_RESTORE -> RegisterFile.updateRegister(step.param1, step.param2);
                        case PC_RESTORE -> RegisterFile.setProgramCounter(step.param1);
                        case COPROC0_REGISTER_RESTORE -> Coprocessor0.updateRegister(step.param1, step.param2);
                        case COPROC1_REGISTER_RESTORE -> Coprocessor1.updateRegister(step.param1, step.param2);
                        case COPROC1_CONDITION_CLEAR -> Coprocessor1.clearConditionFlag(step.param1);
                        case COPROC1_CONDITION_SET -> Coprocessor1.setConditionFlag(step.param1);
                        case DO_NOTHING -> {}
                    }
                }
                catch (AddressErrorException exception) {
                    // If the original action did not cause an exception this will not either.
                    throw new RuntimeException("accessed invalid memory address while backstepping");
                }
            }
            while (!backSteps.isEmpty() && statement == backSteps.peek().statement);
            isEnabled = true;  // RESET IT (was disabled at top of loop -- see comment)
        }
    }


    /**
     * Convenience method called below to get program counter value.  If it needs to be
     * be modified (e.g. to subtract 4) that can be done here in one place.
     */
    private int pc() {
        // PC incremented prior to instruction simulation, so need to adjust for that.
        return RegisterFile.getProgramCounter() - Instruction.BYTES_PER_INSTRUCTION;
    }

    /**
     * Add a new "back step" (the undo action) to the stack. The action here
     * is to restore a memory word value.
     *
     * @param address The affected memory address.
     * @param value   The "restore" value to be stored there.
     */
    public void addMemoryRestoreWord(int address, int value) {
        backSteps.push(BackStepAction.MEMORY_RESTORE_WORD, pc(), address, value);
    }

    /**
     * Add a new "back step" (the undo action) to the stack.  The action here
     * is to restore a memory half-word value.
     *
     * @param address The affected memory address.
     * @param value   The "restore" value to be stored there, in low order half.
     */
    public void addMemoryRestoreHalf(int address, int value) {
        backSteps.push(BackStepAction.MEMORY_RESTORE_HALF, pc(), address, value);
    }

    /**
     * Add a new "back step" (the undo action) to the stack.  The action here
     * is to restore a memory byte value.
     *
     * @param address The affected memory address.
     * @param value   The "restore" value to be stored there, in low order byte.
     */
    public void addMemoryRestoreByte(int address, int value) {
        backSteps.push(BackStepAction.MEMORY_RESTORE_BYTE, pc(), address, value);
    }

    /**
     * Add a new "back step" (the undo action) to the stack.  The action here
     * is to restore a register file register value.
     *
     * @param register The affected register number.
     * @param value    The "restore" value to be stored there.
     */
    public void addRegisterFileRestore(int register, int value) {
        backSteps.push(BackStepAction.REGISTER_RESTORE, pc(), register, value);
    }

    /**
     * Add a new "back step" (the undo action) to the stack.  The action here
     * is to restore the program counter.
     *
     * @param value The "restore" value to be stored there.
     */
    public void addPCRestore(int value) {
        // adjust for value reflecting incremented PC.
        value -= Instruction.BYTES_PER_INSTRUCTION;
        // Use "value" insead of "pc()" for second arg because RegisterFile.getProgramCounter()
        // returns branch target address at this point.
        backSteps.push(BackStepAction.PC_RESTORE, value, value);
    }

    /**
     * Add a new "back step" (the undo action) to the stack.  The action here
     * is to restore a coprocessor 0 register value.
     *
     * @param register The affected register number.
     * @param value    The "restore" value to be stored there.
     */
    public void addCoprocessor0Restore(int register, int value) {
        backSteps.push(BackStepAction.COPROC0_REGISTER_RESTORE, pc(), register, value);
    }

    /**
     * Add a new "back step" (the undo action) to the stack.  The action here
     * is to restore a coprocessor 1 register value.
     *
     * @param register The affected register number.
     * @param value    The "restore" value to be stored there.
     */
    public void addCoprocessor1Restore(int register, int value) {
        backSteps.push(BackStepAction.COPROC1_REGISTER_RESTORE, pc(), register, value);
    }

    /**
     * Add a new "back step" (the undo action) to the stack.  The action here
     * is to set the given coprocessor 1 condition flag (to 1).
     *
     * @param flag The condition flag number.
     */
    public void addConditionFlagSet(int flag) {
        backSteps.push(BackStepAction.COPROC1_CONDITION_SET, pc(), flag);
    }

    /**
     * Add a new "back step" (the undo action) to the stack.  The action here
     * is to clear the given coprocessor 1 condition flag (to 0).
     *
     * @param flag The condition flag number.
     */
    public void addConditionFlagClear(int flag) {
        backSteps.push(BackStepAction.COPROC1_CONDITION_CLEAR, pc(), flag);
    }

    /**
     * Add a new "back step" (the undo action) to the stack.  The action here
     * is to do nothing!  This is just a place holder so when user is backstepping
     * through the program no instructions will be skipped.  Cosmetic. If the top of the
     * stack has the same PC counter, the do-nothing action will not be added.
     *
     * @param programCounter The program counter to check against the top of the stack.
     */
    public void addDoNothing(int programCounter) {
        if (backSteps.isEmpty() || backSteps.peek().programCounter != programCounter) {
            backSteps.push(BackStepAction.DO_NOTHING, programCounter);
        }
    }

    /**
     * The types of "undo" actions.
     */
    private enum BackStepAction {
        MEMORY_RESTORE_WORD,
        MEMORY_RESTORE_HALF,
        MEMORY_RESTORE_BYTE,
        REGISTER_RESTORE,
        PC_RESTORE,
        COPROC0_REGISTER_RESTORE,
        COPROC1_REGISTER_RESTORE,
        COPROC1_CONDITION_CLEAR,
        COPROC1_CONDITION_SET,
        DO_NOTHING,
    }

    /**
     * Represents a "back step" (undo action) on the stack.
     */
    private static class BackStep {
        private BackStepAction action; // what "undo" action to perform
        private int programCounter; // program counter value when original step occurred
        private int param1; // optional first parameter required by that action
        private int param2; // optional second parameter required by that action
        private BasicStatement statement; // statement whose action is being "undone" here
        private boolean isInDelaySlot; // true if instruction executed in "delay slot" (delayed branching enabled)

        /**
         * It is critical that BackStep object get its values by calling this method
         * rather than assigning to individual members, because of the technique used
         * to set its statement member (and possibly programCounter).
         */
        private void assign(BackStepAction action, int programCounter, int param1, int param2) {
            this.action = action;
            this.programCounter = programCounter;
            this.param1 = param1;
            this.param2 = param2;
            try {
                // Client does not have direct access to program statement, and rather than making all
                // of them go through the methods below to obtain it, we will do it here.
                // Want the program statement but do not want observers notified.
                this.statement = Memory.getInstance().fetchStatement(programCounter, false);
            }
            catch (AddressErrorException exception) {
                // The only situation causing this so far: user modifies memory or register
                // contents through direct manipulation on the GUI, after assembling the program but
                // before starting to run it (or after backstepping all the way to the start).
                // The action will not be associated with any instruction, but will be carried out
                // when popped.
                this.statement = null;
                this.programCounter = NOT_PC_VALUE; // Backstep method above will see this as flag to not set PC
            }
            this.isInDelaySlot = Simulator.getInstance().isInDelaySlot(); // ADDED 25 June 2007
        }
    }

    /**
     * Special purpose stack class for backstepping.  You've heard of circular queues
     * implemented with an array, right?  This is a circular stack!  When full, the
     * newly-pushed item overwrites the oldest item, with circular top!  All operations
     * are constant time.  It's synchronized too, to be safe (is used by both the
     * simulation thread and the GUI thread for the back-step button).
     * Upon construction, it is filled with newly-created empty BackStep objects which
     * will exist for the life of the stack.  Push does not create a BackStep object
     * but instead overwrites the contents of the existing one.  Thus during MIPS
     * program (simulated) execution, BackStep objects are never created or junked
     * regardless of how many steps are executed.  This will speed things up a bit
     * and make life easier for the garbage collector.
     */
    private static class BackStepStack {
        private final int capacity;
        private int size;
        private int top;
        private final BackStep[] stack;

        /**
         * Stack is created upon successful assembly or reset.  The one-time overhead of
         * creating all the BackStep objects will not be noticed by the user, and enhances
         * runtime performance by not having to create or recycle them during MIPS
         * program execution.
         */
        private BackStepStack(int capacity) {
            this.capacity = capacity;
            this.size = 0;
            this.top = -1;
            this.stack = new BackStep[capacity];
            for (int i = 0; i < capacity; i++) {
                this.stack[i] = new BackStep();
            }
        }

        private synchronized void clear() {
            this.size = 0;
            this.top = -1;
        }

        private synchronized boolean isEmpty() {
            return size == 0;
        }

        private synchronized void push(BackStepAction action, int programCounter, int param1, int param2) {
            if (size == 0) {
                top = 0;
                size++;
            }
            else if (size < capacity) {
                top = (top + 1) % capacity;
                size++;
            }
            else {
                // size == capacity.  The top moves up one, replacing oldest entry (goodbye!)
                top = (top + 1) % capacity;
            }
            // We'll re-use existing objects rather than create/discard each time.
            // Must use assign() method rather than series of assignment statements!
            stack[top].assign(action, programCounter, param1, param2);
        }

        private synchronized void push(BackStepAction action, int programCounter, int param1) {
            push(action, programCounter, param1, 0);
        }

        private synchronized void push(BackStepAction action, int programCounter) {
            push(action, programCounter, 0, 0);
        }

        /**
         * NO PROTECTION.  This class is used only within this file so there is no excuse
         * for trying to pop from empty stack.
         */
        private synchronized BackStep pop() {
            BackStep bs;
            bs = stack[top];
            if (size == 1) {
                top = -1;
            }
            else {
                top = (top + capacity - 1) % capacity;
            }
            size--;
            return bs;
        }

        /**
         * NO PROTECTION.  This class is used only within this file so there is no excuse
         * for trying to peek from empty stack.
         */
        private synchronized BackStep peek() {
            return stack[top];
        }
    }
}