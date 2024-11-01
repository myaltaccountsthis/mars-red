package mars.venus.actions.run;

import mars.simulator.SimulatorException;
import mars.mips.hardware.RegisterFile;
import mars.simulator.ProgramArgumentList;
import mars.simulator.Simulator;
import mars.venus.actions.VenusAction;
import mars.venus.VenusUI;
import mars.venus.execute.ProgramStatus;

import javax.swing.*;
import java.awt.event.ActionEvent;

/*
Copyright (c) 2003-2007,  Pete Sanderson and Kenneth Vollmar

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
 * Action for the Run -> Start menu item.
 */
public class RunStartAction extends VenusAction {
    public RunStartAction(VenusUI gui, Integer mnemonic, KeyStroke accel) {
        super(gui, "Start", VenusUI.getSVGActionIcon("start.svg"), "Run the current program", mnemonic, accel);
    }

    /**
     * Run the program.
     */
    @Override
    public void actionPerformed(ActionEvent event) {
        if (this.gui.getProgramStatus() == ProgramStatus.NOT_STARTED) {
            // DPS 17-July-2008
            // Store any program arguments into MIPS memory and registers before
            // execution begins. Arguments go into the gap between $sp and kernel memory.
            // Argument pointers and count go into runtime stack and $sp is adjusted accordingly.
            // $a0 gets argument count (argc), $a1 gets stack address of first arg pointer (argv).
            String programArguments = this.gui.getMainPane().getExecuteTab().getTextSegmentWindow().getProgramArguments();
            if (programArguments != null && !programArguments.isEmpty() && this.gui.getSettings().useProgramArguments.get()) {
                new ProgramArgumentList(programArguments).storeProgramArguments();
            }
        }

        try {
            int[] breakPoints = this.gui.getMainPane().getExecuteTab().getTextSegmentWindow().getSortedBreakPointsArray();
            Simulator.getInstance().simulate(RegisterFile.getProgramCounter(), -1, breakPoints);
        }
        catch (SimulatorException exception) {
            // Ignore
        }
    }

    @Override
    public void update() {
        this.setEnabled(this.gui.getProgramStatus().canStart());
    }
}