package mars;

import mars.assembler.*;
import mars.mips.hardware.RegisterFile;
import mars.simulator.BackStepper;
import mars.simulator.Simulator;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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
 * Internal representations of MIPS program.  Connects source, tokens and machine code.  Having
 * all these structures available facilitates construction of good messages, debugging, and easy simulation.
 *
 * @author Pete Sanderson
 * @version August 2003
 */
public class Program {
    private boolean steppedExecution = false;

    private String filename;
    private List<String> sourceList;
    private List<ProgramStatement> parsedList;
    private List<ProgramStatement> machineList;
    private BackStepper backStepper;

    /**
     * Produces name of associated source code file.
     *
     * @return File name as String.
     */
    public String getFilename() {
        return filename;
    }

    /**
     * Assigns list of parsed source code statements.
     */
    public void setParsedStatements(List<ProgramStatement> parsedList) {
        this.parsedList = parsedList;
    }

    /**
     * Produces list of parsed source code statements.
     *
     * @return List of ProgramStatement.  Each ProgramStatement represents a parsed MIPS statement.
     */
    public List<ProgramStatement> getParsedStatements() {
        return parsedList;
    }

    /**
     * Produces list of machine statements that are assembled from the program.
     *
     * @return List of ProgramStatement.  Each ProgramStatement represents an assembled basic MIPS instruction.
     * @see ProgramStatement
     */
    public List<ProgramStatement> getMachineStatements() {
        return machineList;
    }

    /**
     * Returns BackStepper associated with this program.  It is created upon successful assembly.
     *
     * @return BackStepper object, null if there is none.
     */
    public BackStepper getBackStepper() {
        return backStepper;
    }

    /**
     * Returns SymbolTable associated with this program.  It is created at assembly time,
     * and stores local labels (those not declared using .globl directive).
     */
    public SymbolTable getLocalSymbolTable() {
        return localSymbolTable;
    }

    /**
     * Produces specified line of MIPS source program.
     *
     * @param lineNum Line number of MIPS source program to get.  Line 1 is first line.
     * @return Returns specified line of MIPS source.  If outside the line range,
     *     it returns null.  Line 1 is first line.
     */
    public String getSourceLine(int lineNum) {
        if (1 <= lineNum && lineNum <= sourceList.size()) {
            return sourceList.get(lineNum - 1);
        }
        else {
            return null;
        }
    }

    /**
     * Reads MIPS source code from file into structure.  Will always read from file.
     * It is GUI responsibility to assure that source edits are written to file
     * when user selects compile or run/step options.
     *
     * @param filename String containing name of MIPS source code file.
     * @throws ProcessingException Will throw exception if there is any problem reading the file.
     */
    public void readSource(String filename) throws ProcessingException {
        this.filename = filename;

        try {
            BufferedReader inputFile = new BufferedReader(new FileReader(filename));
            // Gather all lines from the source file into a list of strings
            sourceList = inputFile.lines().collect(Collectors.toList());
        }
        catch (Exception e) {
            sourceList = new ArrayList<>();
            ErrorList errors = new ErrorList();
            errors.add(new ErrorMessage((Program) null, 0, 0, e.toString()));
            throw new ProcessingException(errors);
        }
    }

    /**
     * Prepares the given list of files for assembly.  This involves
     * reading and tokenizing all the source files.  There may be only one.
     *
     * @param pathnames        List containing the source file path(s) in no particular order
     * @param leadPathname     String containing path of source file that needs to go first and
     *                         will be represented by "this" Program object.
     * @param exceptionHandler String containing path of source file containing exception
     *                         handler.  This will be assembled first, even ahead of leadPathname, to allow it to
     *                         include "startup" instructions loaded beginning at 0x00400000.  Specify null or
     *                         empty String to indicate there is no such designated exception handler.
     * @return List containing one Program object for each file to assemble.
     *     objects for any additional files (send list to assembler)
     * @throws ProcessingException Will throw exception if errors occurred while reading or tokenizing.
     */
    public List<Program> prepareFilesForAssembly(List<String> pathnames, String leadPathname, String exceptionHandler) throws ProcessingException {
        List<Program> programsToAssemble = new ArrayList<>();
        int leadFilePosition = 0;
        if (exceptionHandler != null && !exceptionHandler.isEmpty()) {
            pathnames.add(0, exceptionHandler);
            leadFilePosition = 1;
        }
        for (String filename : pathnames) {
            Program program = (filename.equals(leadPathname)) ? this : new Program();
            program.readSource(filename);
            program.tokenize();
            if (program == this && !programsToAssemble.isEmpty()) {
                // Insert this program at the beginning of the list, but after exception handler if present
                programsToAssemble.add(leadFilePosition, program);
            }
            else {
                programsToAssemble.add(program);
            }
        }
        return programsToAssemble;
    }

    /**
     * Assembles the MIPS source program. All files comprising the program must have
     * already been tokenized.  Assembler warnings are not considered errors.
     *
     * @param programsToAssemble       List of Program objects, each representing a tokenized source file.
     * @param extendedAssemblerEnabled A boolean value - true means extended (pseudo) instructions
     *                                 are permitted in source code and false means they are to be flagged as errors.
     * @return ErrorList containing nothing or only warnings (otherwise would have thrown exception).
     * @throws ProcessingException Will throw exception if errors occurred while assembling.
     */
    public ErrorList assemble(List<Program> programsToAssemble, boolean extendedAssemblerEnabled) throws ProcessingException {
        return assemble(programsToAssemble, extendedAssemblerEnabled, false);
    }

    /**
     * Assembles the MIPS source program. All files comprising the program must have
     * already been tokenized.
     *
     * @param programsToAssemble       List of Program objects, each representing a tokenized source file.
     * @param extendedAssemblerEnabled A boolean value - true means extended (pseudo) instructions
     *                                 are permitted in source code and false means they are to be flagged as errors
     * @param warningsAreErrors        A boolean value - true means assembler warnings will be considered errors and terminate
     *                                 the assemble; false means the assembler will produce warning message but otherwise ignore warnings.
     * @return ErrorList containing nothing or only warnings (otherwise would have thrown exception).
     * @throws ProcessingException Will throw exception if errors occurred while assembling.
     */
    public ErrorList assemble(List<Program> programsToAssemble, boolean extendedAssemblerEnabled, boolean warningsAreErrors) throws ProcessingException {
        this.backStepper = null;
        Assembler assembler = new Assembler();
        this.machineList = assembler.assembleFilenames(programsToAssemble, extendedAssemblerEnabled, warningsAreErrors);
        this.backStepper = new BackStepper();
        return assembler.getErrorList();
    }

    /**
     * Simulates execution of the MIPS program. Program must have already been assembled.
     * Begins simulation at beginning of text segment and continues to completion.
     *
     * @param breakPoints Array of breakpoints (PC addresses).  (Can be null.)
     * @throws ProcessingException Thrown if errors occurred while simulating.
     */
    public void simulate(int[] breakPoints) throws ProcessingException {
        this.simulate(breakPoints, -1);
    }

    /**
     * Simulates execution of the MIPS program. Program must have already been assembled.
     * Begins simulation at beginning of text segment and continues to completion or
     * until the specified maximum number of steps are simulated.
     *
     * @param maxSteps Maximum number of steps to simulate.  Default -1 means no maximum.
     * @throws ProcessingException Thrown if errors occurred while simulating.
     */
    public void simulate(int maxSteps) throws ProcessingException {
        this.simulate(null, maxSteps);
    }

    /**
     * Simulates execution of the MIPS program. Program must have already been assembled.
     * Begins simulation at current program counter address and continues until stopped,
     * paused, maximum steps exceeded, or exception occurs.
     *
     * @param breakPoints Array of breakpoints (PC addresses).  (Can be null.)
     * @param maxSteps    Maximum number of steps to simulate.  Default -1 means no maximum.
     * @throws ProcessingException Thrown if errors occurred while simulating.
     */
    public void simulate(int[] breakPoints, int maxSteps) throws ProcessingException {
        steppedExecution = false;
        Simulator.getInstance().simulate(this, RegisterFile.getProgramCounter(), maxSteps, breakPoints);
    }

    /**
     * Simulates execution of the MIPS program. Program must have already been assembled.
     * Begins simulation at current program counter address and executes one step.
     *
     * @throws ProcessingException Thrown if errors occurred while simulating.
     */
    public void simulateStep() throws ProcessingException {
        steppedExecution = true;
        Simulator.getInstance().simulate(this, RegisterFile.getProgramCounter(), 1, null);
    }

    /**
     * Will be true only while in process of simulating a program statement
     * in step mode (e.g. returning to GUI after each step).  This is used to
     * prevent spurious AccessNotices from being sent from Memory and Register
     * to observers at other times (e.g. while updating the data and register
     * displays, while assembling program's data segment, etc).
     */
    public boolean inSteppedExecution() {
        return steppedExecution;
    }
}
