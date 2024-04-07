package mars.venus;

import mars.Application;
import mars.util.SVGIcon;
import mars.venus.actions.ToolAction;
import mars.venus.actions.edit.*;
import mars.venus.actions.file.*;
import mars.venus.actions.help.*;
import mars.venus.actions.run.*;
import mars.venus.actions.settings.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.URL;
import java.util.ArrayList;

/*
Copyright (c) 2003-2013,  Pete Sanderson and Kenneth Vollmar

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
 * Top level container for the Venus GUI.
 *
 * @author Sanderson and Team JSpim
 */
/*
 * Heavily modified by Pete Sanderson, July 2004, to incorporate JSPIMMenu and JSPIMToolbar
 * not as subclasses of JMenuBar and JToolBar, but as instances of them.  They are both
 * here primarily so both can share the Action objects.
 */
public class VenusUI extends JFrame {
    /**
     * Width and height in user pixels of icons in the tool bar and menus
     */
    // TODO: Make this configurable
    public static final int ICON_SIZE = 24;
    /**
     * Time in milliseconds to show splash screen.
     */
    public static final int SPLASH_DURATION_MILLIS = 4000;

    private final JMenuBar menu;
    private final MainPane mainPane;
    private final RegistersPane registersPane;
    private final MessagesPane messagesPane;
    private final Editor editor;

    private final String baseTitle;
    private int menuState = FileStatus.NO_FILE;

    // PLEASE PUT THESE TWO (& THEIR METHODS) SOMEWHERE THEY BELONG, NOT HERE
    private static boolean reset = true; // registers/memory reset for execution
    private static boolean started = false; // started execution

    // The "action" objects, which include action listeners.  One of each will be created then
    // shared between a menu item and its corresponding toolbar button.  This is a very cool
    // technique because it relates the button and menu item so closely.

    private FileNewAction fileNewAction;
    private FileOpenAction fileOpenAction;
    private FileCloseAction fileCloseAction;
    private FileCloseAllAction fileCloseAllAction;
    private FileSaveAction fileSaveAction;
    private FileSaveAsAction fileSaveAsAction;
    private FileSaveAllAction fileSaveAllAction;
    private FileDumpMemoryAction fileDumpMemoryAction;
    private FilePrintAction filePrintAction;
    private FileExitAction fileExitAction;

    private EditUndoAction editUndoAction;
    private EditRedoAction editRedoAction;
    private EditCutAction editCutAction;
    private EditCopyAction editCopyAction;
    private EditPasteAction editPasteAction;
    private EditFindReplaceAction editFindReplaceAction;
    private EditSelectAllAction editSelectAllAction;
    private RunAssembleAction runAssembleAction;

    private RunStartAction runStartAction;
    private RunStepAction runStepAction;
    private RunBackstepAction runBackstepAction;
    private RunPauseAction runPauseAction;
    private RunStopAction runStopAction;
    private RunResetAction runResetAction;
    private RunClearBreakpointsAction runClearBreakpointsAction;
    private RunToggleBreakpointsAction runToggleBreakpointsAction;

    private SettingsLabelAction settingsLabelAction;
    private SettingsPopupInputAction settingsPopupInputAction;
    private SettingsValueDisplayBaseAction settingsValueDisplayBaseAction;
    private SettingsAddressDisplayBaseAction settingsAddressDisplayBaseAction;
    private SettingsExtendedAction settingsExtendedAction;
    private SettingsAssembleOnOpenAction settingsAssembleOnOpenAction;
    private SettingsAssembleAllAction settingsAssembleAllAction;
    private SettingsWarningsAreErrorsAction settingsWarningsAreErrorsAction;
    private SettingsStartAtMainAction settingsStartAtMainAction;
    private SettingsProgramArgumentsAction settingsProgramArgumentsAction;
    private SettingsDelayedBranchingAction settingsDelayedBranchingAction;
    private SettingsExceptionHandlerAction settingsExceptionHandlerAction;
    private SettingsEditorAction settingsEditorAction;
    private SettingsHighlightingAction settingsHighlightingAction;
    private SettingsMemoryConfigurationAction settingsMemoryConfigurationAction;
    private SettingsSelfModifyingCodeAction settingsSelfModifyingCodeAction;

    private HelpHelpAction helpHelpAction;
    private HelpAboutAction helpAboutAction;

    /**
     * Create a new instance of the Venus GUI and show it once it is created.
     *
     * @param title Name of the window to be created.
     */
    public VenusUI(String title) {
        super(title);

        // Launch splash screen
        SplashScreen splashScreen = new SplashScreen(this, SPLASH_DURATION_MILLIS);
        splashScreen.showSplash();

        this.baseTitle = title;
        this.editor = new Editor(this);
        Application.setGUI(this);

        // TODO: Use this logic whenever window is resized. -Sean Clarke
        double screenWidth = Toolkit.getDefaultToolkit().getScreenSize().getWidth();
        double screenHeight = Toolkit.getDefaultToolkit().getScreenSize().getHeight();
        // basically give up some screen space if running at 800 x 600
        double messageWidthPct = (screenWidth < 1000.0) ? 0.67 : 0.73;
        double messageHeightPct = (screenWidth < 1000.0) ? 0.12 : 0.15;
        double mainWidthPct = (screenWidth < 1000.0) ? 0.67 : 0.73;
        double mainHeightPct = (screenWidth < 1000.0) ? 0.60 : 0.65;
        double registersWidthPct = (screenWidth < 1000.0) ? 0.18 : 0.22;
        double registersHeightPct = (screenWidth < 1000.0) ? 0.72 : 0.80;

        Dimension messagesPanePreferredSize = new Dimension((int) (screenWidth * messageWidthPct), (int) (screenHeight * messageHeightPct));
        Dimension mainPanePreferredSize = new Dimension((int) (screenWidth * mainWidthPct), (int) (screenHeight * mainHeightPct));
        Dimension registersPanePreferredSize = new Dimension((int) (screenWidth * registersWidthPct), (int) (screenHeight * registersHeightPct));

        Application.initialize();

        // Image courtesy of NASA/JPL.
        URL iconImageURL = this.getClass().getResource(Application.IMAGES_PATH + "RedMars16.gif");
        if (iconImageURL == null) {
            System.out.println("Internal Error: images folder or file not found");
            System.exit(0);
        }
        Image iconImage = Toolkit.getDefaultToolkit().getImage(iconImageURL);
        this.setIconImage(iconImage);

        // Everything in frame will be arranged on JPanel "center", which is only frame component.
        // "center" has BorderLayout and 2 major components:
        //   -- panel (jp) on North with 2 components
        //      1. toolbar
        //      2. run speed slider.
        //   -- split pane (horizonSplitter) in center with 2 components side-by-side
        //      1. split pane (splitter) with 2 components stacked
        //         a. main pane, with 2 tabs (edit, execute)
        //         b. messages pane with 2 tabs (mars, run I/O)
        //      2. registers pane with 3 tabs (register file, coprocessor 0, coprocessor 1)
        // I should probably run this breakdown out to full detail.  The components are created
        // roughly in bottom-up order; some are created in component constructors and thus are
        // not visible here.

        RegistersWindow registersTab = new RegistersWindow();
        Coprocessor1Window coprocessor1Tab = new Coprocessor1Window();
        Coprocessor0Window coprocessor0Tab = new Coprocessor0Window();
        registersPane = new RegistersPane(this, registersTab, coprocessor1Tab, coprocessor0Tab);
        registersPane.setPreferredSize(registersPanePreferredSize);

        mainPane = new MainPane(this, editor, registersTab, coprocessor1Tab, coprocessor0Tab);
        mainPane.setPreferredSize(mainPanePreferredSize);
        messagesPane = new MessagesPane();
        messagesPane.setPreferredSize(messagesPanePreferredSize);
        JSplitPane splitter = new JSplitPane(JSplitPane.VERTICAL_SPLIT, mainPane, messagesPane);
        splitter.setOneTouchExpandable(true);
        splitter.resetToPreferredSizes();
        JSplitPane horizonSplitter = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, splitter, registersPane);
        horizonSplitter.setOneTouchExpandable(true);
        horizonSplitter.resetToPreferredSizes();

        // due to dependencies, do not set up menu/toolbar until now.
        this.createActionObjects();
        menu = this.setUpMenuBar();
        this.setJMenuBar(menu);

        JToolBar toolbar = this.setUpToolBar();

        JPanel jp = new JPanel(new FlowLayout(FlowLayout.LEFT));
        jp.add(toolbar);
        jp.add(RunSpeedPanel.getInstance());
        JPanel center = new JPanel(new BorderLayout());
        center.add(jp, BorderLayout.NORTH);
        center.add(horizonSplitter);

        this.getContentPane().add(center);

        FileStatus.reset();
        // The following has side effect of establishing menu state
        FileStatus.set(FileStatus.NO_FILE);

        this.addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent event) {
                // Maximize the application window
                VenusUI.this.setExtendedState(JFrame.MAXIMIZED_BOTH);
                splashScreen.toFront();
            }

            @Override
            public void windowClosing(WindowEvent event) {
                // Check for unsaved changes before closing the application
                // Don't save the workspace state after closing all files, unless the exit fails
                editor.getEditTabbedPane().setWorkspaceStateSavingEnabled(false);
                if (editor.closeAll()) {
                    System.exit(0);
                }
                editor.getEditTabbedPane().setWorkspaceStateSavingEnabled(true);
            }
        });

        // The following will handle the windowClosing event properly in the
        // situation where user Cancels out of "save edits?" dialog.  By default,
        // the GUI frame will be hidden but I want it to do nothing.
        this.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

        // Restore previous session
        this.getMainPane().getEditTabbedPane().loadWorkspaceState();

        // Show the window
        this.pack();
        this.setVisible(true);
        // Ensure the splash screen is still visible
        splashScreen.toFront();
    }

    /**
     * Action objects are used instead of action listeners because one can be easily shared between
     * a menu item and a toolbar button.  Does nice things like disable both if the action is
     * disabled, etc.
     */
    private void createActionObjects() {
        Toolkit tk = this.getToolkit();
        Class<?> cs = this.getClass();

        try {
            fileNewAction = new FileNewAction(this, "New", getSVGActionIcon("new.svg"), "Create a new file for editing", KeyEvent.VK_N, KeyStroke.getKeyStroke(KeyEvent.VK_N, tk.getMenuShortcutKeyMaskEx()));
            fileOpenAction = new FileOpenAction(this, "Open...", getSVGActionIcon("open.svg"), "Open a file for editing", KeyEvent.VK_O, KeyStroke.getKeyStroke(KeyEvent.VK_O, tk.getMenuShortcutKeyMaskEx()));
            fileCloseAction = new FileCloseAction(this, "Close", getSVGActionIcon("close.svg"), "Close the current file", KeyEvent.VK_C, KeyStroke.getKeyStroke(KeyEvent.VK_W, tk.getMenuShortcutKeyMaskEx()));
            fileCloseAllAction = new FileCloseAllAction(this, "Close All", getSVGActionIcon("close_all.svg"), "Close all open files", KeyEvent.VK_L, null);
            fileSaveAction = new FileSaveAction(this, "Save", getSVGActionIcon("save.svg"), "Save the current file", KeyEvent.VK_S, KeyStroke.getKeyStroke(KeyEvent.VK_S, tk.getMenuShortcutKeyMaskEx()));
            fileSaveAsAction = new FileSaveAsAction(this, "Save As...", getSVGActionIcon("save_as.svg"), "Save current file with different name", KeyEvent.VK_A, null);
            fileSaveAllAction = new FileSaveAllAction(this, "Save All", getSVGActionIcon("save_all.svg"), "Save all open files", KeyEvent.VK_V, null);
            fileDumpMemoryAction = new FileDumpMemoryAction(this, "Dump Memory...", getSVGActionIcon("dump_memory.svg"), "Dump machine code or data in an available format", KeyEvent.VK_D, KeyStroke.getKeyStroke(KeyEvent.VK_D, tk.getMenuShortcutKeyMaskEx()));
            filePrintAction = new FilePrintAction(this, "Print...", getSVGActionIcon("print.svg"), "Print current file", KeyEvent.VK_P, null);
            fileExitAction = new FileExitAction(this, "Exit", getSVGActionIcon("exit.svg"), "Exit " + Application.NAME, KeyEvent.VK_X, null);

            editUndoAction = new EditUndoAction(this, "Undo", getSVGActionIcon("undo.svg"), "Undo last edit", KeyEvent.VK_U, KeyStroke.getKeyStroke(KeyEvent.VK_Z, tk.getMenuShortcutKeyMaskEx()));
            editRedoAction = new EditRedoAction(this, "Redo", getSVGActionIcon("redo.svg"), "Redo last edit", KeyEvent.VK_R, KeyStroke.getKeyStroke(KeyEvent.VK_Y, tk.getMenuShortcutKeyMaskEx()));
            editCutAction = new EditCutAction(this, "Cut", getSVGActionIcon("cut.svg"), "Cut", KeyEvent.VK_C, KeyStroke.getKeyStroke(KeyEvent.VK_X, tk.getMenuShortcutKeyMaskEx()));
            editCopyAction = new EditCopyAction(this, "Copy", getSVGActionIcon("copy.svg"), "Copy", KeyEvent.VK_O, KeyStroke.getKeyStroke(KeyEvent.VK_C, tk.getMenuShortcutKeyMaskEx()));
            editPasteAction = new EditPasteAction(this, "Paste", getSVGActionIcon("paste.svg"), "Paste", KeyEvent.VK_P, KeyStroke.getKeyStroke(KeyEvent.VK_V, tk.getMenuShortcutKeyMaskEx()));
            editFindReplaceAction = new EditFindReplaceAction(this, "Find / Replace", getSVGActionIcon("find.svg"), "Find and/or replace text in the current file", KeyEvent.VK_F, KeyStroke.getKeyStroke(KeyEvent.VK_F, tk.getMenuShortcutKeyMaskEx()));
            editSelectAllAction = new EditSelectAllAction(this, "Select All", getSVGActionIcon("select_all.svg"), "Select all text in the current file", KeyEvent.VK_A, KeyStroke.getKeyStroke(KeyEvent.VK_A, tk.getMenuShortcutKeyMaskEx()));

            runAssembleAction = new RunAssembleAction(this, "Assemble", getSVGActionIcon("assemble.svg"), "Assemble the current file and clear breakpoints", KeyEvent.VK_A, KeyStroke.getKeyStroke(KeyEvent.VK_F3, 0));
            runStartAction = new RunStartAction(this, "Start", new ImageIcon(tk.getImage(cs.getResource(Application.IMAGES_PATH + "Play22.png"))), "Run the current program", KeyEvent.VK_G, KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0));
            runStepAction = new RunStepAction(this, "Step Forward", new ImageIcon(tk.getImage(cs.getResource(Application.IMAGES_PATH + "StepForward22.png"))), "Execute the next instruction", KeyEvent.VK_T, KeyStroke.getKeyStroke(KeyEvent.VK_F7, 0));
            runBackstepAction = new RunBackstepAction("Step Backward", new ImageIcon(tk.getImage(cs.getResource(Application.IMAGES_PATH + "StepBack22.png"))), "Undo the last step", KeyEvent.VK_B, KeyStroke.getKeyStroke(KeyEvent.VK_F8, 0), this);
            runPauseAction = new RunPauseAction(this, "Pause", new ImageIcon(tk.getImage(cs.getResource(Application.IMAGES_PATH + "Pause22.png"))), "Pause the currently running program", KeyEvent.VK_P, KeyStroke.getKeyStroke(KeyEvent.VK_F9, 0));
            runStopAction = new RunStopAction(this, "Stop", new ImageIcon(tk.getImage(cs.getResource(Application.IMAGES_PATH + "Stop22.png"))), "Stop the currently running program", KeyEvent.VK_S, KeyStroke.getKeyStroke(KeyEvent.VK_F11, 0));
            runResetAction = new RunResetAction(this, "Reset", new ImageIcon(tk.getImage(cs.getResource(Application.IMAGES_PATH + "Reset22.png"))), "Reset MIPS memory and registers", KeyEvent.VK_R, KeyStroke.getKeyStroke(KeyEvent.VK_F12, 0));
            runClearBreakpointsAction = new RunClearBreakpointsAction(this, "Clear All Breakpoints", null, "Clears all execution breakpoints set since the last assemble.", KeyEvent.VK_K, KeyStroke.getKeyStroke(KeyEvent.VK_K, tk.getMenuShortcutKeyMaskEx()));
            runToggleBreakpointsAction = new RunToggleBreakpointsAction(this, "Toggle All Breakpoints", null, "Disable/enable all breakpoints without clearing (can also click Bkpt column header)", KeyEvent.VK_T, KeyStroke.getKeyStroke(KeyEvent.VK_T, tk.getMenuShortcutKeyMaskEx()));

            settingsLabelAction = new SettingsLabelAction(this, "Show symbol table", null, "Toggle visibility of Labels window (symbol table) in the Execute tab", null, null);
            settingsPopupInputAction = new SettingsPopupInputAction(this, "Use dialog for user input", null, "If set, use popup dialog for input syscalls (5, 6, 7, 8, 12) instead of console input", null, null);
            settingsValueDisplayBaseAction = new SettingsValueDisplayBaseAction(this, "Hexadecimal values", null, "Toggle between hexadecimal and decimal display of memory/register values", null, null);
            settingsAddressDisplayBaseAction = new SettingsAddressDisplayBaseAction(this, "Hexadecimal addresses", null, "Toggle between hexadecimal and decimal display of memory addresses", null, null);
            settingsExtendedAction = new SettingsExtendedAction(this, "Allow extended (pseudo) instructions", null, "If set, MIPS extended (pseudo) instructions are formats are permitted.", null, null);
            settingsAssembleOnOpenAction = new SettingsAssembleOnOpenAction(this, "Assemble files when opened", null, "If set, a file will be automatically assembled as soon as it is opened.  File Open dialog will show most recently opened file.", null, null);
            settingsAssembleAllAction = new SettingsAssembleAllAction(this, "Assemble all files in current directory", null, "If set, all files in current directory will be assembled when Assemble operation is selected.", null, null);
            settingsWarningsAreErrorsAction = new SettingsWarningsAreErrorsAction(this, "Promote assembler warnings to errors", null, "If set, assembler warnings will be interpreted as errors and prevent successful assembly.", null, null);
            settingsStartAtMainAction = new SettingsStartAtMainAction(this, "Use \"main\" as program entry point", null, "If set, assembler will initialize Program Counter to text address globally labeled 'main', if defined.", null, null);
            settingsProgramArgumentsAction = new SettingsProgramArgumentsAction(this, "Allow program arguments", null, "If set, program arguments for MIPS program can be entered in border of Text Segment window.", null, null);
            settingsDelayedBranchingAction = new SettingsDelayedBranchingAction(this, "Delayed branching", null, "If set, delayed branching will occur during MIPS execution.", null, null);
            settingsSelfModifyingCodeAction = new SettingsSelfModifyingCodeAction(this, "Self-modifying code", null, "If set, the MIPS program can write and branch to both text and data segments.", null, null);
            settingsEditorAction = new SettingsEditorAction(this, "Editor Settings...", null, "View and modify text editor settings.", null, null);
            settingsHighlightingAction = new SettingsHighlightingAction(this, "Highlighting...", null, "View and modify Execute tab highlighting colors", null, null);
            settingsExceptionHandlerAction = new SettingsExceptionHandlerAction(this, "Exception Handler...", null, "If set, the specified exception handler file will be included in all Assemble operations.", null, null);
            settingsMemoryConfigurationAction = new SettingsMemoryConfigurationAction(this, "Memory Configuration...", null, "View and modify memory segment base addresses for simulated MIPS.", null, null);

            helpHelpAction = new HelpHelpAction(this, "Help...", getSVGActionIcon("help.svg"), "Help", KeyEvent.VK_H, KeyStroke.getKeyStroke(KeyEvent.VK_H, tk.getMenuShortcutKeyMaskEx()));
            helpAboutAction = new HelpAboutAction(this, "About...", getSVGActionIcon("about.svg"), "Information about " + Application.NAME, null, null);
        }
        catch (Exception e) {
            System.out.println("Internal Error: images folder not found, or other null pointer exception while creating Action objects");
            e.printStackTrace();
            System.exit(0);
        }
    }

    private Icon getSVGActionIcon(String filename) {
        URL url = this.getClass().getResource(Application.ACTION_ICONS_PATH + filename);
        return url == null ? null : new SVGIcon(url, ICON_SIZE, ICON_SIZE);
    }

    /**
     * Build the menus and connect them to action objects (which serve as action listeners
     * shared between menu item and corresponding toolbar icon).
     */
    private JMenuBar setUpMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        JMenu fileMenu = new JMenu("File");
        fileMenu.setMnemonic(KeyEvent.VK_F);
        fileMenu.add(createMenuItem(fileNewAction));
        fileMenu.add(createMenuItem(fileOpenAction));
        fileMenu.add(createMenuItem(fileCloseAction));
        fileMenu.add(createMenuItem(fileCloseAllAction));
        fileMenu.addSeparator();
        fileMenu.add(createMenuItem(fileSaveAction));
        fileMenu.add(createMenuItem(fileSaveAsAction));
        fileMenu.add(createMenuItem(fileSaveAllAction));
        fileMenu.addSeparator();
        fileMenu.add(createMenuItem(fileDumpMemoryAction));
        fileMenu.add(createMenuItem(filePrintAction));
        fileMenu.addSeparator();
        fileMenu.add(createMenuItem(fileExitAction));
        menuBar.add(fileMenu);

        JMenu editMenu = new JMenu("Edit");
        editMenu.setMnemonic(KeyEvent.VK_E);
        editMenu.add(createMenuItem(editUndoAction));
        editMenu.add(createMenuItem(editRedoAction));
        editMenu.addSeparator();
        editMenu.add(createMenuItem(editCutAction));
        editMenu.add(createMenuItem(editCopyAction));
        editMenu.add(createMenuItem(editPasteAction));
        editMenu.addSeparator();
        editMenu.add(createMenuItem(editFindReplaceAction));
        editMenu.addSeparator();
        editMenu.add(createMenuItem(editSelectAllAction));
        menuBar.add(editMenu);

        JMenu runMenu = new JMenu("Run");
        runMenu.setMnemonic(KeyEvent.VK_R);
        runMenu.add(createMenuItem(runAssembleAction));
        runMenu.add(createMenuItem(runStartAction));
        runMenu.add(createMenuItem(runStepAction));
        runMenu.add(createMenuItem(runBackstepAction));
        runMenu.add(createMenuItem(runPauseAction));
        runMenu.add(createMenuItem(runStopAction));
        runMenu.add(createMenuItem(runResetAction));
        runMenu.addSeparator();
        runMenu.add(createMenuItem(runClearBreakpointsAction));
        runMenu.add(createMenuItem(runToggleBreakpointsAction));
        menuBar.add(runMenu);

        JMenu settingsMenu = new JMenu("Settings");
        settingsMenu.setMnemonic(KeyEvent.VK_S);
        settingsMenu.add(createMenuCheckBox(settingsLabelAction, Application.getSettings().labelWindowVisible.get()));
        settingsMenu.add(createMenuBaseChooser(settingsAddressDisplayBaseAction, Application.getSettings().displayAddressesInHex.get(), mainPane.getExecutePane().getAddressDisplayBaseChooser()));
        settingsMenu.add(createMenuBaseChooser(settingsValueDisplayBaseAction, Application.getSettings().displayValuesInHex.get(), mainPane.getExecutePane().getValueDisplayBaseChooser()));
        settingsMenu.addSeparator();
        settingsMenu.add(createMenuCheckBox(settingsAssembleOnOpenAction, Application.getSettings().assembleOnOpenEnabled.get()));
        settingsMenu.add(createMenuCheckBox(settingsAssembleAllAction, Application.getSettings().assembleAllEnabled.get()));
        settingsMenu.add(createMenuCheckBox(settingsWarningsAreErrorsAction, Application.getSettings().warningsAreErrors.get()));
        settingsMenu.add(createMenuCheckBox(settingsStartAtMainAction, Application.getSettings().startAtMain.get()));
        settingsMenu.add(createMenuCheckBox(settingsExtendedAction, Application.getSettings().extendedAssemblerEnabled.get()));
        settingsMenu.addSeparator();
        settingsMenu.add(createMenuCheckBox(settingsProgramArgumentsAction, Application.getSettings().useProgramArguments.get()));
        settingsMenu.add(createMenuCheckBox(settingsPopupInputAction, Application.getSettings().popupSyscallInput.get()));
        settingsMenu.add(createMenuCheckBox(settingsDelayedBranchingAction, Application.getSettings().delayedBranchingEnabled.get()));
        settingsMenu.add(createMenuCheckBox(settingsSelfModifyingCodeAction, Application.getSettings().selfModifyingCodeEnabled.get()));
        settingsMenu.addSeparator();
        settingsMenu.add(createMenuItem(settingsEditorAction));
        settingsMenu.add(createMenuItem(settingsHighlightingAction));
        settingsMenu.add(createMenuItem(settingsExceptionHandlerAction));
        settingsMenu.add(createMenuItem(settingsMemoryConfigurationAction));
        menuBar.add(settingsMenu);

        ArrayList<ToolAction> toolActions = ToolLoader.getToolActions();
        if (!toolActions.isEmpty()) {
            JMenu toolsMenu = new JMenu("Tools");
            toolsMenu.setMnemonic(KeyEvent.VK_T);
            for (ToolAction toolAction : toolActions) {
                toolsMenu.add(createMenuItem(toolAction));
            }
            menuBar.add(toolsMenu);
        }

        JMenu helpMenu = new JMenu("Help");
        helpMenu.setMnemonic(KeyEvent.VK_H);
        helpMenu.add(createMenuItem(helpHelpAction));
        helpMenu.add(createMenuItem(helpAboutAction));
        menuBar.add(helpMenu);

        return menuBar;
    }

    private JMenuItem createMenuItem(Action action) {
        JMenuItem menuItem = new JMenuItem(action);
        menuItem.setDisabledIcon(menuItem.getIcon());
        return menuItem;
    }

    private JCheckBoxMenuItem createMenuCheckBox(Action action, boolean selected) {
        JCheckBoxMenuItem checkBox = new JCheckBoxMenuItem(action);
        checkBox.setSelected(selected);
        return checkBox;
    }

    private JCheckBoxMenuItem createMenuBaseChooser(Action action, boolean selected, NumberDisplayBaseChooser chooser) {
        JCheckBoxMenuItem checkBox = new JCheckBoxMenuItem(action);
        checkBox.setSelected(selected);
        chooser.setSettingsMenuItem(checkBox);
        return checkBox;
    }

    /**
     * Build the toolbar and connect items to action objects (which serve as action listeners
     * shared between toolbar icon and corresponding menu item).
     */
    // TODO: Make the toolbar setup customizable -Sean Clarke
    JToolBar setUpToolBar() {
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);
        toolBar.setRollover(true);

        toolBar.add(createToolBarButton(fileNewAction));
        toolBar.add(createToolBarButton(fileOpenAction));
        toolBar.add(createToolBarButton(fileSaveAction));
        toolBar.addSeparator();
        toolBar.add(createToolBarButton(editUndoAction));
        toolBar.add(createToolBarButton(editRedoAction));
        toolBar.add(createToolBarButton(editCutAction));
        toolBar.add(createToolBarButton(editCopyAction));
        toolBar.add(createToolBarButton(editPasteAction));
        toolBar.add(createToolBarButton(editFindReplaceAction));
        toolBar.addSeparator();
        toolBar.add(createToolBarButton(runAssembleAction));
        toolBar.add(createToolBarButton(runStartAction));
        toolBar.add(createToolBarButton(runPauseAction));
        toolBar.add(createToolBarButton(runStopAction));
        toolBar.add(createToolBarButton(runStepAction));
        toolBar.add(createToolBarButton(runBackstepAction));
        toolBar.add(createToolBarButton(runResetAction));
        toolBar.addSeparator();
        toolBar.add(createToolBarButton(helpHelpAction));

        return toolBar;
    }

    private JButton createToolBarButton(Action action) {
        JButton button = new JButton(action);
        button.setHideActionText(button.getIcon() != null);
        return button;
    }

    /**
     * Determine from FileStatus what the menu state (enabled/disabled) should
     * be then call the appropriate method to set it.  Current states are:
     * <ul>
     * <li>setMenuStateInitial: set upon startup and after File->Close
     * <li>setMenuStateEditingNew: set upon File->New
     * <li>setMenuStateEditing: set upon File->Open or File->Save or erroneous Run->Assemble
     * <li>setMenuStateRunnable: set upon successful Run->Assemble
     * <li>setMenuStateRunning: set upon Run->Go
     * <li>setMenuStateTerminated: set upon completion of simulated execution
     * </ul>
     */
    public void setMenuState(int status) {
        menuState = status;
        switch (status) {
            case FileStatus.NO_FILE -> setMenuStateInitial();
            case FileStatus.NEW_NOT_EDITED -> setMenuStateEditingNew();
            case FileStatus.NEW_EDITED -> setMenuStateEditingNew();
            case FileStatus.NOT_EDITED -> setMenuStateNotEdited(); // was MenuStateEditing. DPS 9-Aug-2011
            case FileStatus.EDITED -> setMenuStateEditing();
            case FileStatus.RUNNABLE -> setMenuStateRunnable();
            case FileStatus.RUNNING -> setMenuStateRunning();
            case FileStatus.TERMINATED -> setMenuStateTerminated();
            case FileStatus.OPENING -> {} // This is a temporary state. DPS 9-Aug-2011
            default -> System.out.println("Invalid File Status: " + status);
        }
    }

    void setMenuStateInitial() {
        // Note: undo and redo are handled separately by the undo manager
        fileNewAction.setEnabled(true);
        fileOpenAction.setEnabled(true);
        fileCloseAction.setEnabled(false);
        fileCloseAllAction.setEnabled(false);
        fileSaveAction.setEnabled(false);
        fileSaveAsAction.setEnabled(false);
        fileSaveAllAction.setEnabled(false);
        fileDumpMemoryAction.setEnabled(false);
        filePrintAction.setEnabled(false);
        fileExitAction.setEnabled(true);
        editUndoAction.setEnabled(false);
        editRedoAction.setEnabled(false);
        editCutAction.setEnabled(false);
        editCopyAction.setEnabled(false);
        editPasteAction.setEnabled(false);
        editFindReplaceAction.setEnabled(false);
        editSelectAllAction.setEnabled(false);
        settingsDelayedBranchingAction.setEnabled(true); // added 25 June 2007
        settingsMemoryConfigurationAction.setEnabled(true); // added 21 July 2009
        runAssembleAction.setEnabled(false);
        runStartAction.setEnabled(false);
        runStepAction.setEnabled(false);
        runBackstepAction.setEnabled(false);
        runResetAction.setEnabled(false);
        runStopAction.setEnabled(false);
        runPauseAction.setEnabled(false);
        runClearBreakpointsAction.setEnabled(false);
        runToggleBreakpointsAction.setEnabled(false);
        helpHelpAction.setEnabled(true);
        helpAboutAction.setEnabled(true);
        updateUndoRedoActions();
    }

    /*
     * Added DPS 9-Aug-2011, for newly-opened files.  Retain
     * existing Run menu state (except Assemble, which is always true).
     * Thus if there was a valid assembly it is retained.
     */
    void setMenuStateNotEdited() {
        // Note: undo and redo are handled separately by the undo manager
        fileNewAction.setEnabled(true);
        fileOpenAction.setEnabled(true);
        fileCloseAction.setEnabled(true);
        fileCloseAllAction.setEnabled(true);
        fileSaveAction.setEnabled(true);
        fileSaveAsAction.setEnabled(true);
        fileSaveAllAction.setEnabled(true);
        fileDumpMemoryAction.setEnabled(false);
        filePrintAction.setEnabled(true);
        fileExitAction.setEnabled(true);
        updateUndoRedoActions();
        editCutAction.setEnabled(true);
        editCopyAction.setEnabled(true);
        editPasteAction.setEnabled(true);
        editFindReplaceAction.setEnabled(true);
        editSelectAllAction.setEnabled(true);
        settingsDelayedBranchingAction.setEnabled(true);
        settingsMemoryConfigurationAction.setEnabled(true);
        runAssembleAction.setEnabled(true);
        // If assemble-all, allow previous Run menu settings to remain.
        // Otherwise, clear them out.  DPS 9-Aug-2011
        if (!Application.getSettings().assembleAllEnabled.get()) {
            runStartAction.setEnabled(false);
            runStepAction.setEnabled(false);
            runBackstepAction.setEnabled(false);
            runResetAction.setEnabled(false);
            runStopAction.setEnabled(false);
            runPauseAction.setEnabled(false);
            runClearBreakpointsAction.setEnabled(false);
            runToggleBreakpointsAction.setEnabled(false);
        }
        helpHelpAction.setEnabled(true);
        helpAboutAction.setEnabled(true);
    }

    void setMenuStateEditing() {
        // Note: undo and redo are handled separately by the undo manager
        fileNewAction.setEnabled(true);
        fileOpenAction.setEnabled(true);
        fileCloseAction.setEnabled(true);
        fileCloseAllAction.setEnabled(true);
        fileSaveAction.setEnabled(true);
        fileSaveAsAction.setEnabled(true);
        fileSaveAllAction.setEnabled(true);
        fileDumpMemoryAction.setEnabled(false);
        filePrintAction.setEnabled(true);
        fileExitAction.setEnabled(true);
        updateUndoRedoActions();
        editCutAction.setEnabled(true);
        editCopyAction.setEnabled(true);
        editPasteAction.setEnabled(true);
        editFindReplaceAction.setEnabled(true);
        editSelectAllAction.setEnabled(true);
        settingsDelayedBranchingAction.setEnabled(true); // added 25 June 2007
        settingsMemoryConfigurationAction.setEnabled(true); // added 21 July 2009
        runAssembleAction.setEnabled(true);
        runStartAction.setEnabled(false);
        runStepAction.setEnabled(false);
        runBackstepAction.setEnabled(false);
        runResetAction.setEnabled(false);
        runStopAction.setEnabled(false);
        runPauseAction.setEnabled(false);
        runClearBreakpointsAction.setEnabled(false);
        runToggleBreakpointsAction.setEnabled(false);
        helpHelpAction.setEnabled(true);
        helpAboutAction.setEnabled(true);
    }

    /**
     * Use this when "File -> New" is used
     */
    void setMenuStateEditingNew() {
        // Note: undo and redo are handled separately by the undo manager
        fileNewAction.setEnabled(true);
        fileOpenAction.setEnabled(true);
        fileCloseAction.setEnabled(true);
        fileCloseAllAction.setEnabled(true);
        fileSaveAction.setEnabled(true);
        fileSaveAsAction.setEnabled(true);
        fileSaveAllAction.setEnabled(true);
        fileDumpMemoryAction.setEnabled(false);
        filePrintAction.setEnabled(true);
        fileExitAction.setEnabled(true);
        updateUndoRedoActions();
        editCutAction.setEnabled(true);
        editCopyAction.setEnabled(true);
        editPasteAction.setEnabled(true);
        editFindReplaceAction.setEnabled(true);
        editSelectAllAction.setEnabled(true);
        settingsDelayedBranchingAction.setEnabled(true); // added 25 June 2007
        settingsMemoryConfigurationAction.setEnabled(true); // added 21 July 2009
        runAssembleAction.setEnabled(false);
        runStartAction.setEnabled(false);
        runStepAction.setEnabled(false);
        runBackstepAction.setEnabled(false);
        runResetAction.setEnabled(false);
        runStopAction.setEnabled(false);
        runPauseAction.setEnabled(false);
        runClearBreakpointsAction.setEnabled(false);
        runToggleBreakpointsAction.setEnabled(false);
        helpHelpAction.setEnabled(true);
        helpAboutAction.setEnabled(true);
    }

    /**
     * Use this upon successful assemble or reset
     */
    void setMenuStateRunnable() {
        // Note: undo and redo are handled separately by the undo manager
        fileNewAction.setEnabled(true);
        fileOpenAction.setEnabled(true);
        fileCloseAction.setEnabled(true);
        fileCloseAllAction.setEnabled(true);
        fileSaveAction.setEnabled(true);
        fileSaveAsAction.setEnabled(true);
        fileSaveAllAction.setEnabled(true);
        fileDumpMemoryAction.setEnabled(true);
        filePrintAction.setEnabled(true);
        fileExitAction.setEnabled(true);
        updateUndoRedoActions();
        editCutAction.setEnabled(true);
        editCopyAction.setEnabled(true);
        editPasteAction.setEnabled(true);
        editFindReplaceAction.setEnabled(true);
        editSelectAllAction.setEnabled(true);
        settingsDelayedBranchingAction.setEnabled(true); // added 25 June 2007
        settingsMemoryConfigurationAction.setEnabled(true); // added 21 July 2009
        runAssembleAction.setEnabled(true);
        runStartAction.setEnabled(true);
        runStepAction.setEnabled(true);
        runBackstepAction.setEnabled(Application.getSettings().getBackSteppingEnabled() && !Application.program.getBackStepper().isEmpty());
        runResetAction.setEnabled(true);
        runStopAction.setEnabled(false);
        runPauseAction.setEnabled(false);
        runToggleBreakpointsAction.setEnabled(true);
        helpHelpAction.setEnabled(true);
        helpAboutAction.setEnabled(true);
    }

    /**
     * Use this while program is running
     */
    void setMenuStateRunning() {
        fileNewAction.setEnabled(false);
        fileOpenAction.setEnabled(false);
        fileCloseAction.setEnabled(false);
        fileCloseAllAction.setEnabled(false);
        fileSaveAction.setEnabled(false);
        fileSaveAsAction.setEnabled(false);
        fileSaveAllAction.setEnabled(false);
        fileDumpMemoryAction.setEnabled(false);
        filePrintAction.setEnabled(false);
        fileExitAction.setEnabled(false);
        editUndoAction.setEnabled(false); // DPS 10 Jan 2008
        editRedoAction.setEnabled(false); // DPS 10 Jan 2008
        editCutAction.setEnabled(false);
        editCopyAction.setEnabled(false);
        editPasteAction.setEnabled(false);
        editFindReplaceAction.setEnabled(false);
        editSelectAllAction.setEnabled(false);
        settingsDelayedBranchingAction.setEnabled(false); // added 25 June 2007
        settingsMemoryConfigurationAction.setEnabled(false); // added 21 July 2009
        runAssembleAction.setEnabled(false);
        runStartAction.setEnabled(false);
        runStepAction.setEnabled(false);
        runBackstepAction.setEnabled(false);
        runResetAction.setEnabled(false);
        runStopAction.setEnabled(true);
        runPauseAction.setEnabled(true);
        runToggleBreakpointsAction.setEnabled(false);
        helpHelpAction.setEnabled(true);
        helpAboutAction.setEnabled(true);
    }

    /**
     * Use this upon completion of execution
     */
    void setMenuStateTerminated() {
        // Note: undo and redo are handled separately by the undo manager
        fileNewAction.setEnabled(true);
        fileOpenAction.setEnabled(true);
        fileCloseAction.setEnabled(true);
        fileCloseAllAction.setEnabled(true);
        fileSaveAction.setEnabled(true);
        fileSaveAsAction.setEnabled(true);
        fileSaveAllAction.setEnabled(true);
        fileDumpMemoryAction.setEnabled(true);
        filePrintAction.setEnabled(true);
        fileExitAction.setEnabled(true);
        updateUndoRedoActions();
        editCutAction.setEnabled(true);
        editCopyAction.setEnabled(true);
        editPasteAction.setEnabled(true);
        editFindReplaceAction.setEnabled(true);
        editSelectAllAction.setEnabled(true);
        settingsDelayedBranchingAction.setEnabled(true); // added 25 June 2007
        settingsMemoryConfigurationAction.setEnabled(true); // added 21 July 2009
        runAssembleAction.setEnabled(true);
        runStartAction.setEnabled(false);
        runStepAction.setEnabled(false);
        runBackstepAction.setEnabled(Application.getSettings().getBackSteppingEnabled() && !Application.program.getBackStepper().isEmpty());
        runResetAction.setEnabled(true);
        runStopAction.setEnabled(false);
        runPauseAction.setEnabled(false);
        runToggleBreakpointsAction.setEnabled(true);
        helpHelpAction.setEnabled(true);
        helpAboutAction.setEnabled(true);
    }

    /**
     * Automatically update whether the Undo and Redo actions are enabled or disabled
     * based on the status of the {@link javax.swing.undo.UndoManager}.
     */
    public void updateUndoRedoActions() {
        editUndoAction.updateEnabledStatus();
        editRedoAction.updateEnabledStatus();
    }

    /**
     * Set the content to display as part of the window title, if any. (Usually, this is a filename.)
     * The resulting full title will look like "(content) - (base title)" if the content is not null,
     * or simply the base title otherwise.
     */
    public void setTitleContent(String content) {
        if (content != null) {
            setTitle(content + " - " + baseTitle);
        }
        else {
            setTitle(baseTitle);
        }
    }

    /**
     * Get current menu state.  State values are constants in FileStatus class.  DPS 23 July 2008
     *
     * @return Current menu state.
     */
    public int getMenuState() {
        return menuState;
    }

    /**
     * Set whether the register values are reset.
     *
     * @param reset Boolean true if the register values have been reset.
     */
    public static void setReset(boolean reset) {
        VenusUI.reset = reset;
    }

    /**
     * Set whether MIPS program execution has started.
     *
     * @param started true if the MIPS program execution has started.
     */
    public static void setStarted(boolean started) {
        VenusUI.started = started;
    }

    /**
     * Get whether the register values are reset.
     *
     * @return Boolean true if the register values have been reset.
     */
    public static boolean getReset() {
        return reset;
    }

    /**
     * Get whether MIPS program is currently executing.
     *
     * @return true if MIPS program is currently executing.
     */
    public static boolean getStarted() {
        return started;
    }

    /**
     * Get a reference to the text editor associated with this GUI.
     *
     * @return Editor for the GUI.
     */
    public Editor getEditor() {
        return editor;
    }

    /**
     * Get a reference to the main pane associated with this GUI.
     *
     * @return MainPane for the GUI.
     */
    public MainPane getMainPane() {
        return mainPane;
    }

    /**
     * Get a reference to the messages pane associated with this GUI.
     *
     * @return MessagesPane for the GUI.
     */
    public MessagesPane getMessagesPane() {
        return messagesPane;
    }

    /**
     * Get reference to registers pane associated with this GUI.
     *
     * @return RegistersPane for the GUI.
     */
    public RegistersPane getRegistersPane() {
        return registersPane;
    }

    /**
     * Get a reference to the Run->Assemble action.  Needed by File->Open in case the
     * assemble-upon-open flag is set.
     *
     * @return The object for the Run->Assemble operation.
     */
    public RunAssembleAction getRunAssembleAction() {
        return runAssembleAction;
    }

    /**
     * Have the menu request keyboard focus.  DPS 5-4-10
     */
    public void haveMenuRequestFocus() {
        this.menu.requestFocus();
    }

    /**
     * Send keyboard event to menu for possible processing.  DPS 5-4-10
     *
     * @param e KeyEvent for menu component to consider for processing.
     */
    public void dispatchEventToMenu(KeyEvent e) {
        this.menu.dispatchEvent(e);
    }
}