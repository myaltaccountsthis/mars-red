package mars.assembler;

import mars.Application;
import mars.mips.hardware.Coprocessor1;
import mars.mips.hardware.Register;
import mars.mips.hardware.RegisterFile;
import mars.util.Binary;

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
 * Enumeration to identify the types of tokens found in MIPS programs.
 *
 * @author Pete Sanderson
 * @version August 2003
 */
public enum TokenType {
    ERROR,
    COMMENT,
    DIRECTIVE,
    OPERATOR,
    DELIMITER,
    /*
     * note: REGISTER_NAME is token of form $zero whereas REGISTER_NUMBER is token
     * of form $0.  The former is part of extended assembler, and latter is part
     * of basic assembler.
     */
    REGISTER_NAME,
    REGISTER_NUMBER,
    FP_REGISTER_NAME,
    IDENTIFIER,
    LEFT_PAREN,
    RIGHT_PAREN,
    INTEGER_5,
    INTEGER_16,
    INTEGER_16U,
    INTEGER_32,
    REAL_NUMBER,
    CHARACTER,
    STRING,
    PLUS,
    MINUS,
    COLON,
    MACRO_PARAMETER;

    /**
     * Classifies the given token into one of the MIPS types.
     *
     * @param value String containing candidate language element, extracted from MIPS program.
     * @return Returns the corresponding TokenTypes object if the parameter matches a
     *     defined MIPS token type, else returns <code>null</code>.
     */
    public static TokenType detectLiteralType(String value) {
        // If it starts with single quote ('), it is a mal-formed character literal
        // because a well-formed character literal was converted to string-ified
        // integer before getting here...
        if (value.charAt(0) == '\'') {
            return TokenType.ERROR;
        }

        // See if it is a comment
        if (value.charAt(0) == '#') {
            return TokenType.COMMENT;
        }

        // See if it is one of the simple tokens
        if (value.length() == 1) {
            switch (value.charAt(0)) {
                case '(' -> {
                    return TokenType.LEFT_PAREN;
                }
                case ')' -> {
                    return TokenType.RIGHT_PAREN;
                }
                case ':' -> {
                    return TokenType.COLON;
                }
                case '+' -> {
                    return TokenType.PLUS;
                }
                case '-' -> {
                    return TokenType.MINUS;
                }
            }
        }

        // See if it is a macro parameter
        if (Macro.tokenIsMacroParameter(value, false)) {
            return TokenType.MACRO_PARAMETER;
        }

        // See if it is a register
        Register reg = RegisterFile.getRegister(value);
        if (reg != null) {
            if (reg.getName().equals(value)) {
                return TokenType.REGISTER_NAME;
            }
            else {
                return TokenType.REGISTER_NUMBER;
            }
        }

        // See if it is a floating point register
        reg = Coprocessor1.getRegister(value);
        if (reg != null) {
            return TokenType.FP_REGISTER_NAME;
        }

        // See if it is an immediate (constant) integer value
        // Classify based on # bits needed to represent in binary
        // This is needed because most immediate operands limited to 16 bits
        // others limited to 5 bits unsigned (shift amounts) others 32 bits.
        try {
            int i = Binary.decodeInteger(value);   // KENV 1/6/05

            /* **************************************************************************
             *  MODIFICATION AND COMMENT, DPS 3-July-2008
             *
             * The modifications of January 2005 documented below are being rescinded.
             * All hexadecimal immediate values are considered 32 bits in length and
             * their classification as INTEGER_5, INTEGER_16, INTEGER_16U (new)
             * or INTEGER_32 depends on their 32 bit value.  So 0xFFFF will be
             * equivalent to 0x0000FFFF instead of 0xFFFFFFFF.  This change, along with
             * the introduction of INTEGER_16U (adopted from Greg Gibeling of Berkeley),
             * required extensive changes to instruction templates especially for
             * pseudo-instructions.
             *
             * This modification also appears in buildBasicStatementFromBasicInstruction()
             * in mars.ProgramStatement.
             *
             *  ///// Begin modification 1/4/05 KENV   ///////////////////////////////////////////
             *  // We have decided to interpret non-signed (no + or -) 16-bit hexadecimal immediate
             *  // operands as signed values in the range -32768 to 32767. So 0xffff will represent
             *  // -1, not 65535 (bit 15 as sign bit), 0x8000 will represent -32768 not 32768.
             *  // NOTE: 32-bit hexadecimal immediate operands whose values fall into this range
             *  // will be likewise affected, but they are used only in pseudo-instructions.  The
             *  // code in ExtendedInstruction.java to split this number into upper 16 bits for "lui"
             *  // and lower 16 bits for "ori" works with the original source code token, so it is
             *  // not affected by this tweak.  32-bit immediates in data segment directives
             *  // are also processed elsewhere so are not affected either.
             *  ////////////////////////////////////////////////////////////////////////////////
             *
             *     if ( Binary.isHex(value) &&
             *         (i >= 32768) &&
             *         (i <= 65535) )  // Range 0x8000 ... 0xffff
             *     {
             *          // Subtract the 0xffff bias, because strings in the
             *          // range "0x8000" ... "0xffff" are used to represent
             *          // 16-bit negative numbers, not positive numbers.
             *        i = i - 65536;
             *     }
             *    // ------------- END    KENV 1/4/05   MODIFICATIONS --------------
             *
             **************************  END DPS 3-July-2008 COMMENTS *******************************/
            // shift operands must be in range 0-31
            if (i >= 0 && i <= 31) {
                return TokenType.INTEGER_5;
            }
            if (i >= DataTypes.MIN_UHALF_VALUE && i <= DataTypes.MAX_UHALF_VALUE) {
                return TokenType.INTEGER_16U;
            }
            if (i >= DataTypes.MIN_HALF_VALUE && i <= DataTypes.MAX_HALF_VALUE) {
                return TokenType.INTEGER_16;
            }
            return TokenType.INTEGER_32;  // default when no other type is applicable
        }
        catch (NumberFormatException exception) {
            // Ignore, this simply means the token is not an integer
        }

        // See if it is a real (fixed or floating point) number.  Note that parseDouble()
        // accepts integer values but if it were an integer literal we wouldn't get this far.
        try {
            Double.parseDouble(value);
            return TokenType.REAL_NUMBER;
        }
        catch (NumberFormatException exception) {
            // NO ACTION -- exception suppressed
        }

        // See if it is an instruction operator
        if (Application.instructionSet.matchOperator(value) != null) {
            return TokenType.OPERATOR;
        }

        // See if it is a directive
        if (value.charAt(0) == '.' && Directive.matchDirective(value) != null) {
            return TokenType.DIRECTIVE;
        }

        // See if it is a quoted string
        if (value.charAt(0) == '"') {
            return TokenType.STRING;
        }

        // Test for identifier goes last because I have defined tokens for various
        // MIPS constructs (such as operators and directives) that also could fit
        // the lexical specifications of an identifier, and those need to be
        // recognized first.
        if (isValidIdentifier(value)) {
            return TokenType.IDENTIFIER;
        }

        // Matches no MIPS language token.
        return TokenType.ERROR;
    }

    /**
     * Determine whether this token type is an integer (i.e. {@link #INTEGER_5}, {@link #INTEGER_16},
     * {@link #INTEGER_16U}, or {@link #INTEGER_32}).
     *
     * @return <code>true</code> if this is an integer type, or <code>false</code> otherwise.
     */
    public boolean isInteger() {
        return this == TokenType.INTEGER_5
            || this == TokenType.INTEGER_16
            || this == TokenType.INTEGER_16U
            || this == TokenType.INTEGER_32;
    }

    /**
     * Determine whether this token type is a floating-point number (i.e. {@link #REAL_NUMBER}).
     *
     * @return <code>true</code> if this is a floating-point type, or <code>false</code> otherwise.
     */
    public static boolean isFloatingPoint(TokenType type) {
        return type == TokenType.REAL_NUMBER;
    }

    /**
     * COD2, A-51:  "Identifiers are a sequence of alphanumeric characters,
     * underbars (_), and dots (.) that do not begin with a number."
     * Ideally this would be in a separate Identifier class but I did not see an immediate
     * need beyond this method (refactoring effort would probably identify other uses
     * related to symbol table).
     * <p>
     * DPS 14-Jul-2008: added '$' as valid symbol.  Permits labels to include $.
     * MIPS-target GCC will produce labels that start with $.
     */
    public static boolean isValidIdentifier(String value) {
        if (!(Character.isLetter(value.charAt(0)) || value.charAt(0) == '_' || value.charAt(0) == '.' || value.charAt(0) == '$')) {
            return false;
        }
        for (int index = 1; index < value.length(); index++) {
            if (!(Character.isLetterOrDigit(value.charAt(index)) || value.charAt(index) == '_' || value.charAt(index) == '.' || value.charAt(index) == '$')) {
                return false;
            }
        }
        return true;
    }
}
