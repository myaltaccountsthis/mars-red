package mars.venus;

import mars.Globals;
import mars.assembler.Directive;
import mars.mips.instructions.Instruction;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLFrameHyperlinkEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
	
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
 * Action for the Help -> Help menu item
 */
public class HelpHelpAction extends GuiAction {
    public HelpHelpAction(String name, Icon icon, String description, Integer mnemonic, KeyStroke accel, VenusUI gui) {
        super(name, icon, description, mnemonic, accel, gui);
    }

    // ideally read or computed from config file...
    private Dimension getSize() {
        return new Dimension(800, 600);
    }

    // Light gray background color for alternating lines of the instruction lists
    private static final Color ALT_BACKGROUND_COLOR = new Color(0xEE, 0xEE, 0xEE);

    /**
     * Separates Instruction name descriptor from detailed (operation) description
     * in help string.
     */
    public static final String descriptionDetailSeparator = ":";

    /**
     * Displays tabs with categories of information.
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("MIPS", createMipsHelpInfoPanel());
        tabbedPane.addTab("MARS", createMarsHelpInfoPanel());
        tabbedPane.addTab("License", createCopyrightInfoPanel());
        tabbedPane.addTab("Bugs/Comments", createHTMLHelpPanel("BugReportingHelp.html"));
        tabbedPane.addTab("Acknowledgements", createHTMLHelpPanel("Acknowledgements.html"));
        tabbedPane.addTab("Instruction Set Song", createHTMLHelpPanel("MIPSInstructionSetSong.html"));
        // Create non-modal dialog. Based on java.sun.com "How to Make Dialogs", DialogDemo.java
        final JDialog dialog = new JDialog(gui, "MARS " + Globals.MARS_VERSION + " Help");
        // Ensure the dialog goes away if user clicks the X
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                dialog.setVisible(false);
                dialog.dispose();
            }
        });
        // Add a "close" button to the non-modal help dialog.
        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e1 -> {
            dialog.setVisible(false);
            dialog.dispose();
        });
        JPanel closePanel = new JPanel();
        closePanel.setLayout(new BoxLayout(closePanel, BoxLayout.LINE_AXIS));
        closePanel.add(Box.createHorizontalGlue());
        closePanel.add(closeButton);
        closePanel.add(Box.createHorizontalGlue());
        closePanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 5));
        JPanel contentPane = new JPanel();
        contentPane.setLayout(new BoxLayout(contentPane, BoxLayout.PAGE_AXIS));
        contentPane.add(tabbedPane);
        contentPane.add(Box.createRigidArea(new Dimension(0, 5)));
        contentPane.add(closePanel);
        contentPane.setOpaque(true);
        dialog.setContentPane(contentPane);
        // Show it
        dialog.setSize(this.getSize());
        dialog.setLocationRelativeTo(gui);
        dialog.setVisible(true);
    }

    // Create panel containing Help Info read from html document.
    private JPanel createHTMLHelpPanel(String filename) {
        JPanel helpPanel = new JPanel(new BorderLayout());
        JScrollPane helpScrollPane;
        JEditorPane helpDisplay;
        try {
            InputStream is = this.getClass().getResourceAsStream(Globals.helpPath + filename);
            BufferedReader in = new BufferedReader(new InputStreamReader(is));
            String line;
            StringBuilder text = new StringBuilder();
            while ((line = in.readLine()) != null) {
                text.append(line).append("\n");
            }
            in.close();
            helpDisplay = new JEditorPane("text/html", text.toString());
            helpDisplay.setEditable(false);
            helpDisplay.setCaretPosition(0); // assure top of document displayed
            helpScrollPane = new JScrollPane(helpDisplay, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
            helpDisplay.addHyperlinkListener(new HelpHyperlinkListener());
        }
        catch (Exception ie) {
            helpScrollPane = new JScrollPane(new JLabel("Error (" + ie + "): " + filename + " contents could not be loaded."));
        }
        helpPanel.add(helpScrollPane);
        return helpPanel;
    }

    // Set up the copyright notice for display.
    private JPanel createCopyrightInfoPanel() {
        JPanel marsCopyrightInfo = new JPanel(new BorderLayout());
        JScrollPane marsCopyrightScrollPane;
        JEditorPane marsCopyrightDisplay;
        try {
            InputStream is = this.getClass().getResourceAsStream("/LICENSE.txt");
            BufferedReader in = new BufferedReader(new InputStreamReader(is));
            String line;
            StringBuilder text = new StringBuilder("<pre>");
            while ((line = in.readLine()) != null) {
                text.append(line).append("\n");
            }
            in.close();
            text.append("</pre>");
            marsCopyrightDisplay = new JEditorPane("text/html", text.toString());
            marsCopyrightDisplay.setEditable(false);
            marsCopyrightDisplay.setCaretPosition(0); // assure top of document displayed
            marsCopyrightScrollPane = new JScrollPane(marsCopyrightDisplay, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        }
        catch (Exception ioe) {
            marsCopyrightScrollPane = new JScrollPane(new JLabel("Error: license contents could not be loaded."));
        }
        marsCopyrightInfo.add(marsCopyrightScrollPane);
        return marsCopyrightInfo;
    }

    // Set up MARS help tab.  Subtabs get their contents from HTML files.
    private JPanel createMarsHelpInfoPanel() {
        JPanel marsHelpInfo = new JPanel(new BorderLayout());
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Intro", createHTMLHelpPanel("MarsHelpIntro.html"));
        tabbedPane.addTab("IDE", createHTMLHelpPanel("MarsHelpIDE.html"));
        tabbedPane.addTab("Debugging", createHTMLHelpPanel("MarsHelpDebugging.html"));
        tabbedPane.addTab("Settings", createHTMLHelpPanel("MarsHelpSettings.html"));
        tabbedPane.addTab("Tools", createHTMLHelpPanel("MarsHelpTools.html"));
        tabbedPane.addTab("Command", createHTMLHelpPanel("MarsHelpCommand.html"));
        tabbedPane.addTab("Limits", createHTMLHelpPanel("MarsHelpLimits.html"));
        tabbedPane.addTab("History", createHTMLHelpPanel("MarsHelpHistory.html"));
        marsHelpInfo.add(tabbedPane);
        return marsHelpInfo;
    }

    // Set up MIPS help tab.  Most contents are generated from instruction set info.
    private JPanel createMipsHelpInfoPanel() {
        JPanel mipsHelpInfo = new JPanel(new BorderLayout());
        String helpRemarksColor = "CCFF99";
        // Introductory remarks go at the top as a label
        String helpRemarks = "<html><center><table bgcolor=\"#" + helpRemarksColor + "\" border=0 cellpadding=0>" +
            "<tr>" + "<th colspan=2><b><i><font size=+1>&nbsp;&nbsp;Operand Key for Example Instructions&nbsp;&nbsp;</font></i></b></th>" + "</tr>" + "<tr>" + "<td><tt>label, target</tt></td><td>any textual label</td>" + "</tr><tr>" + "<td><tt>$t1, $t2, $t3</tt></td><td>any integer register</td>" + "</tr><tr>" + "<td><tt>$f2, $f4, $f6</tt></td><td><i>even-numbered</i> floating point register</td>" + "</tr><tr>" + "<td><tt>$f0, $f1, $f3</tt></td><td><i>any</i> floating point register</td>" + "</tr><tr>" + "<td><tt>$8</tt></td><td>any Coprocessor 0 register</td>" + "</tr><tr>" + "<td><tt>1</tt></td><td>condition flag (0 to 7)</td>" + "</tr><tr>" + "<td><tt>10</tt></td><td>unsigned 5-bit integer (0 to 31)</td>" + "</tr><tr>" + "<td><tt>-100</tt></td><td>signed 16-bit integer (-32768 to 32767)</td>" + "</tr><tr>" + "<td><tt>100</tt></td><td>unsigned 16-bit integer (0 to 65535)</td>" + "</tr><tr>" + "<td><tt>100000</tt></td><td>signed 32-bit integer (-2147483648 to 2147483647)</td>" + "</tr><tr>" + "</tr><tr>" + "<td colspan=2><b><i><font size=+1>Load & Store addressing mode, basic instructions</font></i></b></td>" + "</tr><tr>" + "<td><tt>-100($t2)</tt></td><td>sign-extended 16-bit integer added to contents of $t2</td>" + "</tr><tr>" + "</tr><tr>" + "<td colspan=2><b><i><font size=+1>Load & Store addressing modes, pseudo instructions</font></i></b></td>" + "</tr><tr>" + "<td><tt>($t2)</tt></td><td>contents of $t2</td>" + "</tr><tr>" + "<td><tt>-100</tt></td><td>signed 16-bit integer</td>" + "</tr><tr>" + "<td><tt>100</tt></td><td>unsigned 16-bit integer</td>" + "</tr><tr>" + "<td><tt>100000</tt></td><td>signed 32-bit integer</td>" + "</tr><tr>" + "<td><tt>100($t2)</tt></td><td>zero-extended unsigned 16-bit integer added to contents of $t2</td>" + "</tr><tr>" + "<td><tt>100000($t2)</tt></td><td>signed 32-bit integer added to contents of $t2</td>" + "</tr><tr>" + "<td><tt>label</tt></td><td>32-bit address of label</td>" + "</tr><tr>" + "<td><tt>label($t2)</tt></td><td>32-bit address of label added to contents of $t2</td>" + "</tr><tr>" + "<td><tt>label+100000</tt></td><td>32-bit integer added to label's address</td>" + "</tr><tr>" + "<td><tt>label+100000($t2)&nbsp;&nbsp;&nbsp;</tt></td><td>sum of 32-bit integer, label's address, and contents of $t2</td>" + "</tr>" + "</table></center></html>";
        JLabel helpRemarksLabel = new JLabel(helpRemarks, JLabel.CENTER);
        helpRemarksLabel.setOpaque(true);
        helpRemarksLabel.setBackground(Color.decode("0x" + helpRemarksColor));
        JScrollPane operandsScrollPane = new JScrollPane(helpRemarksLabel, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
        mipsHelpInfo.add(operandsScrollPane, BorderLayout.NORTH);
        // Below the label is a tabbed pane with categories of MIPS help
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Basic Instructions", createMipsInstructionHelpPane("mars.mips.instructions.BasicInstruction"));
        tabbedPane.addTab("Extended (pseudo) Instructions", createMipsInstructionHelpPane("mars.mips.instructions.ExtendedInstruction"));
        tabbedPane.addTab("Directives", createMipsDirectivesHelpPane());
        tabbedPane.addTab("Syscalls", createHTMLHelpPanel("SyscallHelp.html"));
        tabbedPane.addTab("Exceptions", createHTMLHelpPanel("ExceptionsHelp.html"));
        tabbedPane.addTab("Macros", createHTMLHelpPanel("MacrosHelp.html"));
        operandsScrollPane.setPreferredSize(new Dimension((int) this.getSize().getWidth(), (int) (this.getSize().getHeight() * .2)));
        operandsScrollPane.getVerticalScrollBar().setUnitIncrement(10);
        tabbedPane.setPreferredSize(new Dimension((int) this.getSize().getWidth(), (int) (this.getSize().getHeight() * .6)));
        JSplitPane splitsville = new JSplitPane(JSplitPane.VERTICAL_SPLIT, operandsScrollPane, tabbedPane);
        splitsville.setOneTouchExpandable(true);
        splitsville.resetToPreferredSizes();
        mipsHelpInfo.add(splitsville);
        return mipsHelpInfo;
    }

    private JScrollPane createMipsDirectivesHelpPane() {
        Vector<String> exampleList = new Vector<>();
        String blanks = "            "; // 12 blanks
        for (Directive directive : Directive.values()) {
            exampleList.add(directive.toString() + blanks.substring(0, Math.max(0, blanks.length() - directive.toString().length())) + directive.getDescription());
        }
        Collections.sort(exampleList);
        JList<String> examples = new JList<>(exampleList);
        JScrollPane mipsScrollPane = new JScrollPane(examples, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        examples.setFont(new Font("Monospaced", Font.PLAIN, 12));
        return mipsScrollPane;
    }

    private JScrollPane createMipsInstructionHelpPane(String instructionClassName) {
        ArrayList<Instruction> instructionList = Globals.instructionSet.getInstructionList();
        Vector<String> exampleList = new Vector<>(instructionList.size());
        String blanks = "                        "; // 24 blanks
        for (Instruction instr : Globals.instructionSet.getInstructionList()) {
            try {
                if (Class.forName(instructionClassName).isInstance(instr)) {
                    exampleList.add(instr.getExampleFormat() + blanks.substring(0, Math.max(0, blanks.length() - instr.getExampleFormat().length())) + instr.getDescription());
                }
            }
            catch (ClassNotFoundException cnfe) {
                System.out.println(cnfe + " " + instructionClassName);
            }
        }
        Collections.sort(exampleList);
        JList<String> examples = new JList<>(exampleList);
        JScrollPane mipsScrollPane = new JScrollPane(examples, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        examples.setFont(new Font("Monospaced", Font.PLAIN, 12));
        examples.setCellRenderer(new MyCellRenderer());
        return mipsScrollPane;
    }

    private static class MyCellRenderer extends JLabel implements ListCellRenderer<String> {
        // This is the only method defined by ListCellRenderer.
        // We just reconfigure the JLabel each time we're called.
        @Override
        public Component getListCellRendererComponent(JList list, String value, int index, boolean isSelected, boolean cellHasFocus) {
            setText(value);
            if (isSelected) {
                setBackground(list.getSelectionBackground());
                setForeground(list.getSelectionForeground());
            }
            else {
                setBackground((index % 2 == 0) ? ALT_BACKGROUND_COLOR : list.getBackground());
                setForeground(list.getForeground());
            }
            setEnabled(list.isEnabled());
            setFont(list.getFont());
            setOpaque(true);
            return this;
        }
    }

    /**
     *  Determines MARS response when user click on hyperlink in displayed help page.
     *  The response will be to pop up a simple dialog with the page contents.  It
     *  will not display URL, no navigation, nothing.  Just display the page and
     *  provide a Close button.
     */
    private class HelpHyperlinkListener implements HyperlinkListener {
        private static final String CANNOT_DISPLAY_MESSAGE = "<html><title></title><body><strong>Unable to display requested document.</strong></body></html>";

        JDialog webpageDisplay;
        JTextField webpageURL;

        @Override
        public void hyperlinkUpdate(HyperlinkEvent e) {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                JEditorPane pane = (JEditorPane) e.getSource();
                if (e instanceof HTMLFrameHyperlinkEvent evt) {
                    HTMLDocument doc = (HTMLDocument) pane.getDocument();
                    doc.processHTMLFrameHyperlinkEvent(evt);
                }
                else {
                    webpageDisplay = new JDialog(gui, "Primitive HTML Viewer");
                    webpageDisplay.setLayout(new BorderLayout());
                    webpageDisplay.setLocation(gui.getSize().width / 6, gui.getSize().height / 6);
                    JEditorPane webpagePane;
                    try {
                        webpagePane = new JEditorPane(e.getURL());
                    }
                    catch (Throwable t) {
                        webpagePane = new JEditorPane("text/html", CANNOT_DISPLAY_MESSAGE);
                    }
                    webpagePane.addHyperlinkListener(e1 -> {
                        if (e1.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                            JEditorPane pane1 = (JEditorPane) e1.getSource();
                            if (e1 instanceof HTMLFrameHyperlinkEvent evt) {
                                HTMLDocument doc = (HTMLDocument) pane1.getDocument();
                                doc.processHTMLFrameHyperlinkEvent(evt);
                            }
                            else {
                                try {
                                    pane1.setPage(e1.getURL());
                                }
                                catch (Throwable t) {
                                    pane1.setText(CANNOT_DISPLAY_MESSAGE);
                                }
                                webpageURL.setText(e1.getURL().toString());
                            }
                        }
                    });
                    webpagePane.setPreferredSize(new Dimension(gui.getSize().width * 2 / 3, gui.getSize().height * 2 / 3));
                    webpagePane.setEditable(false);
                    webpagePane.setCaretPosition(0);
                    JScrollPane webpageScrollPane = new JScrollPane(webpagePane, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
                    webpageURL = new JTextField(e.getURL().toString(), 50);
                    webpageURL.setEditable(false);
                    webpageURL.setBackground(Color.WHITE);
                    JPanel URLPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
                    URLPanel.add(new JLabel("URL: "));
                    URLPanel.add(webpageURL);
                    webpageDisplay.add(URLPanel, BorderLayout.NORTH);
                    webpageDisplay.add(webpageScrollPane);
                    JButton closeButton = new JButton("Close");
                    closeButton.addActionListener(e1 -> {
                        webpageDisplay.setVisible(false);
                        webpageDisplay.dispose();
                    });
                    JPanel closePanel = new JPanel();
                    closePanel.setLayout(new BoxLayout(closePanel, BoxLayout.LINE_AXIS));
                    closePanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 5));
                    closePanel.add(Box.createHorizontalGlue());
                    closePanel.add(closeButton);
                    closePanel.add(Box.createHorizontalGlue());
                    webpageDisplay.add(closePanel, BorderLayout.SOUTH);
                    webpageDisplay.pack();
                    webpageDisplay.setVisible(true);
                }
            }
        }
    }
}