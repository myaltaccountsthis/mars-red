package mars.simulator;

import mars.*;
import mars.mips.hardware.Coprocessor0;
import mars.mips.hardware.Coprocessor1;
import mars.mips.hardware.Processor;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/*
Copyright (c) 2003-2010,  Pete Sanderson and Kenneth Vollmar

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
 * Used to simulate the execution of an assembled MIPS program.
 *
 * @author Pete Sanderson, August 2005
 */
public class Simulator {
    /**
     * Constant that represents unlimited run speed.  Compare with return value of
     * getRunSpeed() to determine if set to unlimited.  At the unlimited setting, the GUI
     * will not attempt to update register and memory contents as each instruction
     * is executed.  This is the only possible value for command-line use of Mars.
     */
    public static final double UNLIMITED_SPEED = Double.POSITIVE_INFINITY;

    private static Simulator instance = null;

    private final List<SimulatorListener> guiListeners;
    private final List<SimulatorListener> threadListeners;
    private final BackStepper backStepper;
    private final SystemIO systemIO;
    private Integer delayedJumpAddress;
    private volatile double runSpeed;
    /**
     * Others can set this to indicate an external interrupt.
     * The device is identified by the address of its MMIO control register.
     * DPS 23 July 2008
     */
    private volatile Integer externalInterruptDevice;
    private final List<Runnable> queuedStateChanges;

    private SimulatorThread thread;
    private boolean hasQueuedStepEvent;

    /**
     * Returns the singleton instance of the MIPS simulator.
     *
     * @return The <code>Simulator</code> object in use.
     */
    public static Simulator getInstance() {
        if (Simulator.instance == null) {
            Simulator.instance = new Simulator();
        }
        return Simulator.instance;
    }

    private Simulator() {
        this.guiListeners = new ArrayList<>();
        this.threadListeners = new ArrayList<>();
        this.backStepper = new BackStepper();
        this.systemIO = new SystemIO();
        this.delayedJumpAddress = null;
        this.runSpeed = UNLIMITED_SPEED;
        this.externalInterruptDevice = null;
        this.queuedStateChanges = new ArrayList<>();
        this.thread = null;
        this.hasQueuedStepEvent = false;
    }

    public boolean isRunning() {
        return this.thread != null;
    }

    public BackStepper getBackStepper() {
        return this.backStepper;
    }

    /**
     * Obtain the associated {@link SystemIO} instance, which handles I/O-related syscall functionality.
     *
     * @return The system I/O handler.
     */
    public SystemIO getSystemIO() {
        return this.systemIO;
    }

    public void reset() {
        Processor.reset();
        Coprocessor1.reset();
        Coprocessor0.reset();
        this.backStepper.reset();
        this.systemIO.resetFiles();
        this.delayedJumpAddress = null;
        this.externalInterruptDevice = null;
    }

    /**
     * Schedule a jump in execution to another point in the program.
     * If delayed branching is enabled, the actual jump will occur after the next instruction is executed.
     *
     * @param targetAddress The address of the instruction to jump to.
     */
    public void processJump(int targetAddress) {
        if (Application.getSettings().delayedBranchingEnabled.get()) {
            this.delayedJumpAddress = targetAddress;
        }
        else {
            Processor.setProgramCounter(targetAddress);
        }
    }

    /**
     * Determine whether or not the next instruction to be executed is in a
     * "delay slot."  This means delayed branching is enabled, the branch
     * condition has evaluated true, and the next instruction executed will
     * be the one following the branch.  It is said to occupy the "delay slot."
     * Normally programmers put a nop instruction here but it can be anything.
     *
     * @return <code>true</code> if the next instruction is in a delay slot, or <code>false</code> otherwise.
     */
    public boolean isInDelaySlot() {
        return this.delayedJumpAddress != null;
    }

    /**
     * Get the address scheduled to replace the program counter during the next clock cycle
     * for the purposes of delayed branching. If delayed branching is not enabled, this will always
     * be null. If no jump/branch has been scheduled, this will return null.
     *
     * @return The address execution is scheduled to jump to after the next cycle, or null if not applicable.
     */
    public Integer getDelayedJumpAddress() {
        return this.delayedJumpAddress;
    }

    /**
     * Clear the delayed jump address, if applicable. This is mainly used to reset the state of the delayed jump
     * after it has been processed by the simulator thread.
     */
    public void clearDelayedJumpAddress() {
        this.delayedJumpAddress = null;
    }

    /**
     * Get the current run speed of the simulator in instructions per second.
     *
     * @return The run speed, or {@link #UNLIMITED_SPEED} if the run speed is unlimited.
     * @see #isLimitingRunSpeed()
     */
    public double getRunSpeed() {
        return this.runSpeed;
    }

    /**
     * Determine whether the current run speed of the simulator is limited (that is, any value other than
     * {@link #UNLIMITED_SPEED}). When the run speed is limited, the simulator intentionally slows down execution
     * to allow the GUI to track the program counter, memory accesses, register updates, etc.
     *
     * @return <code>false</code> if the current run speed is {@link #UNLIMITED_SPEED}, or <code>true</code> otherwise.
     * @see #getRunSpeed()
     */
    public boolean isLimitingRunSpeed() {
        return this.runSpeed != UNLIMITED_SPEED;
    }

    /**
     * Set the current run speed of the simulator in instructions per second.
     *
     * @param runSpeed The run speed, or {@link #UNLIMITED_SPEED} to disable run speed limiting.
     */
    public void setRunSpeed(double runSpeed) {
        // The below condition is used instead of "runSpeed <= 0.0" in order to catch NaN values
        if (!(runSpeed > 0.0)) {
            throw new IllegalArgumentException("invalid run speed " + runSpeed);
        }
        this.runSpeed = runSpeed;
    }

    /**
     * Get the identifier of the memory-mapped I/O device which flagged an external interrupt, if any.
     * Once this method is called, the external interrupt flag is reset.
     *
     * @return The device identifier, typically the address of the control register,
     *         or null if no interrupt was flagged.
     */
    public Integer checkExternalInterruptDevice() {
        Integer device = this.externalInterruptDevice;
        this.externalInterruptDevice = null;
        return device;
    }

    /**
     * Flag an external interrupt as a result of a memory-mapped I/O device.
     * <p>
     * This method may be called from any thread.
     *
     * @param device The device identifier, typically the address of the control register.
     */
    public void raiseExternalInterrupt(int device) {
        this.externalInterruptDevice = device;
    }

    public void changeState(Runnable stateChanger) {
        if (this.isRunning()) {
            synchronized (this.queuedStateChanges) {
                this.queuedStateChanges.add(stateChanger);
            }
        }
        else {
            this.flushStateChanges();
            stateChanger.run();
        }
    }

    public void flushStateChanges() {
        synchronized (this.queuedStateChanges) {
            for (Runnable stateChanger : this.queuedStateChanges) {
                stateChanger.run();
            }
            this.queuedStateChanges.clear();
        }
    }

    /**
     * Add a {@link SimulatorListener} whose callbacks will be executed on the GUI thread.
     *
     * @param listener The listener to add.
     */
    public void addGUIListener(SimulatorListener listener) {
        if (!this.guiListeners.contains(listener)) {
            this.guiListeners.add(listener);
        }
    }

    /**
     * Remove a {@link SimulatorListener} which was added via {@link #addGUIListener(SimulatorListener)}.
     *
     * @param listener The listener to remove.
     */
    public void removeGUIListener(SimulatorListener listener) {
        this.guiListeners.remove(listener);
    }

    /**
     * Add a {@link SimulatorListener} whose callbacks will be executed on the simulator thread.
     *
     * @param listener The listener to add.
     */
    public void addThreadListener(SimulatorListener listener) {
        if (!this.threadListeners.contains(listener)) {
            this.threadListeners.add(listener);
        }
    }

    /**
     * Remove a {@link SimulatorListener} which was added via {@link #addThreadListener(SimulatorListener)}.
     *
     * @param listener The listener to remove.
     */
    public void removeThreadListener(SimulatorListener listener) {
        this.threadListeners.remove(listener);
    }

    /**
     * Simulate execution of given MIPS program.  It must have already been assembled.
     *
     * @param programCounter Address of first instruction to simulate; this is the initial value of the program counter.
     * @param maxSteps       Maximum number of steps to perform before returning false (0 or less means no max).
     * @param breakpoints    Array of breakpoint program counter values. (Can be null.)
     * @throws SimulatorException Throws exception if run-time exception occurs.
     */
    public void simulate(int programCounter, int maxSteps, int[] breakpoints) throws SimulatorException {
        this.thread = new SimulatorThread(this, programCounter, maxSteps, breakpoints);
        this.thread.start();

        if (Application.getGUI() == null) {
            // The simulator was run from the command line

            // This is a slightly hacky way to get the exception out of the simulator thread (we love Java)
            final SimulatorException[] exception = new SimulatorException[1];
            SimulatorListener exceptionListener = new SimulatorListener() {
                @Override
                public void simulatorFinished(SimulatorFinishEvent event) {
                    exception[0] = event.getException();
                }
            };
            this.addThreadListener(exceptionListener);

            try {
                // Wait for the simulator thread to finish
                this.thread.join();
            }
            catch (InterruptedException interruptedException) {
                // This should not happen, as the simulator thread should handle the interrupt
                System.err.println("Error: unhandled simulator interrupt: " + interruptedException);
            }
            this.thread = null;
            this.removeThreadListener(exceptionListener);

            if (exception[0] != null) {
                throw exception[0];
            }
        }
    }

    /**
     * Flag the simulator to stop due to pausing. Once it has done so,
     * {@link SimulatorListener#simulatorPaused(SimulatorPauseEvent)} will be called for all registered listeners.
     */
    public void pause() {
        if (this.thread != null) {
            this.thread.stopForPause();
            this.thread = null;
            this.flushStateChanges();
        }
    }

    /**
     * Flag the simulator to stop due to termination. Once it has done so,
     * {@link SimulatorListener#simulatorFinished(SimulatorFinishEvent)} will be called for all registered listeners.
     * <p>
     * Note: Unlike {@link #pause()}, this may be called while the simulator is paused.
     */
    public void terminate() {
        if (this.thread != null) {
            this.thread.stopForTermination();
            this.thread = null;
            this.flushStateChanges();
        }
        else {
            this.dispatchFinishEvent(Processor.getProgramCounter(), SimulatorFinishEvent.Reason.EXTERNAL, null);
        }
    }

    /**
     * Called when the simulator has started execution of the current program.
     * Invokes {@link SimulatorListener#simulatorStarted(SimulatorStartEvent)} for all listeners.
     */
    public void dispatchStartEvent(int stepCount, int programCounter) {
        final SimulatorStartEvent event = new SimulatorStartEvent(this, stepCount, programCounter);
        for (SimulatorListener listener : this.threadListeners) {
            listener.simulatorStarted(event);
        }
        if (Application.getGUI() != null) {
            SwingUtilities.invokeLater(() -> {
                for (SimulatorListener listener : this.guiListeners) {
                    listener.simulatorStarted(event);
                }
            });
        }
    }

    /**
     * Called when the simulator has paused execution of the current program.
     * Invokes {@link SimulatorListener#simulatorPaused(SimulatorPauseEvent)} for all listeners.
     */
    public void dispatchPauseEvent(int stepCount, int programCounter, SimulatorPauseEvent.Reason reason) {
        final SimulatorPauseEvent event = new SimulatorPauseEvent(this, stepCount, programCounter, reason);
        for (SimulatorListener listener : this.threadListeners) {
            listener.simulatorPaused(event);
        }
        if (Application.getGUI() != null) {
            SwingUtilities.invokeLater(() -> {
                for (SimulatorListener listener : this.guiListeners) {
                    listener.simulatorPaused(event);
                }
            });
        }
    }

    /**
     * Called when the simulator has finished execution of the current program.
     * Invokes {@link SimulatorListener#simulatorFinished(SimulatorFinishEvent)} for all listeners.
     */
    public void dispatchFinishEvent(int programCounter, SimulatorFinishEvent.Reason reason, SimulatorException exception) {
        final SimulatorFinishEvent event = new SimulatorFinishEvent(this, programCounter, reason, exception);
        for (SimulatorListener listener : this.threadListeners) {
            listener.simulatorFinished(event);
        }
        if (Application.getGUI() != null) {
            SwingUtilities.invokeLater(() -> {
                for (SimulatorListener listener : this.guiListeners) {
                    listener.simulatorFinished(event);
                }
            });
        }
    }

    /**
     * Called when the simulator has finished executing an instruction, but only if the run speed is not unlimited.
     * Invokes {@link SimulatorListener#simulatorStepped()} for all listeners.
     * <p>
     * <b>Note: For very fast run speeds, GUI listeners may not receive all step events.</b> This is an intentional
     * feature to prevent overloading of the GUI event queue.
     */
    public void dispatchStepEvent() {
        synchronized (this.threadListeners) {
            for (SimulatorListener listener : this.threadListeners) {
                listener.simulatorStepped();
            }
        }
        if (Application.getGUI() != null && !this.hasQueuedStepEvent) {
            this.hasQueuedStepEvent = true;
            SwingUtilities.invokeLater(() -> {
                this.hasQueuedStepEvent = false;
                for (SimulatorListener listener : this.guiListeners) {
                    listener.simulatorStepped();
                }
            });
        }
    }
}
