package org.robotlegs.toolwindows;

import com.intellij.lang.javascript.JavaScriptSupportLoader;
import com.intellij.lang.javascript.psi.JSFile;
import com.intellij.lang.javascript.psi.JSFunction;
import com.intellij.lang.javascript.psi.ecmal4.JSClass;
import com.intellij.lang.javascript.psi.impl.JSChangeUtil;
import com.intellij.lang.javascript.psi.impl.JSPsiImplUtils;
import com.intellij.lang.javascript.psi.resolve.JSResolveUtil;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.table.JBTable;
import com.intellij.usages.UsageInfo2UsageAdapter;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.SortableColumnModel;
import utils.FindUsagesUtils;
import utils.RobotlegsMappingUtils;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

/**
 * User: John Lindquist
 * Date: 6/19/11
 * Time: 6:15 PM
 */
public class BrowserToolWindowFactory implements ToolWindowFactory
{
    private static final String[] COLUMN_TITLES = new String[]{"Filename", "Column 1", "Column 2", "Column 3", "Column 4"};

    //extract Strings to config
    private static final String MEDIATOR_MAP = "org.robotlegs.core.IMediatorMap";
    private static final String MAP_VIEW = "mapView";

    private static final String COMMAND_MAP = "org.robotlegs.core.ICommandMap";
    private static final String MAP_EVENT = "mapEvent";

    private static final String SIGNAL_COMMAND_MAP = "org.robotlegs.core.ISignalCommandMap";
    private static final String MAP_SIGNAL_CLASS = "mapSignalClass";

    private static final String INJECTOR = "org.robotlegs.core.IInjector";
    private static final String MAP_SINGLETON = "mapSingleton";
    private static final String MAP_SINGLETON_OF = "mapSingletonOf";
    private static final String MAP_VALUE = "mapValue";
    private static final String MAP_RULE = "mapRule";
    private static final String MAP_CLASS = "mapClass";

    //tab names
    private static final String MEDIATOR_MAP_NAME = "MediatorMap";
    private static final String COMMAND_MAP_NAME = "CommandMap";
    private static final String SIGNAL_COMMAND_MAP_NAME = "SignalCommandMap";
    private static final String INJECTOR_NAME = "Injector";
    private static final String REFRESH_ALL = "Refresh All";

    private Project project;

    private String[] mediatorFunctions = new String[]{MAP_VIEW};
    private String[] commandFunctions = new String[]{MAP_EVENT};
    private String[] signalFunctions = new String[]{MAP_SIGNAL_CLASS};
    private String[] injectorFunctions = new String[]{MAP_SINGLETON, MAP_SINGLETON_OF, MAP_VALUE, MAP_RULE, MAP_CLASS};

    private UsagesRequestValues mediatorMapValues = new UsagesRequestValues(MEDIATOR_MAP, MEDIATOR_MAP_NAME, mediatorFunctions);
    private UsagesRequestValues commandMapValues = new UsagesRequestValues(COMMAND_MAP, COMMAND_MAP_NAME, commandFunctions);
    private UsagesRequestValues signalCommandMapValues = new UsagesRequestValues(SIGNAL_COMMAND_MAP, SIGNAL_COMMAND_MAP_NAME, signalFunctions);
    private UsagesRequestValues injectorValues = new UsagesRequestValues(INJECTOR, INJECTOR_NAME, injectorFunctions);

    private UsagesRequestValues[] values = new UsagesRequestValues[]{mediatorMapValues, commandMapValues, signalCommandMapValues, injectorValues};
    private ContentManager contentManager;

    final Vector<JBTable> tables = new Vector<JBTable>();

    public void createToolWindowContent(Project project, ToolWindow toolWindow)
    {
        this.project = project;
        contentManager = toolWindow.getContentManager();

        refreshValues();
    }

    private Runnable createRunnable(final List<UsageInfo2UsageAdapter> usages, final String tabName)
    {
        Runnable aRunnable = new Runnable(){
            public void run(){
                ApplicationManager.getApplication().invokeLater(new Runnable() {
                    public void run() {
                        synchronized (usages)
                        {
                            //Create a map of the first param (the "view") to the second param (the "mediator")
                            Vector<UsageMapping> vectorUsages = RobotlegsMappingUtils.getMappedElementsFromFunctionUsages(usages);
                            createTable(project, contentManager, vectorUsages, tabName);
                        }
                    }
                });
            }
        };

        return aRunnable;
    }

    private void refreshValues()
    {
//        contentManager.removeAllContents(true);

        for (UsagesRequestValues value : values)
        {
            List<UsageInfo2UsageAdapter> usages = new ArrayList<UsageInfo2UsageAdapter>();

            Runnable runner = createRunnable(usages, value.getTabName());

            JSClass jsClass = (JSClass) JSResolveUtil.findClassByQName(value.getClassQName(), GlobalSearchScope.allScope(project));

            if (jsClass != null)
            {
                for (String functionName : value.getFunctions())
                {
                    //Find the "mapView" function on IMediatorMap so we can find where it's used throughout the app
                    JSFunction foundFunction = jsClass.findFunctionByName(functionName);

                    if (foundFunction != null)
                    {
                        //Find all the usages of "mapView" and return then as UsageInfo
                        FindUsagesUtils.findUsagesOfPsiElement(foundFunction, project, usages, runner);
                    }
                }
            }
        }
    }

    private void createTable(Project project, ContentManager contentManager, Vector<UsageMapping> usageMappings, String tableName)
    {
        final Vector<Vector> names = new Vector<Vector>();
        final Vector<Vector> dataRows = new Vector<Vector>();

        prepareTableData(usageMappings, dataRows, names);

        if (names.size() > 0)
        {
            final JBTable table;
            JBTable findTable = findTable(tableName);

            AbstractTableModel tableModel = new MappingsTableModel(names);

            if (findTable != null)
            {
                table = findTable;
                table.setModel(tableModel);

                return;
            }
            else
            {
                table = new JBTable(tableModel);
                table.setName(tableName);

                tables.add(table);
            }

            table.setCellSelectionEnabled(true);
            table.setAutoCreateRowSorter(true);

            JPanel jPanel = new JPanel();
            jPanel.setLayout(new BorderLayout());
            JButton button = new JButton(REFRESH_ALL, IconLoader.getIcon("/vcs/refresh.png"));
            button.addMouseListener(new MouseAdapter()
            {
                @Override public void mouseClicked(MouseEvent e)
                {
                    refreshValues();
                }
            });
            jPanel.add(button, BorderLayout.NORTH);

            JBScrollPane jbScrollPane = new JBScrollPane(table);
            jbScrollPane.getVerticalScrollBar().setUnitIncrement(16);

            jPanel.add(jbScrollPane, BorderLayout.CENTER);

            Content content = ContentFactory.SERVICE.getInstance().createContent(jPanel, tableName, false);
            contentManager.addContent(content);

            table.addMouseListener(new MyMouseAdapter(table, dataRows, project));
            table.setEnableAntialiasing(true);
        }
    }

    private JBTable findTable(String tableName)
    {
        for (JBTable table : tables)
        {
            if (table.getName() == tableName)
            {
                return table;
            }
        }

        return null;
    }

    private void prepareTableData(Vector<UsageMapping> usageMappings, Vector<Vector> dataRows, Vector<Vector> names)
    {
        for (UsageMapping usageMapping : usageMappings)
        {
            Vector<String> column = new Vector<String>();
            Vector<Object> dataColumn = new Vector<Object>();

            PsiFile psiFile = usageMapping.getUsage().getElement().getContainingFile();
            column.add(psiFile.getName()); //todo: reconsider how to approach getting names of files
            dataColumn.add(usageMapping.getUsage());

            Vector<PsiElement> mappedElements = usageMapping.getMappedElements();
            for (PsiElement mapping : mappedElements)
            {
                if (mapping instanceof PsiNamedElement)
                {
                    column.add(((PsiNamedElement) mapping).getName());
                }
                else
                {
                    if (mapping != null)
                    {
                        column.add(mapping.getText());
                    }
                }
                dataColumn.add(mapping);
            }

            names.add(column);
            dataRows.add(dataColumn);
        }
    }

    private class MyMouseAdapter extends MouseAdapter
    {
        private final JBTable table;
        private final Vector<Vector> dataRows;
        private final Project project;

        public MyMouseAdapter(JBTable table, Vector<Vector> dataRows, Project project)
        {
            this.table = table;
            this.dataRows = dataRows;
            this.project = project;
        }

        @Override public void mouseClicked(final MouseEvent e)
        {
            int row = table.rowAtPoint(e.getPoint());
            int column = table.columnAtPoint(e.getPoint());
            Object selectionName = table.getValueAt(row, column);
            System.out.print(selectionName + "\n");

            Object itemUnderMouse = null;

            for (Vector rowVector : dataRows)
            {
                if (column < rowVector.size())
                {
                    Object columnItem = rowVector.get(column);
                    String itemName = "";

                    if (columnItem instanceof UsageInfo2UsageAdapter)
                    {
                        UsageInfo2UsageAdapter usageAdapter = (UsageInfo2UsageAdapter) columnItem;
                        itemName = usageAdapter.getElement().getContainingFile().getName();
                    }

                    if (columnItem instanceof PsiNamedElement)
                    {
                        PsiNamedElement psiElement = (PsiNamedElement) columnItem;
                        itemName = psiElement.getName();
                    }
                    else if (columnItem instanceof PsiElement)
                    {
                        PsiElement psiElement = (PsiElement) columnItem;
                        itemName = psiElement.getText();
                    }

                    if (itemName.equals(selectionName))
                    {
                        itemUnderMouse = columnItem;
                        break;
                    }
                }
            }

            if (SwingUtilities.isLeftMouseButton(e))
            {
                if (itemUnderMouse instanceof PsiElement)
                {
                    PsiElement psiElement = (PsiElement) itemUnderMouse;
                    VirtualFile virtualFile = psiElement.getContainingFile().getVirtualFile();
                    FileEditorManager.getInstance(project).openFile(virtualFile, true);
                    Editor selectedTextEditor = FileEditorManager.getInstance(project).getSelectedTextEditor();

                    selectedTextEditor.getCaretModel().moveToOffset(psiElement.getTextOffset());
                    selectedTextEditor.getScrollingModel().scrollToCaret(ScrollType.CENTER);
                }

                if (itemUnderMouse instanceof UsageInfo2UsageAdapter)
                {
                    UsageInfo2UsageAdapter usageAdapter = (UsageInfo2UsageAdapter) itemUnderMouse;
                    VirtualFile virtualFile = usageAdapter.getFile();
                    FileEditorManager.getInstance(project).openFile(virtualFile, true);
                    Editor selectedTextEditor = FileEditorManager.getInstance(project).getSelectedTextEditor();

                    selectedTextEditor.getCaretModel().moveToOffset(usageAdapter.getUsageInfo().getNavigationOffset());
                    selectedTextEditor.getScrollingModel().scrollToCaret(ScrollType.CENTER);
                }
            }


            if (SwingUtilities.isRightMouseButton(e) && itemUnderMouse != null)
            {
                ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.TODO_VIEW_TOOLBAR, createActionGroup(selectionName, itemUnderMouse));
                popupMenu.getComponent().show(table, table.getMousePosition().x, table.getMousePosition().y);

            }
        }
    }

    private ActionGroup createActionGroup(Object selectionName, final Object itemUnderMouse)
    {
        DefaultActionGroup group = new DefaultActionGroup();

        Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
        final PsiFile targetFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());

        group.add(new InjectClassIntoEditorAction(selectionName, targetFile, itemUnderMouse));

        return group;
    }

    private static class MappingsTableModel extends AbstractTableModel implements SortableColumnModel
    {
        private final Vector<Vector> rowNames;

        public MappingsTableModel(Vector<Vector> rowNames)
        {
            this.rowNames = rowNames;
        }

        public int getRowCount()
        {
            return rowNames.size();
        }

        public int getColumnCount()
        {
            return 5;
        }

        @Override
        public String getColumnName(int column)
        {
            return COLUMN_TITLES[column];
        }

        public Object getValueAt(int rowIndex, int columnIndex)
        {
            Vector rows = rowNames.get(rowIndex);
            //if there isn't enough data to fill the column, then return ""
            if (columnIndex < rows.size())
            {
                return rows.get(columnIndex);
            }
            return "";
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return String.class;
        }

        public ColumnInfo[] getColumnInfos()
        {
            return new ColumnInfo[0];  //To change body of implemented methods use File | Settings | File Templates.
        }

        public void setSortable(boolean b)
        {
            //To change body of implemented methods use File | Settings | File Templates.
        }

        public boolean isSortable()
        {
            return true;
        }

        public Object getRowValue(int i)
        {
            return null;  //To change body of implemented methods use File | Settings | File Templates.
        }

        public RowSorter.SortKey getDefaultSortKey()
        {
            return null;  //To change body of implemented methods use File | Settings | File Templates.
        }
    }

    private class InjectSelectedClassIntoEditorClass implements Runnable
    {
        private final PsiFile targetFile;
        private final Object itemUnderMouse;

        public InjectSelectedClassIntoEditorClass(PsiFile targetFile, Object itemUnderMouse)
        {
            this.targetFile = targetFile;
            this.itemUnderMouse = itemUnderMouse;
        }

        public void run()
        {
            JSClass editorClass = JSPsiImplUtils.findClass((JSFile) targetFile);
            JSClass jsClass = (JSClass) itemUnderMouse;
            String nameOfInjectedClass = jsClass.getName();
            JSFile containingFile = (JSFile) jsClass.getContainingFile();
            String importStatement = "import " + JSPsiImplUtils.findPackageStatement(containingFile).getQualifiedName() + "." + jsClass.getName();
            PsiElement importLine = JSChangeUtil.createJSTreeFromText(project, importStatement, JavaScriptSupportLoader.ECMA_SCRIPT_L4).getPsi();
            editorClass.addBefore(importLine, editorClass.getFirstChild());

            String lowercaseNameOfClass = nameOfInjectedClass.substring(0, 1).toLowerCase() + nameOfInjectedClass.substring(1, nameOfInjectedClass.length());

            String statement = "[Inject]\npublic var " + lowercaseNameOfClass + ":" + nameOfInjectedClass + ";";
            PsiElement field = JSChangeUtil.createJSTreeFromText(project, statement, JavaScriptSupportLoader.ECMA_SCRIPT_L4).getPsi();
            placeFieldInClass(editorClass, field);

            PsiDocumentManager.getInstance(project).commitAllDocuments();
        }

        private void placeFieldInClass(JSClass editorClass, PsiElement injectedField)
        {
            if (editorClass.getConstructor() != null)
            {
                editorClass.addBefore(injectedField, editorClass.getConstructor());
            }
            else if (editorClass.getFunctions() != null)
            {
                editorClass.addBefore(injectedField, editorClass.getFunctions()[0]);
            }
            else if (editorClass.getFields() != null)
            {
                editorClass.addBefore(injectedField, editorClass.getFields()[editorClass.getFields().length - 1]);
            }
            else
            {
                editorClass.add(injectedField);
            }
        }
    }

    private class InjectClassIntoEditorAction extends AnAction
    {

        private final PsiFile targetFile;
        private final Object itemUnderMouse;

        public InjectClassIntoEditorAction(Object selectionName, PsiFile targetFile, Object itemUnderMouse)
        {
            super("Inject " + selectionName + " into " + targetFile.getName());
            this.targetFile = targetFile;
            this.itemUnderMouse = itemUnderMouse;
        }

        @Override public void actionPerformed(AnActionEvent e)
        {
            if (itemUnderMouse instanceof PsiElement)
            {
                CommandProcessor.getInstance().executeCommand(project, new Runnable()
                {
                    public void run()
                    {
                        ApplicationManager.getApplication().runWriteAction(new InjectSelectedClassIntoEditorClass(targetFile, itemUnderMouse));
                    }
                }, "Inject Class as Field into File", null);

            }
        }
    }

    private class UsagesRequestValues
    {
        private String classQName;
        private String tabName;
        private String[] functions;

        public String getClassQName()
        {
            return classQName;
        }

        public String getTabName()
        {
            return tabName;
        }

        public String[] getFunctions()
        {
            return functions;
        }

        public UsagesRequestValues(String classQName, String tabName, String[] functions)
        {
            this.classQName = classQName;
            this.tabName = tabName;
            this.functions = functions;
        }
    }
}
