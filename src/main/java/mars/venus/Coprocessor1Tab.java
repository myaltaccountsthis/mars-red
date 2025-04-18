package mars.venus;

import mars.Application;
import mars.mips.hardware.*;
import mars.simulator.Simulator;
import mars.util.Binary;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import java.awt.*;

/*
Copyright (c) 2003-2009,  Pete Sanderson and Kenneth Vollmar

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
 * Sets up a window to display Coprocessor 1 registers in the Registers pane of the UI.
 *
 * @author Pete Sanderson 2005
 */
public class Coprocessor1Tab extends RegistersDisplayTab {
    private static final int NAME_COLUMN = 0;
    private static final int SINGLE_COLUMN = 1;
    private static final int DOUBLE_COLUMN = 2;

    private static final String[] HEADER_TIPS = {
        "Register name based on address (hover to see conventional use)", // Name
        "32-bit single-precision IEEE 754 floating point value", // Single
        "64-bit double-precision IEEE 754 floating point value (uses a pair of 32-bit registers)", // Double
    };
    private static final String[] REGISTER_TIPS = {
        "Value 0 (return value), single- or double-precision", // $f0
        "Value 1 (return value), single-precision only", // $f1
        "Value 2 (return value), single- or double-precision", // $f2
        "Value 3 (return value), single-precision only", // $f3
        "Temporary 0 (value not preserved by callee), single- or double-precision", // $f4
        "Temporary 1 (value not preserved by callee), single-precision only", // $f5
        "Temporary 2 (value not preserved by callee), single- or double-precision", // $f6
        "Temporary 3 (value not preserved by callee), single-precision only", // $f7
        "Temporary 4 (value not preserved by callee), single- or double-precision", // $f8
        "Temporary 5 (value not preserved by callee), single-precision only", // $f9
        "Temporary 6 (value not preserved by callee), single- or double-precision", // $f10
        "Temporary 7 (value not preserved by callee), single-precision only", // $f11
        "Argument 0 (for calls and syscalls), single- or double-precision", // $f12
        "Argument 1 (for calls and syscalls), single-precision only", // $f13
        "Argument 2 (for calls and syscalls), single- or double-precision", // $f14
        "Argument 3 (for calls and syscalls), single-precision only", // $f15
        "Temporary 8 (value not preserved by callee), single- or double-precision", // $f16
        "Temporary 9 (value not preserved by callee), single-precision only", // $f17
        "Temporary 10 (value not preserved by callee), single- or double-precision", // $f18
        "Temporary 11 (value not preserved by callee), single-precision only", // $f19
        "Saved 0 (value preserved by callee), single- or double-precision", // $f20
        "Saved 1 (value preserved by callee), single-precision only", // $f21
        "Saved 2 (value preserved by callee), single- or double-precision", // $f22
        "Saved 3 (value preserved by callee), single-precision only", // $f23
        "Saved 4 (value preserved by callee), single- or double-precision", // $f24
        "Saved 5 (value preserved by callee), single-precision only", // $f25
        "Saved 6 (value preserved by callee), single- or double-precision", // $f26
        "Saved 7 (value preserved by callee), single-precision only", // $f27
        "Saved 8 (value preserved by callee), single- or double-precision", // $f28
        "Saved 9 (value preserved by callee), single-precision only", // $f29
        "Saved 10 (value preserved by callee), single- or double-precision", // $f30
        "Saved 11 (value preserved by callee), single-precision only", // $f31
    };

    private final RegistersTable table;
    private final JCheckBox[] conditionFlagCheckBoxes;

    /**
     * Constructor which sets up a fresh window with a table that contains the register values.
     */
    public Coprocessor1Tab(VenusUI gui) {
        super(gui);
        // Display registers in table contained in scroll pane.
        this.setLayout(new BorderLayout()); // table display will occupy entire width if widened
        this.table = new RegistersTable(new RegisterTableModel(this.setupWindow()), HEADER_TIPS, REGISTER_TIPS);
        this.table.setupColumn(NAME_COLUMN, 20, SwingConstants.LEFT);
        this.table.setupColumn(SINGLE_COLUMN, 70, SwingConstants.LEFT);
        this.table.setupColumn(DOUBLE_COLUMN, 130, SwingConstants.LEFT);
        this.add(new JScrollPane(this.table, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER));
        // Display condition flags in panel below the registers
        JPanel flagsPane = new JPanel(new BorderLayout(0, 6));
        flagsPane.setBorder(new EmptyBorder(6, 6, 6, 6));
        flagsPane.setToolTipText("Flags are used by certain floating point instructions, default flag is 0");
        flagsPane.add(new JLabel("Condition Flags", JLabel.CENTER), BorderLayout.NORTH);
        int flagCount = Coprocessor1.getConditionFlagCount();
        this.conditionFlagCheckBoxes = new JCheckBox[flagCount];
        JPanel checksPane = new JPanel(new GridLayout(2, flagCount / 2));
        for (int flag = 0; flag < flagCount; flag++) {
            this.conditionFlagCheckBoxes[flag] = new JCheckBox(Integer.toString(flag));
            final int finalFlag = flag;
            this.conditionFlagCheckBoxes[flag].addActionListener(event -> {
                JCheckBox checkBox = (JCheckBox) event.getSource();
                if (checkBox.isSelected()) {
                    checkBox.setSelected(true);
                    Coprocessor1.setConditionFlag(finalFlag);
                }
                else {
                    checkBox.setSelected(false);
                    Coprocessor1.clearConditionFlag(finalFlag);
                }
            });
            this.conditionFlagCheckBoxes[flag].setToolTipText("Flag " + flag);
            checksPane.add(this.conditionFlagCheckBoxes[flag]);
        }
        flagsPane.add(checksPane, BorderLayout.CENTER);
        this.add(flagsPane, BorderLayout.SOUTH);
    }

    @Override
    protected RegistersTable getTable() {
        return this.table;
    }

    /**
     * Sets up the data for the window.
     *
     * @return The array object with the data for the window.
     */
    public Object[][] setupWindow() {
        int valueBase = NumberDisplayBaseChooser.getBase(Application.getSettings().displayValuesInHex.get());
        Object[][] tableData = new Object[32][3];

        for (Register register : Coprocessor1.getRegisters()) {
            tableData[register.getNumber()][NAME_COLUMN] = register.getName();
            tableData[register.getNumber()][SINGLE_COLUMN] = NumberDisplayBaseChooser.formatFloatNumber(register.getValue(), valueBase);
            // Even numbered double registers
            if (register.getNumber() % 2 == 0) {
                long longValue = 0;
                try {
                    longValue = Coprocessor1.getPairValue(register.getNumber());
                }
                catch (InvalidRegisterAccessException exception) {
                    // Cannot happen since row must be even
                }
                tableData[register.getNumber()][DOUBLE_COLUMN] = NumberDisplayBaseChooser.formatDoubleNumber(longValue, valueBase);
            }
            else {
                tableData[register.getNumber()][DOUBLE_COLUMN] = "";
            }
        }

        return tableData;
    }

    /**
     * Reset and redisplay registers.
     */
    public void resetDisplay() {
        this.clearHighlighting();
        this.updateRegisters(this.gui.getMainPane().getExecuteTab().getValueDisplayBase());
        this.updateConditionFlagDisplay();
    }

    /**
     * Update register display using specified number base (10 or 16).
     *
     * @param base Desired number base.
     */
    @Override
    public void updateRegisters(int base) {
        for (Register register : Coprocessor1.getRegisters()) {
            this.updateFloatRegisterValue(register.getNumber(), register.getValue(), base);
            if (register.getNumber() % 2 == 0) {
                try {
                    long value = Coprocessor1.getPairValue(register.getNumber());
                    this.updateDoubleRegisterValue(register.getNumber(), value, base);
                }
                catch (InvalidRegisterAccessException exception) {
                    // Should not happen because the register number is always even
                }
            }
        }
        this.updateConditionFlagDisplay();
    }

    private void updateConditionFlagDisplay() {
        for (int flag = 0; flag < this.conditionFlagCheckBoxes.length; flag++) {
            this.conditionFlagCheckBoxes[flag].setSelected(Coprocessor1.getConditionFlag(flag) != 0);
        }
    }

    /**
     * This method handles the updating of the GUI.  Does not affect actual register.
     *
     * @param number The number of the float register whose display to update.
     * @param value  New value, as an int.
     * @param base   The number base for display (e.g. 10, 16)
     */
    public void updateFloatRegisterValue(int number, int value, int base) {
        ((RegisterTableModel) this.table.getModel()).setDisplayAndModelValueAt(NumberDisplayBaseChooser.formatFloatNumber(value, base), number, SINGLE_COLUMN);
    }

    /**
     * This method handles the updating of the GUI.  Does not affect actual register.
     *
     * @param number The number of the double register to update. (Must be even.)
     * @param value  New value, as a long.
     * @param base   The number base for display (e.g. 10, 16)
     */
    public void updateDoubleRegisterValue(int number, long value, int base) {
        ((RegisterTableModel) this.table.getModel()).setDisplayAndModelValueAt(NumberDisplayBaseChooser.formatDoubleNumber(value, base), number, DOUBLE_COLUMN);
    }

    /**
     * Highlight the row corresponding to the given register.
     *
     * @param register Register object corresponding to row to be selected.
     */
    @Override
    public void highlightRegister(Register register) {
        this.table.highlightRow(register.getNumber());
    }

    @Override
    public void startObservingRegisters() {
        for (Register register : Coprocessor1.getRegisters()) {
            register.addListener(this);
        }
    }

    @Override
    public void stopObservingRegisters() {
        for (Register register : Coprocessor1.getRegisters()) {
            register.removeListener(this);
        }
    }

    private class RegisterTableModel extends AbstractTableModel {
        private static final String[] COLUMN_NAMES = {"Name", "Single", "Double"};

        private final Object[][] data;

        public RegisterTableModel(Object[][] data) {
            this.data = data;
        }

        @Override
        public int getColumnCount() {
            return COLUMN_NAMES.length;
        }

        @Override
        public int getRowCount() {
            return this.data.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMN_NAMES[column];
        }

        @Override
        public Object getValueAt(int row, int column) {
            return this.data[row][column];
        }

        /**
         * JTable uses this method to determine the default renderer/editor for each cell.
         */
        @Override
        public Class<?> getColumnClass(int column) {
            return this.getValueAt(0, column).getClass();
        }

        /**
         * Float column and even-numbered rows of double column are editable.
         */
        @Override
        public boolean isCellEditable(int row, int column) {
            // Note that the data/cell address is constant, no matter where the cell appears onscreen.
            return column == SINGLE_COLUMN || (column == DOUBLE_COLUMN && row % 2 == 0);
        }

        /**
         * Update cell contents in table model.  This method should be called
         * only when user edits cell, so input validation has to be done.  If
         * value is valid, MIPS register is updated.
         */
        @Override
        public void setValueAt(Object value, int row, int column) {
            int valueBase = Coprocessor1Tab.this.gui.getMainPane().getExecuteTab().getValueDisplayBase();
            String stringValue = value.toString();
            try {
                if (column == SINGLE_COLUMN) {
                    if (Binary.isHex(stringValue)) {
                        int intValue = Binary.decodeInteger(stringValue);

                        // Update the cell to the proper number format
                        this.setDisplayAndModelValueAt(NumberDisplayBaseChooser.formatFloatNumber(intValue, valueBase), row, column);

                        // Assures that if changed during MIPS program execution, the update will
                        // occur only between MIPS instructions.
                        Simulator.getInstance().changeState(() -> Coprocessor1.setValue(row, intValue));
                    }
                    else {
                        // Is not hex, so must be decimal
                        float floatValue = Float.parseFloat(stringValue);

                        // Update the cell to the proper number format
                        this.setDisplayAndModelValueAt(NumberDisplayBaseChooser.formatNumber(floatValue, valueBase), row, column);

                        // Assures that if changed during MIPS program execution, the update will
                        // occur only between MIPS instructions.
                        Simulator.getInstance().changeState(() -> Coprocessor1.setSingleFloat(row, floatValue));
                    }
                }
                else if (column == DOUBLE_COLUMN) {
                    if (Binary.isHex(stringValue)) {
                        long longValue = Binary.decodeLong(stringValue);

                        // Update the cell to the proper number format
                        this.setDisplayAndModelValueAt(NumberDisplayBaseChooser.formatDoubleNumber(longValue, valueBase), row, column);

                        // Assures that if changed during MIPS program execution, the update will
                        // occur only between MIPS instructions.
                        Simulator.getInstance().changeState(() -> {
                            try {
                                Coprocessor1.setPairValue(row, longValue);
                            }
                            catch (InvalidRegisterAccessException exception) {
                                // Should never happen
                            }
                        });
                    }
                    else {
                        // Is not hex, so must be decimal
                        double doubleValue = Double.parseDouble(stringValue);

                        // Update the cell to the proper number format
                        this.setDisplayAndModelValueAt(NumberDisplayBaseChooser.formatNumber(doubleValue, valueBase), row, column);

                        // Assures that if changed during MIPS program execution, the update will
                        // occur only between MIPS instructions.
                        Simulator.getInstance().changeState(() -> {
                            try {
                                Coprocessor1.setDoubleFloat(row, doubleValue);
                            }
                            catch (InvalidRegisterAccessException exception) {
                                // Should never happen
                            }
                        });
                    }
                }
            }
            catch (NumberFormatException exception) {
                this.fireTableCellUpdated(row, column);
            }
        }

        /**
         * Update cell contents in table model.  Does not affect MIPS register.
         */
        public void setDisplayAndModelValueAt(Object value, int row, int column) {
            this.data[row][column] = value;
            this.fireTableCellUpdated(row, column);
        }
    }
}