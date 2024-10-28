package mars.mips.instructions.syscalls;

import mars.Application;
import mars.SimulatorException;
import mars.assembler.BasicStatement;
import mars.mips.hardware.RegisterFile;

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
 * Service to exit the MIPS program with return value given in $a0.  Ignored if running from GUI.
 */
public class SyscallExit2 extends AbstractSyscall {
    /**
     * Build an instance of the syscall with its default service number and name.
     */
    @SuppressWarnings("unused")
    public SyscallExit2() {
        super(17, "Exit2");
    }

    /**
     * Performs syscall function to exit the MIPS program with return value given in $a0.
     * If running in command mode, MARS will exit with that value.  If running under GUI,
     * return value is displayed in the message console.
     */
    @Override
    public void simulate(BasicStatement statement) throws SimulatorException {
        int exitCode = RegisterFile.getValue(4);

        if (Application.getGUI() == null) {
            Application.exitCode = exitCode;
        }
        // Empty error list indicates a clean exit
        throw new SimulatorException(exitCode);
    }
}