package mars.assembler;

import mars.*;
import mars.assembler.syntax.StatementSyntax;
import mars.assembler.syntax.Syntax;
import mars.assembler.syntax.SyntaxParser;
import mars.assembler.token.*;
import mars.mips.hardware.*;
import mars.util.Binary;

import java.util.*;

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
    private final SymbolTable globalSymbolTable;
    private final Map<String, SymbolTable> localSymbolTables;
    private SymbolTable localSymbolTable;
    private final Map<String, Token> localSymbolsToGlobalize;

    private List<ForwardReferencePatch> currentFilePatches;
    private List<ForwardReferencePatch> remainingPatches;

    public final Segment textSegment;
    public final Segment dataSegment;
    public final Segment kernelTextSegment;
    public final Segment kernelDataSegment;
    public final Segment externSegment;
    private Segment segment;

    // Default is to align data from directives on appropriate boundary (word, half, byte)
    // This can be turned off for remainder of current data segment with ".align 0"
    private boolean isAutoAlignmentEnabled;

    private ErrorList errors;
    private SortedMap<Integer, StatementSyntax> parsedStatements;
    private SortedMap<Integer, Statement> resolvedStatements;
    private SortedMap<Integer, BasicStatement> assembledStatements;

    public Assembler() {
        this.globalSymbolTable = new SymbolTable("(global)");
        this.localSymbolTables = new HashMap<>();
        this.localSymbolTable = null;
        this.localSymbolsToGlobalize = new HashMap<>();

        this.currentFilePatches = new ArrayList<>();
        this.remainingPatches = new ArrayList<>();

        this.dataSegment = new Segment(true, MemoryConfigurations.DATA_LOW, MemoryConfigurations.DATA_HIGH);
        this.textSegment = new Segment(false, MemoryConfigurations.TEXT_LOW, MemoryConfigurations.TEXT_HIGH);
        this.kernelDataSegment = new Segment(true, MemoryConfigurations.KERNEL_DATA_LOW, MemoryConfigurations.KERNEL_DATA_HIGH);
        this.kernelTextSegment = new Segment(false, MemoryConfigurations.KERNEL_TEXT_LOW, MemoryConfigurations.KERNEL_TEXT_HIGH);
        this.externSegment = new Segment(true, MemoryConfigurations.EXTERN_LOW, MemoryConfigurations.EXTERN_HIGH);
        this.segment = null;

        this.isAutoAlignmentEnabled = true;

        this.errors = null;
        this.parsedStatements = null;
        this.resolvedStatements = null;
        this.assembledStatements = null;
    }

    /**
     * Get list of assembler errors and warnings
     *
     * @return ErrorList of any assembler errors and warnings.
     */
    public ErrorList getErrorList() {
        return this.errors;
    }

    public Symbol getSymbol(String identifier) {
        Symbol symbol = null;
        if (this.localSymbolTable != null) {
            symbol = this.localSymbolTable.getSymbol(identifier);
        }
        if (symbol == null) {
            symbol = this.globalSymbolTable.getSymbol(identifier);
        }
        return symbol;
    }

    public SymbolTable getLocalSymbolTable() {
        return this.localSymbolTable;
    }

    public SymbolTable getGlobalSymbolTable() {
        return this.globalSymbolTable;
    }

    public Segment getSegment() {
        return this.segment;
    }

    public void setSegment(Segment segment) {
        this.segment = segment;
        this.setAutoAlignmentEnabled(true);
    }

    public boolean isAutoAlignmentEnabled() {
        return this.isAutoAlignmentEnabled;
    }

    public void setAutoAlignmentEnabled(boolean enabled) {
        this.isAutoAlignmentEnabled = enabled;
    }

    public void resetExternalState() {
        Memory.getInstance().reset();
        RegisterFile.reset();
        Coprocessor0.reset();
        Coprocessor1.reset();
    }

    public void assembleFilenames(List<String> sourceFilenames) throws ProcessingException {
        this.errors = new ErrorList();

        List<SourceFile> sourceFiles = new ArrayList<>(sourceFilenames.size());
        for (String filename : sourceFilenames) {
            sourceFiles.add(Tokenizer.tokenizeFile(filename, this.errors));
        }

        this.assembleFiles(sourceFiles);
    }

    /**
     * Parse and generate machine code for the given MIPS program. All source
     * files must have already been tokenized.
     *
     * @param sourceFiles
     */
    public void assembleFiles(List<SourceFile> sourceFiles) throws ProcessingException {
        this.errors = new ErrorList();
        this.parsedStatements = new TreeMap<>();
        this.resolvedStatements = new TreeMap<>();
        this.assembledStatements = new TreeMap<>();

        this.resetExternalState();

        if (sourceFiles.isEmpty()) {
            return;
        }

        // PROCESS THE FIRST ASSEMBLY PASS FOR ALL SOURCE FILES BEFORE PROCEEDING
        // TO SECOND PASS. THIS ASSURES ALL SYMBOL TABLES ARE CORRECTLY BUILT.
        // THERE IS ONE GLOBAL SYMBOL TABLE (for identifiers declared .globl) PLUS
        // ONE LOCAL SYMBOL TABLE FOR EACH SOURCE FILE.
        for (SourceFile sourceFile : sourceFiles) {
            if (this.errors.hasExceededErrorLimit()) {
                break;
            }

            // Clear out (initialize) symbol table related structures.
            this.localSymbolTable = new SymbolTable(sourceFile.getFilename());
            this.localSymbolTables.put(sourceFile.getFilename(), this.localSymbolTable);

            // FIRST PASS OF ASSEMBLER VERIFIES SYNTAX, GENERATES SYMBOL TABLE, INITIALIZES DATA SEGMENT

            SyntaxParser parser = new SyntaxParser(sourceFile.getLines().iterator(), this.errors);
            Syntax syntax;
            while ((syntax = parser.parseNextSyntax()) != null && !this.errors.hasExceededErrorLimit()) {
                syntax.process(this);
            }

            // Move symbols specified by .globl directives from the local symbol table to the global symbol table
            this.transferGlobals();

            // Attempt to resolve forward label references that were discovered in operand fields
            // of data segment directives in current file. Those that are not resolved after this
            // call are either references to global labels not seen yet, or are undefined.
            // Cannot determine which until all files are parsed, so copy unresolved entries
            // into accumulated list and clear out this one for reuse with the next source file.
            this.currentFilePatches.removeIf(patch -> (
                patch.resolve(this.localSymbolTable) || patch.resolve(this.globalSymbolTable)
            ));
            this.remainingPatches.addAll(this.currentFilePatches);
            this.currentFilePatches.clear();
        }

        // Have processed all source files. Attempt to resolve any remaining forward label
        // references from global symbol table. Those that remain unresolved are undefined
        // and require error message.
        this.remainingPatches.removeIf(patch -> patch.resolve(this.globalSymbolTable));
        for (ForwardReferencePatch patch : this.remainingPatches) {
            this.errors.add(new ErrorMessage(
                patch.identifier.getFilename(),
                patch.identifier.getLineIndex(),
                patch.identifier.getColumnIndex(),
                "Undefined symbol '" + patch.identifier + "'"
            ));
        }

        // If the first pass produced any errors, throw them instead of progressing to the second pass
        if (this.errors.errorsOccurred()) {
            throw new ProcessingException(this.errors);
        }

        // SECOND PASS OF ASSEMBLER GENERATES BASIC ASSEMBLER THEN MACHINE CODE.
        // Generates basic assembler statements...
        for (var entry : this.parsedStatements.entrySet()) {
            if (this.errors.hasExceededErrorLimit()) {
                break;
            }

            int address = entry.getKey();
            StatementSyntax syntax = entry.getValue();
            this.resolvedStatements.put(address, syntax.resolve(this, address));
        }

        if (this.errors.errorsOccurred()) {
            throw new ProcessingException(this.errors);
        }

        ///////////// THIRD MAJOR STEP IS PRODUCE MACHINE CODE FROM ASSEMBLY //////////
        // Generates machine code statements from the list of basic assembler statements
        // and writes the statement to memory.
        for (ProgramStatement statement : this.assembledStatements) {
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
                errors.add(new ErrorMessage(t.getFilename(), t.getLineIndex(), t.getColumnIndex(), "Invalid address for text segment: " + e.getAddress()));
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
        this.assembledStatements.sort(new ProgramStatementComparator());
        catchDuplicateAddresses();
        if (errors.errorsOccurred() || (errors.warningsOccurred() && warningsAreErrors)) {
            throw new ProcessingException(errors);
        }
    }

    public void addParsedStatement(StatementSyntax statement) {
        StatementSyntax replacedStatement = this.parsedStatements.put(this.segment.getAddress(), statement);
        if (replacedStatement != null) {
            this.errors.add(new ErrorMessage(
                statement.getSourceLine().getFilename(),
                statement.getSourceLine().getLineIndex(),
                statement.getFirstToken().getColumnIndex(),
                "Attempted to place the statement at address "
                    + Binary.intToHexString(this.segment.getAddress())
                    + ", but a statement was already placed there from "
                    + replacedStatement.getSourceLine().getFilename()
                    + ", line "
                    + (replacedStatement.getSourceLine().getLineIndex() + 1)
            ));
        }

        // Increment the current address by the statement size
        this.segment.incrementAddress(statement.getInstruction().getSizeBytes());
    }

    public void placeStatement(BasicStatement statement, int address) {
        BasicStatement replacedStatement = this.assembledStatements.put(address, statement);
        if (replacedStatement != null) {
            this.errors.add(new ErrorMessage(
                statement.getSyntax().getFirstToken().getFilename(),
                statement.getSyntax().getFirstToken().getLineIndex(),
                statement.getSyntax().getFirstToken().getColumnIndex(),
                "Attempted to place the statement at address "
                + Binary.intToHexString(address)
                + ", but a statement was already placed there from "
                + replacedStatement.getSyntax().getFirstToken().getFilename()
                + ", line "
                + (replacedStatement.getSyntax().getFirstToken().getLineIndex() + 1)
            ));
        }

        try {
            Memory.getInstance().storeStatement(address, statement, true);
        }
        catch (AddressErrorException exception) {
            this.errors.add(new ErrorMessage(
                statement.getSyntax().getFirstToken().getFilename(),
                statement.getSyntax().getFirstToken().getLineIndex(),
                statement.getSyntax().getFirstToken().getColumnIndex(),
                "Cannot place statement at " + Binary.intToHexString(address) + ": " + exception.getMessage()
            ));
        }
    }

    public void alignSegmentAddress(int alignment) {
        // No action needed for byte alignment
        if (alignment > 1) {
            int currentAddress = this.segment.getAddress();
            int alignedAddress = Memory.alignToNext(currentAddress, alignment);

            this.segment.setAddress(alignedAddress);
            this.localSymbolTable.realignSymbols(currentAddress, alignedAddress);
        }
    }

    public void createForwardReferencePatch(int address, int length, Token identifier) {
        this.currentFilePatches.add(new ForwardReferencePatch(address, length, identifier));
    }

    public void defineExtern(Token identifier, int sizeBytes) {
        // Only define a new extern if the identifier is not already in the global symbol table
        if (this.globalSymbolTable.getSymbol(identifier.getLiteral()) == null) {
            this.globalSymbolTable.defineSymbol(
                identifier.getLiteral(),
                this.externSegment.address,
                true
            );
            this.externSegment.incrementAddress(sizeBytes);
        }
    }

    public void makeSymbolGlobal(Token identifier) {
        // Check to ensure the identifier does not conflict with any existing global symbols
        Token previousIdentifier = this.localSymbolsToGlobalize.get(identifier.getLiteral());
        if (previousIdentifier != null) {
            this.errors.add(new ErrorMessage(
                false,
                identifier.getFilename(),
                identifier.getLineIndex(),
                identifier.getColumnIndex(),
                "Symbol '" + identifier + "' was previously declared as global on line " + previousIdentifier.getLineIndex(),
                ""
            ));
        }
        else {
            this.localSymbolsToGlobalize.put(identifier.getLiteral(), identifier);
        }
    }

    /**
     * Process the list of .globl labels, if any, declared and defined in this file.
     * We'll just move their symbol table entries from local symbol table to global
     * symbol table at the end of the first assembly pass.
     */
    private void transferGlobals() {
        for (Token identifier : this.localSymbolsToGlobalize.values()) {
            Symbol symbol = this.localSymbolTable.getSymbol(identifier.getLiteral());
            if (symbol == null) {
                this.errors.add(new ErrorMessage(
                    false,
                    identifier.getFilename(),
                    identifier.getLineIndex(),
                    identifier.getColumnIndex(),
                    "Symbol '" + identifier.getLiteral() + "' has not been defined in this file",
                    ""
                ));
            }
            else if (this.globalSymbolTable.getSymbol(identifier.getLiteral()) != null) {
                this.errors.add(new ErrorMessage(
                    false,
                    identifier.getFilename(),
                    identifier.getLineIndex(),
                    identifier.getColumnIndex(),
                    "Symbol '" + identifier + "' was declared as global in another file",
                    ""
                ));
            }
            else {
                // Transfer the symbol from local to global
                this.localSymbolTable.removeSymbol(symbol.getIdentifier());
                this.globalSymbolTable.defineSymbol(symbol);
            }
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
    public static class Segment {
        private final boolean isData;
        private final int firstAddress;
        private final int lastAddress;
        private int address;

        private Segment(boolean isData, int lowKey, int highKey) {
            this.isData = isData;
            this.firstAddress = Memory.getInstance().getAddress(lowKey);
            this.lastAddress = Memory.getInstance().getAddress(highKey);
            this.resetAddress();
        }

        public boolean isData() {
            return this.isData;
        }

        public int getFirstAddress() {
            return this.firstAddress;
        }

        public int getLastAddress() {
            return this.lastAddress;
        }

        public int getAddress() {
            return this.address;
        }

        public void setAddress(int address) {
            this.address = address;
        }

        public void incrementAddress(int numBytes) {
            this.address += numBytes;
        }

        public void resetAddress() {
            this.address = this.firstAddress;
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
     * - the label's identifier. Normally need only the name but error message needs more.
     */
    private record ForwardReferencePatch(int address, int length, Token identifier) {
        /**
         * Will traverse the list of forward references, attempting to resolve them.
         * For each entry it will first search the provided local symbol table and
         * failing that, the global one. If passed the global symbol table, it will
         * perform a second, redundant, search. If search is successful, the patch
         * is applied and the forward reference removed. If search is not successful,
         * the forward reference remains (it is either undefined or a global label
         * defined in a file not yet parsed).
         */
        public boolean resolve(SymbolTable symbolTable) {
            // Find the symbol, if it exists
            Symbol symbol = symbolTable.getSymbol(this.identifier.getLiteral());
            if (symbol == null) {
                return false;
            }

            this.patch(symbol.getAddress());
            return true;
        }

        private void patch(int value) {
            // Perform the patch operation
            try {
                Memory.getInstance().store(this.address, value, this.length, true);
            }
            catch (AddressErrorException ignored) {
                // Should not happen
            }
        }
    }
}
