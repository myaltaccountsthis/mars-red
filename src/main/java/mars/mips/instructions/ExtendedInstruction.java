package mars.mips.instructions;

import mars.assembler.OperandType;
import mars.assembler.extended.ExpansionTemplate;
import mars.mips.hardware.Memory;

import java.util.List;

/*
Copyright (c) 2003-2008,  Pete Sanderson and Kenneth Vollmar

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
 * Representation of an extended instruction, also known as a pseudo-instruction. Unlike basic instructions,
 * extended instructions do not exist at the hardware level. Instead, the assembler expands them into one or more
 * basic instructions which carry out the intended behavior.
 *
 * @author Pete Sanderson, August 2003
 * @see BasicInstruction
 * @see ExpansionTemplate
 */
public class ExtendedInstruction extends Instruction {
    private ExpansionTemplate standardExpansionTemplate;
    private ExpansionTemplate compactExpansionTemplate;

    /**
     * Create a new <code>ExtendedInstruction</code>.
     *
     * @param mnemonic     The instruction mnemonic used in assembly code (case-insensitive).
     * @param operandTypes The list of operand types for this instruction, which is used to select a specific
     *                     instruction from the group of instructions sharing a mnemonic.
     * @param title        The "long name" of this instruction, which should relate to the mnemonic.
     * @param description  A short human-readable description of what this instruction does when executed.
     */
    public ExtendedInstruction(String mnemonic, List<OperandType> operandTypes, String title, String description) {
        super(mnemonic, operandTypes, title, description);
        this.standardExpansionTemplate = null;
        this.compactExpansionTemplate = null;
    }

    /**
     * Determine whether or not this pseudo-instruction has a second
     * translation optimized for 16 bit address space: a compact version.
     */
    public boolean hasCompactVariant() {
        return this.compactExpansionTemplate != null;
    }

    /**
     * Get ArrayList of Strings that represent list of templates for
     * basic instructions generated by this extended instruction.
     *
     * @return ArrayList of Strings.
     */
    public ExpansionTemplate getStandardExpansionTemplate() {
        return this.standardExpansionTemplate;
    }

    public void setStandardExpansionTemplate(ExpansionTemplate template) {
        this.standardExpansionTemplate = template;
    }

    /**
     * Get ArrayList of Strings that represent list of templates for
     * basic instructions generated by the "compact" or 16-bit version
     * of this extended instruction.
     *
     * @return ArrayList of Strings.  Returns null if the instruction does not
     *     have a compact alternative.
     */
    public ExpansionTemplate getCompactExpansionTemplate() {
        return this.compactExpansionTemplate;
    }

    public void setCompactExpansionTemplate(ExpansionTemplate template) {
        this.compactExpansionTemplate = template;
    }

    public ExpansionTemplate getExpansionTemplate() {
        if (this.hasCompactVariant() && Memory.getInstance().isUsingCompactAddressSpace()) {
            return this.getCompactExpansionTemplate();
        }
        else {
            return this.getStandardExpansionTemplate();
        }
    }

    /**
     * Get length in bytes that this extended instruction requires in its
     * binary form. The answer depends on how many basic instructions it
     * expands to.  This may vary, if expansion includes a nop, depending on
     * whether or not delayed branches are enabled. Each requires 4 bytes.
     *
     * @return int length in bytes of corresponding binary instruction(s).
     */
    @Override
    public int getSizeBytes() {
        return this.getExpansionTemplate().getSizeBytes();
    }
}