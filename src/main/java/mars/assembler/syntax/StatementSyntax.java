package mars.assembler.syntax;

import mars.assembler.*;
import mars.assembler.token.SourceLine;
import mars.assembler.token.Token;
import mars.mips.instructions.BasicInstruction;
import mars.mips.instructions.ExtendedInstruction;
import mars.mips.instructions.Instruction;

import java.util.ArrayList;
import java.util.List;

public class StatementSyntax implements Syntax {
    private final SourceLine sourceLine;
    private final Token firstToken;
    private final Instruction instruction;
    private final List<SyntaxOperand> operands;

    public StatementSyntax(SourceLine sourceLine, Token firstToken, Instruction instruction, List<SyntaxOperand> operands) {
        this.sourceLine = sourceLine;
        this.firstToken = firstToken;
        this.instruction = instruction;
        this.operands = operands;
    }

    @Override
    public SourceLine getSourceLine() {
        return this.sourceLine;
    }

    @Override
    public Token getFirstToken() {
        return this.firstToken;
    }

    public Instruction getInstruction() {
        return this.instruction;
    }

    public List<SyntaxOperand> getOperands() {
        return this.operands;
    }

    @Override
    public void process(Assembler assembler) {
        assembler.addParsedStatement(this);
    }

    public Statement resolve(Assembler assembler, int address) {
        List<Operand> resolvedOperands = new ArrayList<>(this.operands.size());

        for (int index = 0; index < this.operands.size(); index++) {
            Operand operand = this.operands.get(index).resolve(assembler);
            // Perform type conversion if needed
            operand = operand.convertToType(this.instruction.getOperandTypes().get(index), this, assembler, address);
            resolvedOperands.add(operand);
        }

        // TODO: Not a fan of this downcasting approach
        if (this.instruction instanceof BasicInstruction basicInstruction) {
            return new BasicStatement(this, basicInstruction, resolvedOperands);
        }
        else if (this.instruction instanceof ExtendedInstruction extendedInstruction) {
            if (!AssemblerFlag.EXTENDED_MODE.isEnabled()) {
                assembler.logError(this.sourceLine.getLocation(), "Extended instruction '" + this.instruction.getExampleSyntax() + "' is not allowed outside of extended mode");
            }
            return extendedInstruction.getExpansionTemplate().resolve(resolvedOperands, this, assembler, address);
        }
        else {
            // This should never happen
            throw new IllegalStateException("invalid instruction category");
        }
    }
}
