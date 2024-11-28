package mars.mips.instructions.syscalls;

import mars.simulator.SimulatorException;
import mars.assembler.BasicStatement;
import mars.mips.hardware.AddressErrorException;
import mars.mips.hardware.Memory;
import mars.mips.hardware.Processor;
import mars.simulator.Simulator;

import java.nio.file.Path;

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
 * Service to open file name specified by $a0. File descriptor returned in $v0.
 * (This was changed from $a0 in MARS 3.7 for SPIM compatibility.  The table
 * in the Computer Organization and Design book erroneously shows $a0.)
 */
public class SyscallOpen extends AbstractSyscall {
    /**
     * Build an instance of the syscall with its default service number and name.
     */
    @SuppressWarnings("unused")
    public SyscallOpen() {
        super(13, "Open");
    }

    /**
     * Performs syscall function to open file name specified by $a0. File descriptor returned
     * in $v0.  Only supported flags ($a1) are read-only (0), write-only (1) and
     * write-append (9). write-only flag creates file if it does not exist, so it is technically
     * write-create.  write-append will start writing at end of existing file.
     * Mode ($a2) is ignored.
     */
    @Override
    public void simulate(BasicStatement statement) throws SimulatorException {
        // NOTE: with MARS 3.7, return changed from $a0 to $v0 and the terminology
        // of 'flags' and 'mode' was corrected (they had been reversed).
        //
        // Arguments: $a0 = filename (string), $a1 = flags, $a2 = mode
        // Result: file descriptor (in $v0)
        // This code implements the flags:
        // Read          flag = 0
        // Write         flag = 1
        // Read/Write    NOT IMPLEMENTED
        // Write/append  flag = 9
        // This code implements the modes:
        // NO MODES IMPLEMENTED  -- MODE IS IGNORED
        // Returns in $v0: a "file descriptor" if opened, or -1 if error

        String filename;
        try {
            // Read a null-terminated string from memory
            filename = Memory.getInstance().fetchNullTerminatedString(Processor.getValue(Processor.ARGUMENT_0));
        }
        catch (AddressErrorException exception) {
            throw new SimulatorException(statement, exception);
        }

        int descriptor = Simulator.getInstance().getSystemIO().openFile(Path.of(filename), Processor.getValue(Processor.ARGUMENT_1));

        Processor.setValue(Processor.VALUE_0, descriptor); // Set returned descriptor in register
    }
}