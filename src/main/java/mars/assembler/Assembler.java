package mars.assembler;

import mars.*;
import mars.assembler.token.*;
import mars.mips.hardware.AddressErrorException;
import mars.mips.hardware.Memory;
import mars.mips.hardware.MemoryConfigurations;
import mars.mips.instructions.BasicInstruction;
import mars.mips.instructions.ExtendedInstruction;
import mars.mips.instructions.Instruction;
import mars.util.Binary;
import mars.venus.NumberDisplayBaseChooser;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/*
Copyright (c) 2003-2012,  Pete Sanderson and Kenneth Vollmar

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
 * An Assembler is capable of assembling a MIPS program. It has only one public
 * method, <code>assemble()</code>, which implements a two-pass assembler. It
 * translates MIPS source code into binary machine code.
 *
 * @author Pete Sanderson
 * @version August 2003
 */
public class Assembler {
    private List<ProgramStatement> machineList;
    private ErrorList errors;
    private boolean inDataSegment; // status maintained by parser
    private boolean inMacroSegment; // status maintained by parser, true if in macro definition segment
    private int externAddress;
    private boolean autoAlign;
    private Directive dataDirective;
    private Program fileCurrentlyBeingAssembled;
    private TokenList globalDeclarationList;
    private UserKernelAddressSpace textAddress;
    private UserKernelAddressSpace dataAddress;
    private DataSegmentForwardReferences currentFileDataSegmentForwardReferences;
    private DataSegmentForwardReferences accumulatedDataSegmentForwardReferences;

    /**
     * Get list of assembler errors and warnings
     *
     * @return ErrorList of any assembler errors and warnings.
     */
    public ErrorList getErrorList() {
        return errors;
    }

    /**
     * Parse and generate machine code for the given MIPS program. It must have
     * already been tokenized. Warnings are not considered errors.
     *
     * @param tokenizedProgramFile                        A MIPSprogram object representing the program source.
     * @param extendedAssemblerEnabled A boolean value that if true permits use of extended (pseudo)
     *                                 instructions in the source code. If false, these are flagged
     *                                 as errors.
     * @return An ArrayList representing the assembled program. Each member of
     *     the list is a ProgramStatement object containing the source,
     *     intermediate, and machine binary representations of a program
     *     statement.
     * @see ProgramStatement
     */
    public List<ProgramStatement> assemble(Program tokenizedProgramFile, boolean extendedAssemblerEnabled) throws ProcessingException {
        return assemble(tokenizedProgramFile, extendedAssemblerEnabled, false);
    }

    /**
     * Parse and generate machine code for the given MIPS program. It must have
     * already been tokenized.
     *
     * @param tokenizedProgramFile                        A MIPSprogram object representing the program source.
     * @param extendedAssemblerEnabled A boolean value that if true permits use of extended (pseudo)
     *                                 instructions in the source code. If false, these are flagged
     *                                 as errors.
     * @param warningsAreErrors        A boolean value - true means assembler warnings will be
     *                                 considered errors and terminate the assemble; false means the
     *                                 assembler will produce warning message but otherwise ignore
     *                                 warnings.
     * @return An ArrayList representing the assembled program. Each member of
     *     the list is a ProgramStatement object containing the source,
     *     intermediate, and machine binary representations of a program
     *     statement.
     * @see ProgramStatement
     */
    public List<ProgramStatement> assemble(Program tokenizedProgramFile, boolean extendedAssemblerEnabled, boolean warningsAreErrors) throws ProcessingException {
        return this.assemble(List.of(tokenizedProgramFile), extendedAssemblerEnabled, warningsAreErrors);
    }

    /**
     * Parse and generate machine code for the given MIPS program. All source
     * files must have already been tokenized. Warnings will not be considered
     * errors.
     *
     * @param tokenizedProgramFiles    An ArrayList of MIPSprogram objects, each produced from a
     *                                 different source code file, representing the program source.
     * @param extendedAssemblerEnabled A boolean value that if true permits use of extended (pseudo)
     *                                 instructions in the source code. If false, these are flagged
     *                                 as errors.
     * @return An ArrayList representing the assembled program. Each member of
     *     the list is a ProgramStatement object containing the source,
     *     intermediate, and machine binary representations of a program
     *     statement. Returns null if incoming array list is null or empty.
     * @see ProgramStatement
     */
    public List<ProgramStatement> assemble(List<Program> tokenizedProgramFiles, boolean extendedAssemblerEnabled) throws ProcessingException {
        return assemble(tokenizedProgramFiles, extendedAssemblerEnabled, false);
    }

    /**
     * Parse and generate machine code for the given MIPS program. All source
     * files must have already been tokenized.
     *
     * @param tokenizedProgramFiles    An ArrayList of MIPSprogram objects, each produced from a
     *                                 different source code file, representing the program source.
     * @param extendedAssemblerEnabled A boolean value that if true permits use of extended (pseudo)
     *                                 instructions in the source code. If false, these are flagged
     *                                 as errors.
     * @param warningsAreErrors        A boolean value - true means assembler warnings will be
     *                                 considered errors and terminate the assemble; false means the
     *                                 assembler will produce warning message but otherwise ignore
     *                                 warnings.
     * @return An ArrayList representing the assembled program. Each member of
     *     the list is a ProgramStatement object containing the source,
     *     intermediate, and machine binary representations of a program
     *     statement. Returns null if incoming array list is null or empty.
     * @see ProgramStatement
     */
    public List<ProgramStatement> assemble(List<Program> tokenizedProgramFiles, boolean extendedAssemblerEnabled, boolean warningsAreErrors) throws ProcessingException {
        if (tokenizedProgramFiles == null || tokenizedProgramFiles.isEmpty()) {
            return null;
        }
        textAddress = new UserKernelAddressSpace(
            Memory.getInstance().getAddress(MemoryConfigurations.TEXT_LOW),
            Memory.getInstance().getAddress(MemoryConfigurations.KERNEL_TEXT_LOW)
        );
        dataAddress = new UserKernelAddressSpace(
            Memory.getInstance().getAddress(MemoryConfigurations.STATIC_LOW),
            Memory.getInstance().getAddress(MemoryConfigurations.KERNEL_DATA_LOW)
        );
        externAddress = Memory.getInstance().getAddress(MemoryConfigurations.EXTERN_LOW);
        currentFileDataSegmentForwardReferences = new DataSegmentForwardReferences();
        accumulatedDataSegmentForwardReferences = new DataSegmentForwardReferences();
        Application.globalSymbolTable.clear();
        Memory.getInstance().reset();
        this.machineList = new ArrayList<>();
        this.errors = new ErrorList();
        if (Application.debug) {
            System.out.println("Assembler first pass begins:");
        }
        // PROCESS THE FIRST ASSEMBLY PASS FOR ALL SOURCE FILES BEFORE PROCEEDING
        // TO SECOND PASS. THIS ASSURES ALL SYMBOL TABLES ARE CORRECTLY BUILT.
        // THERE IS ONE GLOBAL SYMBOL TABLE (for identifiers declared .globl) PLUS
        // ONE LOCAL SYMBOL TABLE FOR EACH SOURCE FILE.
        for (Program tokenizedProgramFile : tokenizedProgramFiles) {
            if (errors.hasExceededErrorLimit()) {
                break;
            }
            this.fileCurrentlyBeingAssembled = tokenizedProgramFile;
            // List of labels declared ".globl". new list for each file assembled
            this.globalDeclarationList = new TokenList();
            // Parser begins by default in text segment until directed otherwise.
            this.inDataSegment = false;
            // Macro segment will be started by .macro directive
            this.inMacroSegment = false;
            // Default is to align data from directives on appropriate boundary (word, half, byte)
            // This can be turned off for remainder of current data segment with ".align 0"
            this.autoAlign = true;
            // Default data directive is .word for 4 byte data items
            this.dataDirective = Directive.WORD;
            // Clear out (initialize) symbol table related structures.
            fileCurrentlyBeingAssembled.getLocalSymbolTable().clear();
            currentFileDataSegmentForwardReferences.clear();
            List<SourceLine> sourceLines = fileCurrentlyBeingAssembled.getSourceLines();
            List<TokenList> tokenLists = fileCurrentlyBeingAssembled.getTokenLists();
            List<ProgramStatement> parsedStatements = new ArrayList<>();
            // each file keeps its own macro definitions
            fileCurrentlyBeingAssembled.createMacroPool();
            // FIRST PASS OF ASSEMBLER VERIFIES SYNTAX, GENERATES SYMBOL TABLE,
            // INITIALIZES DATA SEGMENT
            for (int i = 0; i < tokenLists.size(); i++) {
                TokenList tokens = tokenLists.get(i);
                SourceLine sourceLine = sourceLines.get(i);
                if (errors.hasExceededErrorLimit()) {
                    break;
                }
                for (int z = 0; z < tokens.size(); z++) {
                    Token token = tokens.get(z);
                    // record this token's original source program and line #. Differs from final, if .include used
                    token.setOriginalToken(sourceLine.getProgram(), sourceLine.getLineNumber());
                }
                List<ProgramStatement> statements = this.parseLine(tokens, sourceLine.getContent(), sourceLine.getLineNumber(), extendedAssemblerEnabled);
                if (statements != null) {
                    parsedStatements.addAll(statements);
                }
            }
            fileCurrentlyBeingAssembled.setParsedStatements(parsedStatements);
            if (inMacroSegment) {
                errors.add(new ErrorMessage(fileCurrentlyBeingAssembled, fileCurrentlyBeingAssembled.getLocalMacroPool().getCurrent().getFromLine(), 0, "Macro started but not ended (no .end_macro directive)"));
            }
            // move ".globl" symbols from local symtab to global
            this.transferGlobals();
            // Attempt to resolve forward label references that were discovered in operand fields
            // of data segment directives in current file. Those that are not resolved after this
            // call are either references to global labels not seen yet, or are undefined.
            // Cannot determine which until all files are parsed, so copy unresolved entries
            // into accumulated list and clear out this one for re-use with the next source file.
            currentFileDataSegmentForwardReferences.resolve(fileCurrentlyBeingAssembled.getLocalSymbolTable());
            accumulatedDataSegmentForwardReferences.add(currentFileDataSegmentForwardReferences);
            currentFileDataSegmentForwardReferences.clear();
        } // end of first-pass loop for each MIPSprogram

        // Have processed all source files. Attempt to resolve any remaining forward label
        // references from global symbol table. Those that remain unresolved are undefined
        // and require error message.
        accumulatedDataSegmentForwardReferences.resolve(Application.globalSymbolTable);
        accumulatedDataSegmentForwardReferences.generateErrorMessages(errors);

        // Throw collection of errors accumulated through the first pass.
        if (errors.errorsOccurred()) {
            throw new ProcessingException(errors);
        }

        // SECOND PASS OF ASSEMBLER GENERATES BASIC ASSEMBLER THEN MACHINE CODE.
        // Generates basic assembler statements...
        if (Application.debug) {
            System.out.println("Assembler second pass begins");
        }
        for (Program tokenizedProgramFile : tokenizedProgramFiles) {
            if (errors.hasExceededErrorLimit()) {
                break;
            }
            this.fileCurrentlyBeingAssembled = tokenizedProgramFile;
            for (ProgramStatement statement : fileCurrentlyBeingAssembled.getParsedStatements()) {
                statement.buildBasicStatementFromBasicInstruction(errors);
                if (errors.errorsOccurred()) {
                    throw new ProcessingException(errors);
                }
                if (statement.getInstruction() instanceof BasicInstruction) {
                    this.machineList.add(statement);
                }
                else {
                    // It is a pseudo-instruction:
                    // 1. Fetch its basic instruction template list
                    // 2. For each template in the list,
                    // 2a. substitute operands from source statement
                    // 2b. tokenize the statement generated by 2a.
                    // 2d. call parseLine() to generate basic instruction
                    // 2e. add returned programStatement to the list
                    // The templates, and the instructions generated by filling
                    // in the templates, are specified
                    // in basic format (e.g. mnemonic register reference $zero
                    // already translated to $0).
                    // So the values substituted into the templates need to be
                    // in this format. Since those
                    // values come from the original source statement, they need
                    // to be translated before
                    // substituting. The next method call will perform this
                    // translation on the original
                    // source statement. Despite the fact that the original
                    // statement is a pseudo
                    // instruction, this method performs the necessary
                    // translation correctly.
                    ExtendedInstruction extendedInstruction = (ExtendedInstruction) statement.getInstruction();
                    String basicAssembly = statement.getBasicAssemblyStatement();
                    int sourceLine = statement.getSourceLine();
                    TokenList tokens = Tokenizer.tokenizeLine(basicAssembly, sourceLine, errors);

                    // If we are using compact memory config and there is a compact expansion, use it
                    ArrayList<String> templates;
                    if (Memory.getInstance().isUsingCompactAddressSpace() && extendedInstruction.hasCompactVariant()) {
                        templates = extendedInstruction.getCompactBasicInstructionTemplateList();
                    }
                    else {
                        templates = extendedInstruction.getBasicInstructionTemplateList();
                    }

                    // Subsequent ProgramStatement constructor needs the correct text segment address.
                    textAddress.set(statement.getAddress());
                    String source = statement.getSource();
                    // Will generate one basic instruction for each template in the list.
                    for (String template : templates) {
                        String instruction = ExtendedInstruction.makeTemplateSubstitutions(this.fileCurrentlyBeingAssembled, template, tokens);
                        // 23 Jan 2008 by DPS. Template substitution may result in no instruction.
                        // If this is the case, skip remainder of loop iteration. This should only
                        // happen if template substitution was for "nop" instruction but delayed branching
                        // is disabled so the "nop" is not generated.
                        if (instruction == null || instruction.isEmpty()) {
                            continue;
                        }

                        // All substitutions have been made so we have generated
                        // a valid basic instruction!
                        if (Application.debug) {
                            System.out.println("PSEUDO generated: " + instruction);
                        }
                        // For generated instruction: tokenize, build program
                        // statement, add to list.
                        TokenList newTokens = Tokenizer.tokenizeLine(instruction, sourceLine, errors);
                        ArrayList<Instruction> instructionMatches = this.matchInstruction(newTokens.get(0));
                        Instruction instructionMatch = OperandType.bestOperandMatch(newTokens, instructionMatches);
                        ProgramStatement newStatement = new ProgramStatement(this.fileCurrentlyBeingAssembled, source, newTokens, newTokens, instructionMatch, textAddress.get(), statement.getSourceLine());
                        source = ""; // Only first generated instruction is linked to original source
                        textAddress.increment(Instruction.BYTES_PER_INSTRUCTION);
                        newStatement.buildBasicStatementFromBasicInstruction(errors);
                        this.machineList.add(newStatement);
                    } // end of FOR loop, repeated for each template in list.
                } // end of ELSE part for extended instruction.
            } // end of assembler second pass.
        }
        if (Application.debug) {
            System.out.println("Code generation begins");
        }
        ///////////// THIRD MAJOR STEP IS PRODUCE MACHINE CODE FROM ASSEMBLY //////////
        // Generates machine code statements from the list of basic assembler statements
        // and writes the statement to memory.
        for (ProgramStatement statement : this.machineList) {
            if (errors.hasExceededErrorLimit()) {
                break;
            }
            statement.buildMachineStatementFromBasicStatement(errors);
            if (Application.debug) {
                System.out.println(statement);
            }
            try {
                Memory.getInstance().storeStatement(statement.getAddress(), statement, false);
            }
            catch (AddressErrorException e) {
                Token t = statement.getOriginalTokenList().get(0);
                errors.add(new ErrorMessage(t.getSourceFilename(), t.getSourceLine(), t.getSourceColumn(), "Invalid address for text segment: " + e.getAddress()));
            }
        }
        // DPS 6 Dec 2006:
        // We will now sort the ArrayList of ProgramStatements by getAddress() value.
        // This is for display purposes, since they have already been stored to Memory.
        // Use of .ktext and .text with address operands has two implications:
        // (1) the addresses may not be ordered at this point. Requires unsigned int
        // sort because kernel addresses are negative. See special Comparator.
        // (2) It is possible for two instructions to be placed at the same address.
        // Such occurrences will be flagged as errors.
        // Yes, I would not have to sort here if I used SortedSet rather than ArrayList
        // but in case of duplicate I like having both statements handy for error message.
        this.machineList.sort(new ProgramStatementComparator());
        catchDuplicateAddresses();
        if (errors.errorsOccurred() || (errors.warningsOccurred() && warningsAreErrors)) {
            throw new ProcessingException(errors);
        }
        return this.machineList;
    }

    /**
     * Check for duplicate text addresses, which can happen inadvertently when using
     * operand on .text directive. Will generate error message for each one that occurs.
     */
    private void catchDuplicateAddresses() {
        for (int address = 0; address < this.machineList.size() - 1; address++) {
            ProgramStatement statement1 = this.machineList.get(address);
            ProgramStatement statement2 = this.machineList.get(address + 1);
            if (statement1.getAddress() == statement2.getAddress()) {
                errors.add(new ErrorMessage(
                    statement2.getSourceMIPSprogram(),
                    statement2.getSourceLine(), 0,
                    "Duplicate text segment address: "
                        + NumberDisplayBaseChooser.formatUnsignedInteger(statement2.getAddress(), (Application.getSettings().displayAddressesInHex.get()) ? 16 : 10)
                        + " already occupied by " + statement1.getSourceFile()
                        + " line " + statement1.getSourceLine()
                        + " (caused by use of " + ((Memory.getInstance().isInTextSegment(statement2.getAddress())) ? ".text" : ".ktext") + " operand)"
                ));
            }
        }
    }

    /**
     * This method parses one line of MIPS source code. It works with the list
     * of tokens, but original source is also provided. It also carries out
     * directives, which includes initializing the data segment. This method is
     * invoked in the assembler first pass.
     *
     * @param tokenList
     * @param source
     * @param sourceLineNumber
     * @param extendedAssemblerEnabled
     * @return List of ProgramStatements because parsing a macro expansion
     *     request will return a list of ProgramStatements expanded
     */
    private List<ProgramStatement> parseLine(TokenList tokenList, String source, int sourceLineNumber, boolean extendedAssemblerEnabled) {
        List<ProgramStatement> parsedStatements = new ArrayList<>();

        TokenList tokens = this.stripComment(tokenList);

        // Labels should not be processed in macro definition segment.
        MacroPool macroPool = fileCurrentlyBeingAssembled.getLocalMacroPool();
        if (inMacroSegment) {
            detectLabels(tokens, macroPool.getCurrent());
        }
        else {
            stripLabels(tokens);
        }
        if (tokens.isEmpty()) {
            return null;
        }
        // Grab first (operator) token...
        Token token = tokens.get(0);
        TokenType tokenType = token.getType();

        // Let's handle the directives here...
        if (tokenType == TokenType.DIRECTIVE) {
            this.executeDirective(tokens);
            return null;
        }

        // don't parse if in macro segment
        if (inMacroSegment) {
            return null;
        }

        // SPIM-style macro calling:
        TokenList parenFreeTokens = tokens;
        if (tokens.size() > 2 && tokens.get(1).getType() == TokenType.LEFT_PAREN && tokens.get(tokens.size() - 1).getType() == TokenType.RIGHT_PAREN) {
            parenFreeTokens = (TokenList) tokens.clone();
            parenFreeTokens.remove(tokens.size() - 1);
            parenFreeTokens.remove(1);
        }
        Macro macro = macroPool.getMatchingMacro(parenFreeTokens, sourceLineNumber);

        // expand macro if this line is a macro expansion call
        if (macro != null) {
            tokens = parenFreeTokens;
            // get unique id for this expansion
            int counter = macroPool.getNextCounter();
            if (macroPool.pushOnCallStack(token)) {
                errors.add(new ErrorMessage(fileCurrentlyBeingAssembled, tokens.get(0).getSourceLine(), 0, "Detected a macro expansion loop (recursive reference). "));
            }
            else {
                for (int i = macro.getFromLine() + 1; i < macro.getToLine(); i++) {

                    String substituted = macro.getSubstitutedLine(i, tokens, counter, errors);
                    TokenList tokenList2 = Tokenizer.tokenizeLine(substituted, i, errors);

                    // If token list getProcessedLine() is not empty, then .eqv was performed and it contains the modified source.
                    // Put it into the line to be parsed, so it will be displayed properly in text segment display. DPS 23 Jan 2013
                    if (!tokenList2.getProcessedLine().isEmpty()) {
                        substituted = tokenList2.getProcessedLine();
                    }

                    // Recursively parse lines of expanded macro
                    List<ProgramStatement> statements = parseLine(tokenList2, "<" + (i - macro.getFromLine() + macro.getOriginalFromLine()) + "> " + substituted.strip(), sourceLineNumber, extendedAssemblerEnabled);
                    if (statements != null) {
                        parsedStatements.addAll(statements);
                    }
                }
                macroPool.popFromCallStack();
            }
            return parsedStatements;
        }

        // DPS 14-July-2008
        // Yet Another Hack: detect unrecognized directive. MARS recognizes the same directives
        // as SPIM but other MIPS assemblers recognize additional directives. Compilers such
        // as MIPS-directed GCC generate assembly code containing these directives. We'd like
        // the opportunity to ignore them and continue. Tokenizer would categorize an unrecognized
        // directive as an TokenTypes.IDENTIFIER because it would not be matched as a directive and
        // MIPS labels can start with '.' NOTE: this can also be handled by including the
        // ignored directive in the Directives.java list. There is already a mechanism in place
        // for generating a warning there. But I cannot anticipate the names of all directives
        // so this will catch anything, including a misspelling of a valid directive (which is
        // a nice thing to do).
        if (tokenType == TokenType.IDENTIFIER && token.getLiteral().charAt(0) == '.') {
            errors.add(new ErrorMessage(true, token.getSourceFilename(), token.getSourceLine(), token.getSourceColumn(), "MARS does not recognize the " + token.getLiteral() + " directive.  Ignored.", ""));
            return null;
        }

        // The directives with lists (.byte, .double, .float, .half, .word, .ascii, .asciiz)
        // should be able to extend the list over several lines. Since this method assembles
        // only one source line, state information must be stored from one invocation to
        // the next, to sense the context of this continuation line. That state information
        // is contained in this.dataDirective (the current data directive).
        //
        if (this.inDataSegment && // 30-Dec-09 DPS Added data segment guard...
            (tokenType == TokenType.PLUS ||// because invalid instructions were being caught...
                tokenType == TokenType.MINUS ||// here and reported as a directive in text segment!
                tokenType == TokenType.STRING
             || tokenType == TokenType.IDENTIFIER || tokenType.isInteger() || tokenType.isFloatingPoint())) {
            this.executeDirectiveContinuation(tokens);
            return null;
        }

        // If we are in the text segment, the variable "token" must now refer to
        // an OPERATOR
        // token. If not, it is either a syntax error or the specified operator
        // is not
        // yet implemented.
        if (!this.inDataSegment) {
            ArrayList<Instruction> instructionMatches = this.matchInstruction(token);
            if (instructionMatches == null) {
                return parsedStatements;
            }
            // OK, we've got an operator match, let's check the operands.
            Instruction instruction = OperandType.bestOperandMatch(tokens, instructionMatches);
            // Here's the place to flag use of extended (pseudo) instructions
            // when setting disabled.
            if (instruction instanceof ExtendedInstruction && !extendedAssemblerEnabled) {
                errors.add(new ErrorMessage(token.getSourceFilename(), token.getSourceLine(), token.getSourceColumn(), "Extended (pseudo) instruction or format not permitted.  See Settings."));
            }
            if (OperandType.tokenOperandMatch(tokens, instruction, errors)) {
                ProgramStatement statement = new ProgramStatement(this.fileCurrentlyBeingAssembled, source, tokenList, tokens, instruction, textAddress.get(), sourceLineNumber);
                // instruction length is 4 for all basic instruction, varies for extended instruction
                // Modified to permit use of compact expansion if address fits
                // in 15 bits. DPS 4-Aug-2009
                int instLength = instruction.getSizeBytes();
                if (instruction instanceof ExtendedInstruction extendedInstruction
                        && Memory.getInstance().isUsingCompactAddressSpace()
                        && extendedInstruction.hasCompactVariant()) {
                    instLength = ((ExtendedInstruction) instruction).getCompactSizeBytes();
                }
                textAddress.increment(instLength);
                parsedStatements.add(statement);
                return parsedStatements;
            }
        }
        return null;
    }

    private void detectLabels(TokenList tokens, Macro current) {
        if (tokenListBeginsWithLabel(tokens)) {
            current.addLabel(tokens.get(0).getLiteral());
        }
    }

    /**
     * Determine whether or not a compact (16-bit) translation from
     * pseudo-instruction to basic instruction can be applied. If
     * the argument is a basic instruction, obviously not. If an
     * extended instruction, we have to be operating under a 16-bit
     * memory model and the instruction has to have defined an
     * alternate compact translation.
     */
    private boolean compactTranslationCanBeApplied(ProgramStatement statement) {
        return statement.getInstruction() instanceof ExtendedInstruction
            && Memory.getInstance().isUsingCompactAddressSpace()
            && ((ExtendedInstruction) statement.getInstruction()).hasCompactVariant();
    }

    /**
     * Pre-process the token list for a statement by stripping off any comment.
     * NOTE: the ArrayList parameter is not modified; a new one is cloned and
     * returned.
     */
    private TokenList stripComment(TokenList tokenList) {
        if (tokenList.isEmpty()) {
            return tokenList;
        }
        TokenList tokens = (TokenList) tokenList.clone();
        // If there is a comment, strip it off.
        int last = tokens.size() - 1;
        if (tokens.get(last).getType() == TokenType.COMMENT) {
            tokens.remove(last);
        }
        return tokens;
    }

    /**
     * Pre-process the token list for a statement by stripping off any label, if
     * either are present. Any label definition will be recorded in the symbol
     * table. NOTE: the ArrayList parameter will be modified.
     */
    private void stripLabels(TokenList tokens) {
        // If there is a label, handle it here and strip it off.
        boolean thereWasLabel = this.parseAndRecordLabel(tokens);
        if (thereWasLabel) {
            tokens.remove(0); // Remove the IDENTIFIER.
            tokens.remove(0); // Remove the COLON, shifted to 0 by previous remove
        }
    }

    /**
     * Parse and record label, if there is one. Note the identifier and its colon are
     * two separate tokens, since they may be separated by spaces in source code.
     */
    private boolean parseAndRecordLabel(TokenList tokens) {
        if (tokens.size() < 2) {
            return false;
        }
        else {
            Token token = tokens.get(0);
            if (tokenListBeginsWithLabel(tokens)) {
                if (token.getType() == TokenType.OPERATOR) {
                    // an instruction name was used as label (e.g. lw:), so change its token type
                    token.setType(TokenType.IDENTIFIER);
                }
                fileCurrentlyBeingAssembled.getLocalSymbolTable().addSymbol(token, (this.inDataSegment) ? dataAddress.get() : textAddress.get(), this.inDataSegment, this.errors);
                return true;
            }
            else {
                return false;
            }
        }
    }

    private boolean tokenListBeginsWithLabel(TokenList tokens) {
        // 2-July-2010. DPS. Remove prohibition of operator names as labels
        if (tokens.size() < 2) {
            return false;
        }
        return (tokens.get(0).getType() == TokenType.IDENTIFIER || tokens.get(0).getType() == TokenType.OPERATOR) && tokens.get(1).getType() == TokenType.COLON;
    }

    /**
     * This source code line is a directive, not a MIPS instruction. Let's carry it out.
     */
    private void executeDirective(TokenList tokens) {
        Token token = tokens.get(0);
        Directive direct = Directive.fromName(token.getLiteral());
        if (Application.debug) {
            System.out.println("line " + token.getSourceLine() + " is directive " + direct);
        }
        if (direct == null) {
            errors.add(new ErrorMessage(token.getSourceFilename(), token.getSourceLine(), token.getSourceColumn(), "\"" + token.getLiteral() + "\" directive is invalid or not implemented in MARS"));
        }
        else if (direct == Directive.EQV) { /* EQV added by DPS 11 July 2012 */
            // Do nothing.  This was vetted and processed during tokenizing.
        }
        else if (direct == Directive.MACRO) {
            if (tokens.size() < 2) {
                errors.add(new ErrorMessage(token.getSourceFilename(), token.getSourceLine(), token.getSourceColumn(), "\"" + token.getLiteral() + "\" directive requires at least one argument."));
                return;
            }
            if (tokens.get(1).getType() != TokenType.IDENTIFIER) {
                errors.add(new ErrorMessage(token.getSourceFilename(), token.getSourceLine(), tokens.get(1).getSourceColumn(), "Invalid Macro name \"" + tokens.get(1).getLiteral() + "\""));
                return;
            }
            if (inMacroSegment) {
                errors.add(new ErrorMessage(token.getSourceFilename(), token.getSourceLine(), token.getSourceColumn(), "Nested macros are not allowed"));
                return;
            }
            inMacroSegment = true;
            MacroPool pool = fileCurrentlyBeingAssembled.getLocalMacroPool();
            pool.beginMacro(tokens.get(1));
            for (int i = 2; i < tokens.size(); i++) {
                Token arg = tokens.get(i);
                if (arg.getType() == TokenType.RIGHT_PAREN || arg.getType() == TokenType.LEFT_PAREN) {
                    continue;
                }
                if (!Macro.tokenIsMacroParameter(arg.getLiteral(), true)) {
                    errors.add(new ErrorMessage(arg.getSourceFilename(), arg.getSourceLine(), arg.getSourceColumn(), "Invalid macro argument '" + arg.getLiteral() + "'"));
                    return;
                }
                pool.getCurrent().addArg(arg.getLiteral());
            }
        }
        else if (direct == Directive.END_MACRO) {
            if (tokens.size() > 1) {
                errors.add(new ErrorMessage(token.getSourceFilename(), token.getSourceLine(), token.getSourceColumn(), "invalid text after .END_MACRO"));
                return;
            }
            if (!inMacroSegment) {
                errors.add(new ErrorMessage(token.getSourceFilename(), token.getSourceLine(), token.getSourceColumn(), ".END_MACRO without .MACRO"));
                return;
            }
            inMacroSegment = false;
            fileCurrentlyBeingAssembled.getLocalMacroPool().commitMacro(token);
        }
        else if (inMacroSegment) {
            // should not parse lines even directives in macro segment
        }
        else if (direct == Directive.DATA || direct == Directive.KDATA) {
            this.inDataSegment = true;
            this.autoAlign = true;
            this.dataAddress.setAddressSpace((direct == Directive.DATA) ? UserKernelAddressSpace.USER : UserKernelAddressSpace.KERNEL);
            if (tokens.size() > 1 && tokens.get(1).getType().isInteger()) {
                this.dataAddress.set(Binary.decodeInteger(tokens.get(1).getLiteral())); // KENV 1/6/05
            }
        }
        else if (direct == Directive.TEXT || direct == Directive.KTEXT) {
            this.inDataSegment = false;
            this.textAddress.setAddressSpace((direct == Directive.TEXT) ? UserKernelAddressSpace.USER : UserKernelAddressSpace.KERNEL);
            if (tokens.size() > 1 && tokens.get(1).getType().isInteger()) {
                this.textAddress.set(Binary.decodeInteger(tokens.get(1).getLiteral())); // KENV 1/6/05
            }
        }
        else if (direct == Directive.WORD || direct == Directive.HALF || direct == Directive.BYTE || direct == Directive.FLOAT || direct == Directive.DOUBLE) {
            this.dataDirective = direct;
            if (passesDataSegmentCheck(token) && tokens.size() > 1) {
                // DPS 11/20/06, added text segment prohibition
                storeNumeric(tokens, direct, errors);
            }
        }
        else if (direct == Directive.ASCII || direct == Directive.ASCIIZ) {
            this.dataDirective = direct;
            if (passesDataSegmentCheck(token)) {
                storeStrings(tokens, direct, errors);
            }
        }
        else if (direct == Directive.ALIGN) {
            if (passesDataSegmentCheck(token)) {
                if (tokens.size() != 2) {
                    errors.add(new ErrorMessage(token.getSourceFilename(), token.getSourceLine(), token.getSourceColumn(), "\"" + token.getLiteral() + "\" requires one operand"));
                    return;
                }
                if (!tokens.get(1).getType().isInteger() || Binary.decodeInteger(tokens.get(1).getLiteral()) < 0) {
                    errors.add(new ErrorMessage(token.getSourceFilename(), token.getSourceLine(), token.getSourceColumn(), "\"" + token.getLiteral() + "\" requires a non-negative integer"));
                    return;
                }
                int value = Binary.decodeInteger(tokens.get(1).getLiteral()); // KENV 1/6/05
                if (value == 0) {
                    this.autoAlign = false;
                }
                else {
                    this.dataAddress.set(this.alignToBoundary(this.dataAddress.get(), (int) Math.pow(2, value)));
                }
            }
        }
        else if (direct == Directive.SPACE) {
            if (passesDataSegmentCheck(token)) {
                if (tokens.size() != 2) {
                    errors.add(new ErrorMessage(token.getSourceFilename(), token.getSourceLine(), token.getSourceColumn(), "\"" + token.getLiteral() + "\" requires one operand"));
                    return;
                }
                if (!tokens.get(1).getType().isInteger() || Binary.decodeInteger(tokens.get(1).getLiteral()) < 0) {
                    errors.add(new ErrorMessage(token.getSourceFilename(), token.getSourceLine(), token.getSourceColumn(), "\"" + token.getLiteral() + "\" requires a non-negative integer"));
                    return;
                }
                int value = Binary.decodeInteger(tokens.get(1).getLiteral()); // KENV 1/6/05
                this.dataAddress.increment(value);
            }
        }
        else if (direct == Directive.EXTERN) {
            if (tokens.size() != 3) {
                errors.add(new ErrorMessage(token.getSourceFilename(), token.getSourceLine(), token.getSourceColumn(), "\"" + token.getLiteral() + "\" directive requires two operands (label and size)."));
                return;
            }
            if (!tokens.get(2).getType().isInteger() || Binary.decodeInteger(tokens.get(2).getLiteral()) < 0) {
                errors.add(new ErrorMessage(token.getSourceFilename(), token.getSourceLine(), token.getSourceColumn(), "\"" + token.getLiteral() + "\" requires a non-negative integer size"));
                return;
            }
            int size = Binary.decodeInteger(tokens.get(2).getLiteral());
            // If label already in global symtab, do nothing. If not, add it right now.
            if (Application.globalSymbolTable.getAddress(tokens.get(1).getLiteral()) == SymbolTable.NOT_FOUND) {
                Application.globalSymbolTable.addSymbol(tokens.get(1), this.externAddress, true, errors);
                this.externAddress += size;
            }
        }
        else if (direct == Directive.SET) {
            errors.add(new ErrorMessage(true, token.getSourceFilename(), token.getSourceLine(), token.getSourceColumn(), "MARS currently ignores the .set directive.", ""));
        }
        else if (direct == Directive.GLOBL) {
            if (tokens.size() < 2) {
                errors.add(new ErrorMessage(token.getSourceFilename(), token.getSourceLine(), token.getSourceColumn(), "\"" + token.getLiteral() + "\" directive requires at least one argument."));
                return;
            }
            // SPIM limits .globl list to one label, why not extend it to a list?
            for (int i = 1; i < tokens.size(); i++) {
                // Add it to a list of labels to be processed at the end of the
                // pass. At that point, transfer matching symbol definitions from
                // local symbol table to global symbol table.
                Token label = tokens.get(i);
                if (label.getType() != TokenType.IDENTIFIER) {
                    errors.add(new ErrorMessage(token.getSourceFilename(), token.getSourceLine(), token.getSourceColumn(), "\"" + token.getLiteral() + "\" directive argument must be label."));
                    return;
                }
                globalDeclarationList.add(label);
            }
        }
        else {
            errors.add(new ErrorMessage(token.getSourceFilename(), token.getSourceLine(), token.getSourceColumn(), "\"" + token.getLiteral() + "\" directive recognized but not yet implemented."));
        }
    }

    /**
     * Process the list of .globl labels, if any, declared and defined in this file.
     * We'll just move their symbol table entries from local symbol table to global
     * symbol table at the end of the first assembly pass.
     */
    private void transferGlobals() {
        for (int i = 0; i < globalDeclarationList.size(); i++) {
            Token label = globalDeclarationList.get(i);
            Symbol symtabEntry = fileCurrentlyBeingAssembled.getLocalSymbolTable().getSymbol(label.getLiteral());
            if (symtabEntry == null) {
                errors.add(new ErrorMessage(fileCurrentlyBeingAssembled, label.getSourceLine(), label.getSourceColumn(), "\"" + label.getLiteral() + "\" declared global label but not defined."));
            }
            else {
                if (Application.globalSymbolTable.getAddress(label.getLiteral()) != SymbolTable.NOT_FOUND) {
                    errors.add(new ErrorMessage(fileCurrentlyBeingAssembled, label.getSourceLine(), label.getSourceColumn(), "\"" + label.getLiteral() + "\" already defined as global in a different file."));
                }
                else {
                    fileCurrentlyBeingAssembled.getLocalSymbolTable().removeSymbol(label.getLiteral());
                    Application.globalSymbolTable.addSymbol(label, symtabEntry.getAddress(), symtabEntry.isData(), errors);
                }
            }
        }
    }

    /**
     * This source code line, if syntactically correct, is a continuation of a
     * directive list begun on on previous line.
     */
    private void executeDirectiveContinuation(TokenList tokens) {
        Directive direct = this.dataDirective;
        if (direct == Directive.WORD || direct == Directive.HALF || direct == Directive.BYTE || direct == Directive.FLOAT || direct == Directive.DOUBLE) {
            if (!tokens.isEmpty()) {
                storeNumeric(tokens, direct, errors);
            }
        }
        else if (direct == Directive.ASCII || direct == Directive.ASCIIZ) {
            if (passesDataSegmentCheck(tokens.get(0))) {
                storeStrings(tokens, direct, errors);
            }
        }
    }

    /**
     * Given token, find the corresponding Instruction object. If token was not
     * recognized as OPERATOR, there is a problem.
     */
    private ArrayList<Instruction> matchInstruction(Token token) {
        if (token.getType() != TokenType.OPERATOR) {
            if (token.getSourceFilename().getLocalMacroPool().matchesAnyMacroName(token.getLiteral())) {
                this.errors.add(new ErrorMessage(token.getSourceFilename(), token.getSourceLine(), token.getSourceColumn(), "forward reference or invalid parameters for macro \"" + token.getLiteral() + "\""));
            }
            else {
                this.errors.add(new ErrorMessage(token.getSourceFilename(), token.getSourceLine(), token.getSourceColumn(), "\"" + token.getLiteral() + "\" is not a recognized operator"));
            }
            return null;
        }
        ArrayList<Instruction> instructionMatches = Application.instructionSet.matchMnemonic(token.getLiteral());
        if (instructionMatches == null) { // This should NEVER happen...
            this.errors.add(new ErrorMessage(token.getSourceFilename(), token.getSourceLine(), token.getSourceColumn(), "Internal Assembler error: \"" + token.getLiteral() + "\" tokenized OPERATOR then not recognized"));
        }
        return instructionMatches;
    }

    /**
     * Processes the .word/.half/.byte/.float/.double directive.
     * Can also handle "directive continuations", e.g. second or subsequent line
     * of a multiline list, which does not contain the directive token. Just pass the
     * current directive as argument.
     */
    private void storeNumeric(TokenList tokens, Directive directive, ErrorList errors) {
        Token token = tokens.get(0);
        // A double-check; should have already been caught...removed ".word" exemption 11/20/06
        if (!passesDataSegmentCheck(token)) {
            return;
        }
        // Correctly handles case where this is a "directive continuation" line.
        int tokenStart = 0;
        if (token.getType() == TokenType.DIRECTIVE) {
            tokenStart = 1;
        }

        // Set byte length in memory of each number (e.g. WORD is 4, BYTE is 1, etc)
        int lengthInBytes = DataTypes.getLengthInBytes(directive);

        // Handle the "value : n" format, which replicates the value "n" times.
        if (tokens.size() == 4 && tokens.get(2).getType() == TokenType.COLON) {
            Token valueToken = tokens.get(1);
            Token repetitionsToken = tokens.get(3);
            // DPS 15-jul-08, allow ":" for repetition for all numeric
            // directives (originally just .word)
            // Conditions for correctly-formed replication:
            // (integer directive AND integer value OR floating directive AND
            // (integer value OR floating value))
            // AND integer repetition value
            if (!(Directive.isIntegerDirective(directive) && valueToken.getType().isInteger() || Directive.isFloatingDirective(directive) && (valueToken.getType().isInteger() || valueToken.getType().isFloatingPoint())) || !repetitionsToken.getType().isInteger()) {
                errors.add(new ErrorMessage(fileCurrentlyBeingAssembled, valueToken.getSourceLine(), valueToken.getSourceColumn(), "malformed expression"));
                return;
            }
            int repetitions = Binary.decodeInteger(repetitionsToken.getLiteral()); // KENV 1/6/05
            if (repetitions <= 0) {
                errors.add(new ErrorMessage(fileCurrentlyBeingAssembled, repetitionsToken.getSourceLine(), repetitionsToken.getSourceColumn(), "repetition factor must be positive"));
                return;
            }
            if (this.inDataSegment) {
                if (this.autoAlign) {
                    this.dataAddress.set(this.alignToBoundary(this.dataAddress.get(), lengthInBytes));
                }
                for (int i = 0; i < repetitions; i++) {
                    if (Directive.isIntegerDirective(directive)) {
                        storeInteger(valueToken, directive, errors);
                    }
                    else {
                        storeRealNumber(valueToken, directive, errors);
                    }
                }
            } // WHAT ABOUT .KDATA SEGMENT?
            return;
        }

        // if not in ".word w : n" format, must just be list of one or more values.
        for (int i = tokenStart; i < tokens.size(); i++) {
            token = tokens.get(i);
            if (Directive.isIntegerDirective(directive)) {
                storeInteger(token, directive, errors);
            }
            if (Directive.isFloatingDirective(directive)) {
                storeRealNumber(token, directive, errors);
            }
        }
    }

    /**
     * Store integer value given integer (word, half, byte) directive.
     * Called by storeNumeric()
     * NOTE: The token itself may be a label, in which case the correct action is
     * to store the address of that label (into however many bytes specified).
     */
    private void storeInteger(Token token, Directive directive, ErrorList errors) {
        int lengthInBytes = DataTypes.getLengthInBytes(directive);
        if (token.getType().isInteger()) {
            int value = Binary.decodeInteger(token.getLiteral());
            // DPS 4-Jan-2013.  Overriding 6-Jan-2005 KENV changes.
            // If value is out of range for the directive, will simply truncate
            // the leading bits (includes sign bits). This is what SPIM does.
            // But will issue a warning (not error) which SPIM does not do.
            if (DataTypes.outOfRange(directive, value)) {
                errors.add(new ErrorMessage(true, token.getSourceFilename(), token.getSourceLine(), token.getSourceColumn(), "\"" + token.getLiteral() + "\" is out-of-range for a signed value and possibly truncated", ""));
            }
            if (directive == Directive.BYTE) {
                value &= 0xFF;
            }
            else if (directive == Directive.HALF) {
                value &= 0xFFFF;
            }

            if (this.inDataSegment) {
                writeToDataSegment(value, lengthInBytes, token, errors);
            }
            /*
             * NOTE of 11/20/06. "try" below will always throw exception b/c you
             * cannot use Memory.set() with text segment addresses and the
             * "not valid address" produced here is misleading. Added data
             * segment check prior to this point, so this "else" will never be
             * executed. I'm leaving it in just in case MARS in the future adds
             * capability of writing to the text segment (e.g. ability to
             * de-assemble a binary value into its corresponding MIPS
             * instruction)
             */
            else {
                try {
                    Memory.getInstance().store(this.textAddress.get(), value, lengthInBytes, true);
                }
                catch (AddressErrorException e) {
                    errors.add(new ErrorMessage(token.getSourceFilename(), token.getSourceLine(), token.getSourceColumn(), "\"" + this.textAddress.get() + "\" is not a valid text segment address"));
                    return;
                }
                this.textAddress.increment(lengthInBytes);
            }
        } // end of "if integer token type"
        else if (token.getType() == TokenType.IDENTIFIER) {
            if (this.inDataSegment) {
                int value = fileCurrentlyBeingAssembled.getLocalSymbolTable().getAddressLocalOrGlobal(token.getLiteral());
                if (value == SymbolTable.NOT_FOUND) {
                    // Record value 0 for now, then set up backpatch entry
                    int dataAddress = writeToDataSegment(0, lengthInBytes, token, errors);
                    currentFileDataSegmentForwardReferences.add(dataAddress, lengthInBytes, token);
                }
                else { // label already defined, so write its address
                    writeToDataSegment(value, lengthInBytes, token, errors);
                }
            } // Data segment check done previously, so this "else" will not be.
            // See 11/20/06 note above.
            else {
                errors.add(new ErrorMessage(token.getSourceFilename(), token.getSourceLine(), token.getSourceColumn(), "\"" + token.getLiteral() + "\" label as directive operand not permitted in text segment"));
            }
        } // end of "if label"
        else {
            errors.add(new ErrorMessage(token.getSourceFilename(), token.getSourceLine(), token.getSourceColumn(), "\"" + token.getLiteral() + "\" is not a valid integer constant or label"));
        }
    }

    /**
     * Store real (fixed or floating point) value given floating (float, double) directive.
     * Called by storeNumeric()
     */
    private void storeRealNumber(Token token, Directive directive, ErrorList errors) {
        int lengthInBytes = DataTypes.getLengthInBytes(directive);
        double value;

        if (token.getType().isInteger() || token.getType().isFloatingPoint()) {
            try {
                value = Double.parseDouble(token.getLiteral());
            }
            catch (NumberFormatException nfe) {
                errors.add(new ErrorMessage(token.getSourceFilename(), token.getSourceLine(), token.getSourceColumn(), "\"" + token.getLiteral() + "\" is not a valid floating point constant"));
                return;
            }
            if (DataTypes.outOfRange(directive, value)) {
                errors.add(new ErrorMessage(token.getSourceFilename(), token.getSourceLine(), token.getSourceColumn(), "\"" + token.getLiteral() + "\" is an out-of-range value"));
                return;
            }
        }
        else {
            errors.add(new ErrorMessage(token.getSourceFilename(), token.getSourceLine(), token.getSourceColumn(), "\"" + token.getLiteral() + "\" is not a valid floating point constant"));
            return;
        }

        // Value has been validated; let's store it.

        if (directive == Directive.FLOAT) {
            writeToDataSegment(Float.floatToIntBits((float) value), lengthInBytes, token, errors);
        }
        if (directive == Directive.DOUBLE) {
            writeDoubleToDataSegment(value, token, errors);
        }
    }

    /**
     * Use directive argument to distinguish between ASCII and ASCIIZ. The
     * latter stores a terminating null byte. Can handle a list of one or more
     * strings on a single line.
     */
    private void storeStrings(TokenList tokens, Directive direct, ErrorList errors) {
        Token token;
        // Correctly handles case where this is a "directive continuation" line.
        int tokenStart = 0;
        if (tokens.get(0).getType() == TokenType.DIRECTIVE) {
            tokenStart = 1;
        }
        for (int i = tokenStart; i < tokens.size(); i++) {
            token = tokens.get(i);
            if (token.getType() != TokenType.STRING) {
                errors.add(new ErrorMessage(token.getSourceFilename(), token.getSourceLine(), token.getSourceColumn(), "\"" + token.getLiteral() + "\" is not a valid character string"));
            }
            else {
                String quote = token.getLiteral();
                for (int j = 1; j < quote.length() - 1; j++) {
                    char ch = quote.charAt(j);
                    if (ch == '\\') {
                        ch = quote.charAt(++j);
                        // Not implemented:
                        // \ N = octal character (n is number)
                        // \ x N = hex character (n is number)
                        // \ u N = unicode character (n is number)
                        // There are of course no spaces in these escape
                        // codes...
                        ch = switch (ch) {
                            case 'n' -> '\n';
                            case 't' -> '\t';
                            case 'r' -> '\r';
                            case '\\' -> '\\';
                            case '\'' -> '\'';
                            case '"' -> '"';
                            case 'b' -> '\b';
                            case 'f' -> '\f';
                            case '0' -> '\0';
                            default -> ch;
                        };
                    }
                    try {
                        Memory.getInstance().store(this.dataAddress.get(), ch, DataTypes.CHAR_SIZE, true);
                    }
                    catch (AddressErrorException exception) {
                        errors.add(new ErrorMessage(token.getSourceFilename(), token.getSourceLine(), token.getSourceColumn(), "\"" + this.dataAddress.get() + "\" is not a valid data segment address"));
                    }
                    this.dataAddress.increment(DataTypes.CHAR_SIZE);
                }
                if (direct == Directive.ASCIIZ) {
                    try {
                        Memory.getInstance().store(this.dataAddress.get(), 0, DataTypes.CHAR_SIZE, true);
                    }
                    catch (AddressErrorException exception) {
                        errors.add(new ErrorMessage(token.getSourceFilename(), token.getSourceLine(), token.getSourceColumn(), "\"" + this.dataAddress.get() + "\" is not a valid data segment address"));
                    }
                    this.dataAddress.increment(DataTypes.CHAR_SIZE);
                }
            }
        }
    }

    /**
     * Simply check to see if we are in data segment. Generate error if not.
     */
    private boolean passesDataSegmentCheck(Token token) {
        if (!this.inDataSegment) {
            errors.add(new ErrorMessage(token.getSourceFilename(), token.getSourceLine(), token.getSourceColumn(), "\"" + token.getLiteral() + "\" directive cannot appear in text segment"));
            return false;
        }
        else {
            return true;
        }
    }

    /**
     * Writes the given int value into current data segment address. Works for
     * all the integer types plus float (caller is responsible for doing floatToIntBits).
     * Returns address at which the value was stored.
     */
    private int writeToDataSegment(int value, int lengthInBytes, Token token, ErrorList errors) {
        if (this.autoAlign) {
            this.dataAddress.set(this.alignToBoundary(this.dataAddress.get(), lengthInBytes));
        }
        try {
            Memory.getInstance().store(this.dataAddress.get(), value, lengthInBytes, true);
        }
        catch (AddressErrorException exception) {
            errors.add(new ErrorMessage(token.getSourceFilename(), token.getSourceLine(), token.getSourceColumn(), "\"" + this.dataAddress.get() + "\" is not a valid data segment address"));
            return this.dataAddress.get();
        }
        int address = this.dataAddress.get();
        this.dataAddress.increment(lengthInBytes);
        return address;
    }

    /**
     * Writes the given double value into current data segment address. Works
     * only for DOUBLE floating point values.
     */
    private void writeDoubleToDataSegment(double value, Token token, ErrorList errors) {
        int lengthInBytes = DataTypes.DOUBLE_SIZE;
        if (this.autoAlign) {
            this.dataAddress.set(this.alignToBoundary(this.dataAddress.get(), lengthInBytes));
        }
        try {
            Memory.getInstance().storeDoubleword(this.dataAddress.get(), Double.doubleToRawLongBits(value), true);
        }
        catch (AddressErrorException exception) {
            errors.add(new ErrorMessage(token.getSourceFilename(), token.getSourceLine(), token.getSourceColumn(), "\"" + this.dataAddress.get() + "\" is not a valid data segment address"));
            return;
        }
        this.dataAddress.increment(lengthInBytes);
    }

    /**
     * If address is multiple of byte boundary, returns address. Otherwise, returns address
     * which is next higher multiple of the byte boundary. Used for aligning data segment.
     * For instance if args are 6 and 4, returns 8 (next multiple of 4 higher than 6).
     * NOTE: it will fix any symbol table entries for this address too. See else part.
     */
    private int alignToBoundary(int address, int byteBoundary) {
        int remainder = address % byteBoundary;
        if (remainder == 0) {
            return address;
        }
        else {
            int alignedAddress = address + byteBoundary - remainder;
            fileCurrentlyBeingAssembled.getLocalSymbolTable().fixSymbolTableAddress(address, alignedAddress);
            return alignedAddress;
        }
    }

    /**
     * Private class used as Comparator to sort the final ArrayList of
     * ProgramStatements.
     * Sorting is based on unsigned integer value of
     * ProgramStatement.getAddress()
     */
    private static class ProgramStatementComparator implements Comparator<ProgramStatement> {
        /**
         * Will be used to sort the collection. Unsigned int compare, because all kernel 32-bit
         * addresses have 1 in high order bit, which makes the int negative.
         * "Unsigned" compare is needed when signs of the two operands differ.
         */
        public int compare(ProgramStatement statement1, ProgramStatement statement2) {
            int addr1 = statement1.getAddress();
            int addr2 = statement2.getAddress();
            return ((addr1 < 0) != (addr2 < 0)) ? addr2 : addr1 - addr2;
        }

        // Take a hard line.
        public boolean equals(Object obj) {
            return this == obj;
        }
    }

    /**
     * Private class to simultaneously track addresses in both user and kernel address spaces.
     * Instantiate one for data segment and one for text segment.
     */
    private static class UserKernelAddressSpace {
        private static final int USER = 0;
        private static final int KERNEL = 1;

        int[] address;
        int currentAddressSpace;

        /**
         * Initially use user address space, not kernel.
         */
        private UserKernelAddressSpace(int userBase, int kernelBase) {
            address = new int[2];
            address[USER] = userBase;
            address[KERNEL] = kernelBase;
            currentAddressSpace = USER;
        }

        private int get() {
            return address[currentAddressSpace];
        }

        private void set(int value) {
            address[currentAddressSpace] = value;
        }

        private void increment(int increment) {
            address[currentAddressSpace] += increment;
        }

        private void setAddressSpace(int addressSpace) {
            if (addressSpace == USER || addressSpace == KERNEL) {
                currentAddressSpace = addressSpace;
            }
            else {
                throw new IllegalArgumentException();
            }
        }
    }

    /**
     * Handy class to handle forward label references appearing as data
     * segment operands. This is needed because the data segment is comletely
     * processed by the end of the first assembly pass, and its directives may
     * contain labels as operands. When this occurs, the label's associated
     * address becomes the operand value. If it is a forward reference, we will
     * save the necessary information in this object for finding and patching in
     * the correct address at the end of the first pass (for this file or for all
     * files if more than one).
     * If such a parsed label refers to a local or global label not defined yet,
     * pertinent information is added to this object:
     * - memory address that needs the label's address,
     * - number of bytes (addresses are 4 bytes but may be used with any of
     * the integer directives: .word, .half, .byte)
     * - the label's token. Normally need only the name but error message needs more.
     */
    private static class DataSegmentForwardReferences {
        private final ArrayList<DataSegmentForwardReference> forwardReferenceList;

        private DataSegmentForwardReferences() {
            forwardReferenceList = new ArrayList<>();
        }

        private int size() {
            return forwardReferenceList.size();
        }

        /**
         * Add a new forward reference entry. Client must supply the following:
         * - memory address to receive the label's address once resolved
         * - number of address bytes to store (1 for .byte, 2 for .half, 4 for .word)
         * - the label's token. All its information will be needed if error message generated.
         */
        private void add(int patchAddress, int length, Token token) {
            forwardReferenceList.add(new DataSegmentForwardReference(patchAddress, length, token));
        }

        /**
         * Add the entries of another DataSegmentForwardReferences object to this one.
         * Can be used at the end of each source file to dump all unresolved references
         * into a common list to be processed after all source files parsed.
         */
        private void add(DataSegmentForwardReferences another) {
            forwardReferenceList.addAll(another.forwardReferenceList);
        }

        /**
         * Clear out the list. Allows you to re-use it.
         */
        private void clear() {
            forwardReferenceList.clear();
        }

        /**
         * Will traverse the list of forward references, attempting to resolve them.
         * For each entry it will first search the provided local symbol table and
         * failing that, the global one. If passed the global symbol table, it will
         * perform a second, redundant, search. If search is successful, the patch
         * is applied and the forward reference removed. If search is not successful,
         * the forward reference remains (it is either undefined or a global label
         * defined in a file not yet parsed).
         */
        private int resolve(SymbolTable localSymtab) {
            int count = 0;
            int labelAddress;
            DataSegmentForwardReference entry;
            for (int i = 0; i < forwardReferenceList.size(); i++) {
                entry = forwardReferenceList.get(i);
                labelAddress = localSymtab.getAddressLocalOrGlobal(entry.token.getLiteral());
                if (labelAddress != SymbolTable.NOT_FOUND) {
                    // patch address has to be valid b/c we already stored there...
                    try {
                        Memory.getInstance().store(entry.patchAddress, labelAddress, entry.length, true);
                    }
                    catch (AddressErrorException ignored) {
                    }
                    forwardReferenceList.remove(i);
                    i--; // needed because removal shifted the remaining list indices down
                    count++;
                }
            }
            return count;
        }

        /**
         * Call this when you are confident that remaining list entries are to
         * undefined labels.
         */
        private void generateErrorMessages(ErrorList errors) {
            for (DataSegmentForwardReference entry : forwardReferenceList) {
                errors.add(new ErrorMessage(entry.token.getSourceFilename(), entry.token.getSourceLine(), entry.token.getSourceColumn(), "Symbol \"" + entry.token.getLiteral() + "\" not found in symbol table."));
            }
        }

        /**
         * Inner-inner class to hold each entry of the forward reference list.
         */
        private static class DataSegmentForwardReference {
            int patchAddress;
            int length;
            Token token;

            DataSegmentForwardReference(int patchAddress, int length, Token token) {
                this.patchAddress = patchAddress;
                this.length = length;
                this.token = token;
            }
        }
    }
}
