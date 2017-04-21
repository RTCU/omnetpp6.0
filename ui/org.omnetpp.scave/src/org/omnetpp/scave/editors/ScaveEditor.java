/*--------------------------------------------------------------*
  Copyright (C) 2006-2015 OpenSim Ltd.

  This file is distributed WITHOUT ANY WARRANTY. See the file
  'License' for details on this and other legal matters.
*--------------------------------------------------------------*/

package org.omnetpp.scave.editors;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EventObject;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.emf.common.command.BasicCommandStack;
import org.eclipse.emf.common.command.Command;
import org.eclipse.emf.common.command.CommandStack;
import org.eclipse.emf.common.command.CommandStackListener;
import org.eclipse.emf.common.notify.Adapter;
import org.eclipse.emf.common.notify.AdapterFactory;
import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.common.notify.Notifier;
import org.eclipse.emf.common.notify.impl.AdapterFactoryImpl;
import org.eclipse.emf.common.notify.impl.AdapterImpl;
import org.eclipse.emf.common.ui.MarkerHelper;
import org.eclipse.emf.common.util.BasicDiagnostic;
import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.edit.command.AddCommand;
import org.eclipse.emf.edit.domain.AdapterFactoryEditingDomain;
import org.eclipse.emf.edit.domain.EditingDomain;
import org.eclipse.emf.edit.domain.IEditingDomainProvider;
import org.eclipse.emf.edit.provider.AdapterFactoryItemDelegator;
import org.eclipse.emf.edit.provider.ComposedAdapterFactory;
import org.eclipse.emf.edit.provider.IChangeNotifier;
import org.eclipse.emf.edit.provider.INotifyChangedListener;
import org.eclipse.emf.edit.provider.ReflectiveItemProviderAdapterFactory;
import org.eclipse.emf.edit.provider.resource.ResourceItemProviderAdapterFactory;
import org.eclipse.emf.edit.ui.dnd.EditingDomainViewerDropAdapter;
import org.eclipse.emf.edit.ui.dnd.LocalTransfer;
import org.eclipse.emf.edit.ui.dnd.ViewerDragAdapter;
import org.eclipse.emf.edit.ui.provider.AdapterFactoryContentProvider;
import org.eclipse.emf.edit.ui.provider.AdapterFactoryLabelProvider;
import org.eclipse.emf.edit.ui.util.EditUIMarkerHelper;
import org.eclipse.emf.edit.ui.view.ExtendedPropertySheetPage;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IStatusLineManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.util.LocalSelectionTransfer;
import org.eclipse.jface.viewers.DecoratingLabelProvider;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.StructuredViewer;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Item;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.swt.widgets.Widget;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorSite;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IMemento;
import org.eclipse.ui.INavigationLocation;
import org.eclipse.ui.INavigationLocationProvider;
import org.eclipse.ui.IPartListener;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.actions.WorkspaceModifyOperation;
import org.eclipse.ui.dialogs.SaveAsDialog;
import org.eclipse.ui.ide.IGotoMarker;
import org.eclipse.ui.part.FileEditorInput;
import org.eclipse.ui.views.contentoutline.ContentOutline;
import org.eclipse.ui.views.contentoutline.ContentOutlinePage;
import org.eclipse.ui.views.contentoutline.IContentOutlinePage;
import org.eclipse.ui.views.properties.IPropertyDescriptor;
import org.eclipse.ui.views.properties.IPropertySheetEntry;
import org.eclipse.ui.views.properties.IPropertySheetPage;
import org.eclipse.ui.views.properties.PropertySheet;
import org.eclipse.ui.views.properties.PropertySheetPage;
import org.eclipse.ui.views.properties.PropertySheetSorter;
import org.omnetpp.common.ui.HoverSupport;
import org.omnetpp.common.ui.HtmlHoverInfo;
import org.omnetpp.common.ui.IHoverInfoProvider;
import org.omnetpp.common.ui.IconGridViewer;
import org.omnetpp.common.ui.MultiPageEditorPartExt;
import org.omnetpp.common.util.DetailedPartInitException;
import org.omnetpp.common.util.ReflectionUtils;
import org.omnetpp.common.util.StringUtils;
import org.omnetpp.scave.Markers;
import org.omnetpp.scave.ScavePlugin;
import org.omnetpp.scave.charting.ChartCanvas;
import org.omnetpp.scave.editors.treeproviders.InputsViewLabelProvider;
import org.omnetpp.scave.editors.treeproviders.ScaveModelLabelDecorator;
import org.omnetpp.scave.editors.treeproviders.ScaveModelLabelProvider;
import org.omnetpp.scave.editors.ui.BrowseDataPage;
import org.omnetpp.scave.editors.ui.ChartPage;
import org.omnetpp.scave.editors.ui.ChartsPage;
import org.omnetpp.scave.editors.ui.DatasetsAndChartsPage;
import org.omnetpp.scave.editors.ui.InputsPage;
import org.omnetpp.scave.editors.ui.ScaveEditorPage;
import org.omnetpp.scave.engine.ResultFileManager;
import org.omnetpp.scave.engineext.ResultFileManagerEx;
import org.omnetpp.scave.model.Analysis;
import org.omnetpp.scave.model.BarChart;
import org.omnetpp.scave.model.Chart;
import org.omnetpp.scave.model.HistogramChart;
import org.omnetpp.scave.model.InputFile;
import org.omnetpp.scave.model.Inputs;
import org.omnetpp.scave.model.LineChart;
import org.omnetpp.scave.model.ScatterChart;
import org.omnetpp.scave.model.ScaveModelFactory;
import org.omnetpp.scave.model.ScaveModelPackage;
import org.omnetpp.scave.model.provider.ScaveEditPlugin;
import org.omnetpp.scave.model2.IScaveEditorContext;
import org.omnetpp.scave.model2.ResultItemRef;
import org.omnetpp.scave.model2.provider.ScaveModelItemProviderAdapterFactory;

/**
 * OMNeT++ Analysis tool.
 *
 * @author andras, tomi
 */
public class ScaveEditor extends MultiPageEditorPartExt implements IEditingDomainProvider, ISelectionProvider, IMenuListener, IGotoMarker, INavigationLocationProvider {
    public static final String
        ACTIVE_PAGE = "ActivePage",
        PAGE = "Page",
        PAGE_ID = "PageId";

    private InputsPage inputsPage;
    private BrowseDataPage browseDataPage;
    private DatasetsAndChartsPage datasetsPage;
    private ChartsPage chartsPage;
    private Map<EObject,ScaveEditorPage> closablePages = new LinkedHashMap<EObject,ScaveEditorPage>();


    /**
     * This keeps track of the editing domain that is used to track all changes to the model.
     */
    protected AdapterFactoryEditingDomain editingDomain;

    /**
     * This is the one adapter factory used for providing views of the model.
     */
    protected ComposedAdapterFactory adapterFactory;

    /**
     * This is the content outline page.
     */
    protected IContentOutlinePage contentOutlinePage;

    /**
     * This is a kludge...
     */
    protected IStatusLineManager contentOutlineStatusLineManager;

    /**
     * This is the content outline page's viewer.
     */
    protected TreeViewer contentOutlineViewer;

    /**
     * This is the property sheet page.
     */
    protected List<PropertySheetPage> propertySheetPages = new ArrayList<PropertySheetPage>();

    /**
     * The selection change listener added to all viewers
     */
    protected ISelectionChangedListener selectionChangedListener = new ISelectionChangedListener() {
        public void selectionChanged(SelectionChangedEvent selectionChangedEvent) {
            handleSelectionChange(selectionChangedEvent.getSelection());
        }
    };

    protected boolean selectionChangeInProgress = false; // to prevent recursive notifications

    /**
     * This keeps track of all the {@link org.eclipse.jface.viewers.ISelectionChangedListener}s that are listening to this editor.
     * We need this because we implement ISelectionProvider which includes having to manage a listener list.
     */
    protected Collection<ISelectionChangedListener> selectionChangedListeners = new ArrayList<ISelectionChangedListener>();

    /**
     * This keeps track of the selection of the editor as a whole.
     */
    protected ISelection editorSelection = StructuredSelection.EMPTY;

    protected MarkerHelper markerHelper = new EditUIMarkerHelper();

    /**
     * This listens for when the outline becomes active
     */
    protected IPartListener partListener =
        new IPartListener() {
            public void partActivated(IWorkbenchPart p) {
                if (p instanceof ContentOutline) {
                    if (((ContentOutline)p).getCurrentPage() == contentOutlinePage) {
                        getActionBarContributor().setActiveEditor(ScaveEditor.this);
                    }
                }
                else if (p instanceof PropertySheet) {
                    if (propertySheetPages.contains(((PropertySheet)p).getCurrentPage())) {
                        getActionBarContributor().setActiveEditor(ScaveEditor.this);
                        handleActivate();
                    }
                }
                else if (p == ScaveEditor.this) {
                    handleActivate();
                }
            }
            public void partBroughtToTop(IWorkbenchPart p) {
            }
            public void partClosed(IWorkbenchPart p) {
            }
            public void partDeactivated(IWorkbenchPart p) {
            }
            public void partOpened(IWorkbenchPart p) {
            }
        };

    
    /**
     *  ResultFileManager containing all files of the analysis.
     */
    private ResultFileManagerEx manager = new ResultFileManagerEx();

    /**
     * Loads/unloads result files in manager, according to changes in the model and in the workspace.
     */
    private ResultFilesTracker tracker;

    /**
     * Updates pages when the model changed.
     */
    private INotifyChangedListener pageUpdater = new INotifyChangedListener() {
        @Override
        public void notifyChanged(Notification notification) {
            updatePages(notification);
        }
    };

    /**
     * Factory of Scave objects.
     */
    private static final ScaveModelFactory factory = ScaveModelFactory.eINSTANCE;

    /**
     * Scave model package.
     */
    private static final ScaveModelPackage pkg = ScaveModelPackage.eINSTANCE;

    /**
     * Implements IScaveEditorContext to provide access to some
     * components of this editor.
     * The class implemented as an Adapter, so it can be associated with
     * model objects (EObjects).
     */
    class ScaveEditorContextAdapter extends AdapterImpl implements IScaveEditorContext
    {
        @Override
        public ResultFileManagerEx getResultFileManager() {
            return ScaveEditor.this.getResultFileManager();
        }

        @Override
        public IChangeNotifier getChangeNotifier() {
            return (IChangeNotifier)ScaveEditor.this.getAdapterFactory();
        }

        @Override
        public ILabelProvider getScaveModelLavelProvider() {
            return new AdapterFactoryLabelProvider(ScaveEditor.this.getAdapterFactory());
        }
    }

    private ScaveEditorContextAdapter editorContextAdapter = new ScaveEditorContextAdapter();

    /**
     * The constructor.
     */
    public ScaveEditor() {

        // Create an adapter factory that yields item providers.
        //
        List<AdapterFactory> factories = new ArrayList<AdapterFactory>();
        factories.add(new ResourceItemProviderAdapterFactory());
        factories.add(new ScaveModelItemProviderAdapterFactory());
        factories.add(new ReflectiveItemProviderAdapterFactory());

        adapterFactory = new ComposedAdapterFactory(factories);

        // Create the command stack that will notify this editor as commands are executed.
        //
        BasicCommandStack commandStack = new BasicCommandStack();

        // Add a listener to set the most recent command's affected objects to be the selection of the viewer with focus.
        //
        commandStack.addCommandStackListener
            (new CommandStackListener() {
                 public void commandStackChanged(final EventObject event) {
                     getContainer().getDisplay().asyncExec
                         (new Runnable() {
                              public void run() {
                                  firePropertyChange(IEditorPart.PROP_DIRTY);

                                  // Try to select the affected objects.
                                  //
                                  Command mostRecentCommand = ((CommandStack)event.getSource()).getMostRecentCommand();
                                  if (mostRecentCommand != null) {
                                      setSelectionToViewer(mostRecentCommand.getAffectedObjects());
                                  }
                                  for (Iterator<PropertySheetPage> i = propertySheetPages.iterator(); i.hasNext(); ) {
                                      PropertySheetPage propertySheetPage = i.next();
                                      if (propertySheetPage.getControl().isDisposed()) {
                                          i.remove();
                                      }
                                      else {
                                          propertySheetPage.refresh();
                                      }
                                  }
                              }
                          });
                 }
             });

        // Create the editing domain with a special command stack.
        //
        editingDomain = new AdapterFactoryEditingDomain(adapterFactory, commandStack, new HashMap<Resource,Boolean>());
    }

    public ResultFileManagerEx getResultFileManager() {
        return manager;
    }

    public InputsPage getInputsPage() {
        return inputsPage;
    }

    public BrowseDataPage getBrowseDataPage() {
        return browseDataPage;
    }

    public DatasetsAndChartsPage getDatasetsPage() {
        return datasetsPage;
    }

    public ChartsPage getChartsPage() {
        return chartsPage;
    }

    @Override
    public void init(IEditorSite site, IEditorInput editorInput)
        throws PartInitException {

//        if (true)
//            throw new PartInitException("NOT IMPL");

        if (!(editorInput instanceof IFileEditorInput))
            throw new DetailedPartInitException("Invalid input, it must be a file in the workspace: " + editorInput.getName(),
                "Please make sure the project is open before trying to open a file in it.");
        IFile fileInput = ((IFileEditorInput)editorInput).getFile();
        if (!editorInput.exists())
            throw new PartInitException("Missing Input: Resource '" + fileInput.getFullPath().toString() + "' does not exists");
        File javaFile = fileInput.getLocation().toFile();
        if (!javaFile.exists())
            throw new PartInitException("Missing Input: Scave file '" + javaFile.toString() + "' does not exists");

        // add part listener to save the editor state *before* it is disposed
        final IWorkbenchPage page = site.getPage();
        page.addPartListener(new IPartListener() {
            @Override
            public void partActivated(IWorkbenchPart part) {}
            @Override
            public void partBroughtToTop(IWorkbenchPart part) {}
            @Override
            public void partDeactivated(IWorkbenchPart part) {}
            @Override
            public void partOpened(IWorkbenchPart part) {}
            @Override
            public void partClosed(IWorkbenchPart part) {
                if (part == ScaveEditor.this) {
                    page.removePartListener(this);
                    saveState();
                }
            }
        });

        // init super. Note that this does not load the model yet -- it's done in createModel() called from createPages().
        setSite(site);
        setInputWithNotify(editorInput);
        setPartName(editorInput.getName());
        site.setSelectionProvider(this);
        site.getPage().addPartListener(partListener);
    }

    @Override
    public void dispose() {
        if (tracker!=null) {
            ResourcesPlugin.getWorkspace().removeResourceChangeListener(tracker);
        }

        if (tracker != null) adapterFactory.removeListener(tracker);
        adapterFactory.removeListener(pageUpdater);

        if (manager != null) {
            // deactivate the tracker explicitly, because it might receive a notification
            // in case of the ScaveEditor.dispose() was called from a notification.
            boolean trackerInactive = true;
            if (tracker != null) {
                trackerInactive = tracker.deactivate();
                tracker = null;
            }
            // it would get garbage-collected anyway, but the sooner the better because it may have allocated large amounts of data
            if (trackerInactive)
                manager.dispose();
            manager = null;
        }

        if (getSite() != null && getSite().getPage() != null)
            getSite().getPage().removePartListener(partListener);

        adapterFactory.dispose();

        if (getActionBarContributor() != null && getActionBarContributor().getActiveEditor() == this)
            getActionBarContributor().setActiveEditor(null);

        for (PropertySheetPage propertySheetPage : propertySheetPages)
            propertySheetPage.dispose();

        if (contentOutlinePage != null)
            contentOutlinePage.dispose();

        super.dispose();
        
    }

    // Modified DropAdapter to convert drop events.
    // The original EditingDomainViewerDropAdapter tries to add
    // files to the ResourceSet as XMI documents (what probably
    // causes a parse error). Here we convert the URIs of the
    // drop source to InputFiles and modify the drop target.
    class DropAdapter extends EditingDomainViewerDropAdapter
    {
        List<InputFile> inputFilesInSource = null;

        public DropAdapter(EditingDomain domain, Viewer viewer) {
            super(domain, viewer);
        }

        @Override
        protected Collection<?> extractDragSource(Object object) {
            Collection<?> collection = super.extractDragSource(object);

            // find URIs in source and convert them InputFiles
            ScaveModelFactory factory = ScaveModelFactory.eINSTANCE;
            inputFilesInSource = null;
            for (Object element : collection) {
                if (element instanceof URI) {
                    String workspacePath = getWorkspacePathFromURI((URI)element);
                    if (workspacePath != null) {
                        if (inputFilesInSource == null)
                            inputFilesInSource = new ArrayList<InputFile>();
                        if (workspacePath.endsWith(".sca") || workspacePath.endsWith(".vec")) {
                            InputFile file = factory.createInputFile();
                            file.setName(workspacePath);
                            inputFilesInSource.add(file);
                        }
                    }
                }
            }

            return inputFilesInSource != null ? inputFilesInSource : collection;
        }

        @Override
        protected Object extractDropTarget(Widget item) {
            Object target = super.extractDropTarget(item);
            if (inputFilesInSource != null) {
                if (target instanceof InputFile)
                    target = ((InputFile)target).eContainer();
                else if (target == null)
                    target = getAnalysis().getInputs();
            }
            return target;
        }
    }

    private String getWorkspacePathFromURI(URI uri) {
        if (uri.isFile()) {
            IPath path = new Path(uri.toFileString());
            IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
            IFile file = root.getFileForLocation(path);
            return file != null ? file.getFullPath().toString() : null;
        }
        else if (uri.isPlatformResource())
            return uri.toPlatformString(true);
        else
            return null;
    }

    protected void setupDragAndDropSupportFor(StructuredViewer viewer) {
        int dndOperations = DND.DROP_COPY | DND.DROP_MOVE | DND.DROP_LINK;
        // XXX FileTransfer causes an exception
        Transfer[] transfers = new Transfer[] { LocalTransfer.getInstance(), LocalSelectionTransfer.getTransfer()/*, FileTransfer.getInstance()*/ };
        viewer.addDragSupport(dndOperations, transfers, new ViewerDragAdapter(viewer));
        viewer.addDropSupport(dndOperations, transfers, new DropAdapter(editingDomain, viewer));
    }

    public void createModel() {
        // Assumes that the input is a file object.
        IFileEditorInput modelFile = (IFileEditorInput)getEditorInput();
        URI resourceURI = URI.createPlatformResourceURI(modelFile.getFile().getFullPath().toString(), true);;
        try {
            editingDomain.getResourceSet().getResource(resourceURI, true);
        }
        catch (Exception e) {
            ScavePlugin.logError("coul not load resource", e);
            //TODO dialog? close editor?
            return;
        }

        // ensure mandatory objects exist.
        // it is ensured that these objects can not be replaced
        // or deleted from the model (using commands)
        // see AnalysisItemProvider
        Analysis analysis = getAnalysis();
        if (analysis.getInputs()==null)
            analysis.setInputs(factory.createInputs());
        if (analysis.getCharts()==null)
            analysis.setCharts(factory.createCharts());

        // create an adapter factory, that associates editorContextAdapter to Resource objects.
        // Therefore the editor context can be accessed from model objects by calling
        // EcoreUtil.getRegisteredAdapter(eObject.eResource(), IScaveEditorContext.class),
        // or simply ScaveModelUtil.getEditorContextFor(eObject).
        editingDomain.getResourceSet().getAdapterFactories().add(new AdapterFactoryImpl() {
            @Override
            public boolean isFactoryForType(Object type) { return type == IScaveEditorContext.class; }
            @Override
            protected Adapter createAdapter(Notifier target) { return target instanceof Resource ? editorContextAdapter : null; }
        });


        IFile inputFile = ((IFileEditorInput)getEditorInput()).getFile();
        tracker = new ResultFilesTracker(manager, analysis.getInputs(), inputFile.getParent().getFullPath());

        // listen to model changes
        adapterFactory.addListener(tracker);
        adapterFactory.addListener(pageUpdater);

        // listen to resource changes: create, delete, modify
        ResourcesPlugin.getWorkspace().addResourceChangeListener(tracker);
    }

    protected Resource createTempResource() {
        IFileEditorInput modelFile = (IFileEditorInput)getEditorInput();
        IPath tempResourcePath = modelFile.getFile().getFullPath().addFileExtension("temp");
        URI resourceURI = URI.createPlatformResourceURI(tempResourcePath.toString(), true);;
        Resource resource = editingDomain.getResourceSet().createResource(resourceURI);
        Analysis analysis = factory.createAnalysis();
        analysis.setInputs(factory.createInputs());
        analysis.setCharts(factory.createCharts());
        resource.getContents().add(analysis);
        return resource;
    }

    protected void doCreatePages() {
        // add fixed pages: Inputs, Browse Data, Datasets
        FillLayout layout = new FillLayout();
        getContainer().setLayout(layout);

        getTabFolder().setMRUVisible(true);

        createInputsPage();
        createBrowseDataPage();
        createDatasetsPage();
        createChartsPage();

        // We can load the result files now.
        // The chart pages are not refreshed automatically when the result files change,
        // so we have to load the files synchronously
        // Note that tracker.updaterJob.join() can not be used here, because the JobManager is suspended during initalization of the UI.
        tracker.synchronize(true);

        // now we can restore chart pages (and other closable pages)
        ResultFileManager.callWithReadLock(manager, new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                restoreState();
                return null;
            }
        });

        final CTabFolder tabfolder = getTabFolder();
        tabfolder.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                int newPageIndex = tabfolder.indexOf((CTabItem) e.item);
                pageChangedByUser(newPageIndex);
            }
        });
    }

    protected CTabFolder getTabFolder() {
        return (CTabFolder)getContainer();
    }

    protected void initializeContentOutlineViewer(Viewer contentOutlineViewer) {
        contentOutlineViewer.setInput(getAnalysis());
    }

    public IPropertySheetPage getPropertySheetPage() {
        PropertySheetPage propertySheetPage =
                new ExtendedPropertySheetPage(editingDomain) {
                    @Override
                    public void setSelectionToViewer(List<?> selection) {
                        ScaveEditor.this.setSelectionToViewer(selection);
                        ScaveEditor.this.setFocus();
                    }

                    // this is a constructor fragment --Andras
                    {
                        // turn off sorting for our INonsortablePropertyDescriptors
                        setSorter(new PropertySheetSorter() {
                            public int compare(IPropertySheetEntry entryA, IPropertySheetEntry entryB) {
                                IPropertyDescriptor descriptorA = (IPropertyDescriptor) ReflectionUtils.getFieldValue(entryA, "descriptor"); // it's f***ing private --Andras
                                if (descriptorA instanceof INonsortablePropertyDescriptor)
                                    return 0;
                                else
                                    return super.compare(entryA, entryB);
                            }
                        });
                    }

                    @Override
                    public void setActionBars(IActionBars actionBars) {
                        super.setActionBars(actionBars);
                        getActionBarContributor().shareGlobalActions(this, actionBars);
                    }

                    @Override
                    public void selectionChanged(IWorkbenchPart part, ISelection selection) {
                        if (selection instanceof IDListSelection) {
                            // re-package element into ResultItemRef, otherwise property sheet
                            // only gets a Long due to sel.toArray() in base class. We only want
                            // to show properties if there's only one item in the selection.
                            IDListSelection idList = (IDListSelection)selection;
                            if (idList.size() == 1) {
                                long id = (Long)idList.getFirstElement();
                                ResultItemRef resultItemRef = new ResultItemRef(id, idList.getResultFileManager());
                                selection = new StructuredSelection(resultItemRef);
                            }
                        }
                        super.selectionChanged(part, selection);
                    }
                };

        propertySheetPage.setPropertySourceProvider(new AdapterFactoryContentProvider(adapterFactory));
        propertySheetPages.add(propertySheetPage);

        if (propertySheetPage instanceof PropertySheetPage) {
            ((PropertySheetPage)propertySheetPage).setPropertySourceProvider(
                new ScavePropertySourceProvider(adapterFactory, manager));
        }
        return propertySheetPage;
    }

    /**
     * Adds a fixed (non-closable) editor page at the last position
     */
    public int addScaveEditorPage(ScaveEditorPage page) {
        int index = addPage(page);
        setPageText(index, page.getPageTitle());
        return index;
    }

    /**
     * Adds a closable editor page at the last position
     */
    public int addClosableScaveEditorPage(ScaveEditorPage page) {
        int index = getPageCount();
        addClosablePage(index, page);
        setPageText(index, page.getPageTitle());
        return index;
    }

    public ScaveEditorPage getActiveEditorPage() {
        int i = getActivePage();
        if (i >= 0)
            return getEditorPage(i);
        else
            return null;
    }

    public ScaveEditorPage getEditorPage(int pageIndex) {
        Control page = getControl(pageIndex);
        if (page instanceof ScaveEditorPage)
            return (ScaveEditorPage)page;
        else
            return null;
    }

    public ChartCanvas getActiveChartCanvas() {
        ScaveEditorPage activePage = getActiveEditorPage();
        return activePage != null ? activePage.getActiveChartCanvas() : null;
    }

    /**
     * Returns the edited resource.
     */
    public Resource getResource() {
        return editingDomain.getResourceSet().getResources().get(0);
    }

    /**
     * Utility method: Returns the Analysis object from the resource.
     */
    public Analysis getAnalysis() {
        Resource resource = getResource();
        Analysis analysis = (Analysis)resource.getContents().get(0);
        return analysis;
    }

    /**
     * Opens a new editor page for the object, or switches to it if already opened.
     */
    public ScaveEditorPage open(Object object) {
        if (object instanceof Chart)
            return openChart((Chart)object);
        else
            return null;
    }

    /**
     * Opens the given chart on a new editor page, or switches to it
     * if already opened.
     */
    public ScaveEditorPage openChart(Chart chart) {
        return openClosablePage(chart);
    }

    /**
     * Opens the given <code>object</code> (dataset/chart/chartsheet), or
     * switches to it if already opened.
     */
    private ScaveEditorPage openClosablePage(EObject object) {
        int pageIndex = getOrCreateClosablePage(object);
        setActivePage(pageIndex);
        return getEditorPage(pageIndex);
    }

    /**
     * Closes the page displaying the given <code>object</code>.
     * If no such page, nothing happens.
     */
    public void closePage(EObject object) {
        Control page = closablePages.get(object);
        if (page != null) {
            removePage(page);
        }
    }

    public void showInputsPage() {
        showPage(getInputsPage());
    }

    public void showBrowseDataPage() {
        showPage(getBrowseDataPage());
    }

    public void showDatasetsPage() {
        showPage(getDatasetsPage());
    }

    public void showChartsPage() {
        showPage(getChartsPage());
    }

    public void showPage(ScaveEditorPage page) {
        int pageIndex = findPage(page);
        if (pageIndex >= 0)
            setActivePage(pageIndex);
    }

    public void gotoObject(Object object) {
        if (object instanceof EObject) {
            EObject eobject = (EObject)object;
            if (getAnalysis() == null || eobject.eResource() != getAnalysis().eResource())
                return;
        }

        ScaveEditorPage activePage = getActiveEditorPage();
        if (activePage != null) {
            if (activePage.gotoObject(object))
                return;
        }
        int activePageIndex = -1;
        for (int pageIndex = getPageCount()-1; pageIndex >= 0; --pageIndex) {
            ScaveEditorPage page = getEditorPage(pageIndex);
            if (page != null && page.gotoObject(object)) {
                activePageIndex = pageIndex;
                break;
            }
        }
        if (activePageIndex >= 0) {
            setActivePage(activePageIndex);
        }
    }


    public void setPageTitle(ScaveEditorPage page, String title) {
        int pageIndex = findPage(page);
        if (pageIndex >= 0)
            setPageText(pageIndex, title);
    }

    private void createInputsPage() {
        inputsPage = new InputsPage(getContainer(), this);
        addScaveEditorPage(inputsPage);
    }

    private void createBrowseDataPage() {
        browseDataPage = new BrowseDataPage(getContainer(), this);
        addScaveEditorPage(browseDataPage);
    }

    private void createDatasetsPage() {
        datasetsPage = new DatasetsAndChartsPage(getContainer(), this);
        addScaveEditorPage(datasetsPage);
    }

    private void createChartsPage() {
        chartsPage = new ChartsPage(getContainer(), this);
        addScaveEditorPage(chartsPage);
    }

    /**
     * Creates a closable page. These pages are closed automatically when the
     * displayed object (chart/dataset/chart sheet) is removed from the model.
     * Their tabs contain a small (x), so the user can also close them.
     */
    private int createClosablePage(EObject object) {
        ScaveEditorPage page;
        if (object instanceof Chart)
            page = new ChartPage(getContainer(), this, (Chart)object);
        else
            throw new IllegalArgumentException("Cannot create editor page for " + object);

        int pageIndex = addClosableScaveEditorPage(page);
        closablePages.put(object, page);
        return pageIndex;
    }

    @Override
    protected void pageClosed(Control control) {
        Assert.isTrue(closablePages.containsValue(control));

        // remove it from the map
        Iterator<Map.Entry<EObject,ScaveEditorPage>> entries = closablePages.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<EObject, ScaveEditorPage> entry = entries.next();
            if (control.equals(entry.getValue()))
                entries.remove();
        }
    }

    /**
     * Returns the page displaying {@code object}.
     * The {@code object} expected to be a Dataset, Chart or ChartSheet.
     */
    protected ScaveEditorPage getClosableEditorPage(EObject object) {
        return closablePages.get(object);
    }

    /**
     * Returns the page displaying <code>object</code>. If the object already has a page
     * it is returned, otherwise a new page created.
     */
    private int getOrCreateClosablePage(EObject object) {
        Control page = closablePages.get(object);
        int pageIndex = page != null ? findPage(page) : createClosablePage(object);
        Assert.isTrue(pageIndex >= 0);
        return pageIndex;
    }

    public void handleSelectionChange(ISelection selection) {
        //FIXME merge this method into fireSelectionChangedEvent()!!! that's where the guard should be as well!
        boolean selectionReallyChanged = selection!=editorSelection && !selection.equals(editorSelection);
        if (!selectionChangeInProgress || selectionReallyChanged) {
            try {
                selectionChangeInProgress = true; // "finally" ensures this gets reset in any case
                editorSelection = selection;
                 // FIXME notifying the view about IDListSelections would remove the selection from the editor!
                if (!(selection instanceof IDListSelection)) {
                    //setViewerSelectionNoNotify(contentOutlineViewer, selection);
                    updateStatusLineManager(contentOutlineStatusLineManager, selection);
                }
                updateStatusLineManager(getActionBars().getStatusLineManager(), selection);
                fireSelectionChangedEvent(selection);
            } finally {
                selectionChangeInProgress = false;
            }
        }

        inputsPage.selectionChanged(selection);
        browseDataPage.selectionChanged(selection);
        datasetsPage.selectionChanged(selection);
        chartsPage.selectionChanged(selection);
        for (Control page : closablePages.values())
            ((ScaveEditorPage)page).selectionChanged(selection);
    }

    class ScaveEditorContentOutlinePage extends ContentOutlinePage {
        public void createControl(Composite parent) {
            super.createControl(parent);
            TreeViewer viewer = getTreeViewer();
            Tree tree = viewer.getTree();
            if (tree != null) {
                tree.addSelectionListener(new SelectionAdapter () {
                    @Override
                    public void widgetDefaultSelected(SelectionEvent e) {
                        if (e.item instanceof TreeItem) {
                            TreeItem item = (TreeItem)e.item;
                            open(item.getData());
                        }
                    }
                });
            }
            
            contentOutlineViewer = getTreeViewer();
            contentOutlineViewer.addSelectionChangedListener(this);

            // Set up the tree viewer.
            contentOutlineViewer.setContentProvider(new AdapterFactoryContentProvider(adapterFactory));
            contentOutlineViewer.setLabelProvider(new ScaveModelLabelProvider(new AdapterFactoryLabelProvider(adapterFactory)));
            initializeContentOutlineViewer(contentOutlineViewer); // should call setInput()
            contentOutlineViewer.expandToLevel(3);

            // Make sure our popups work.
            createContextMenuFor(contentOutlineViewer);

            if (!editingDomain.getResourceSet().getResources().isEmpty()) {
              // Select the root object in the view.
              ArrayList<Object> selection = new ArrayList<Object>();
              selection.add(editingDomain.getResourceSet().getResources().get(0));
              contentOutlineViewer.setSelection(new StructuredSelection(selection), true);
            }
        }

        public void makeContributions(IMenuManager menuManager, IToolBarManager toolBarManager, IStatusLineManager statusLineManager) {
            super.makeContributions(menuManager, toolBarManager, statusLineManager);
            contentOutlineStatusLineManager = statusLineManager;
        }

        public void setActionBars(IActionBars actionBars) {
            super.setActionBars(actionBars);
            getActionBarContributor().shareGlobalActions(this, actionBars);
        }
    }

    public IContentOutlinePage getContentOutlinePage() {
        if (contentOutlinePage == null) {
            contentOutlinePage = new ScaveEditorContentOutlinePage();
            contentOutlinePage.addSelectionChangedListener(selectionChangedListener);
            contentOutlinePage.addSelectionChangedListener(new ISelectionChangedListener() {
                @Override
                public void selectionChanged(SelectionChangedEvent event) {
                    contentOutlineSelectionChanged(event.getSelection());
                }
            });
        }

        return contentOutlinePage;
    }

    protected void contentOutlineSelectionChanged(ISelection selection) {
        if (selection instanceof IStructuredSelection) {
            Object object = ((IStructuredSelection)selection).getFirstElement();
            //Debug.println("Selected: "+object);
            if (object != null)
                gotoObject(object);
        }
    }

    /**
     * Adds the given workspace file to Inputs.
     */
    public void addWorkspaceFileToInputs(IFile resource) {
        String resourcePath = resource.getFullPath().toPortableString();

        // add resourcePath to Inputs if not already there
        Inputs inputs = getAnalysis().getInputs();
        boolean found = false;
        for (Object inputFileObj : inputs.getInputs()) {
            InputFile inputFile = (InputFile)inputFileObj;
            if (inputFile.getName().equals(resourcePath))
                found = true;
        }

        if (!found) {
            // use the EMF.Edit Framework's command interface to do the job (undoable)
            InputFile inputFile = ScaveModelFactory.eINSTANCE.createInputFile();
            inputFile.setName(resourcePath);
            Command command = AddCommand.create(getEditingDomain(), inputs, pkg.getInputs_Inputs(), inputFile);
            executeCommand(command);
        }
    }

    /**
     * Utility function: finds an IFile for an existing file given with OS path. Returns null if the file was not found.
     */
    public static IFile findFileInWorkspace(String fileName) {
        IFile[] iFiles = ResourcesPlugin.getWorkspace().getRoot().findFilesForLocation(new Path(fileName));
        IFile iFile = null;
        for (IFile f : iFiles) {
            if (f.exists()) {
                iFile = f;
                break;
            }
        }
        return iFile;
    }

    /**
     * Utility function to access the active editor in the workbench.
     */
    public static ScaveEditor getActiveScaveEditor(IWorkbench workbench) {
        if (workbench.getActiveWorkbenchWindow() != null) {
            IWorkbenchPage page = workbench.getActiveWorkbenchWindow().getActivePage();
            if (page != null) {
                IEditorPart part = page.getActiveEditor();
                if (part instanceof ScaveEditor)
                    return (ScaveEditor)part;
            }
        }
        return null;
    }

    /**
     * Utility method.
     */
    public void executeCommand(Command command) {
        getEditingDomain().getCommandStack().execute(command);
    }

    public ISelectionChangedListener getSelectionChangedListener() {
        return selectionChangedListener;
    }

    /**
     * Updates the pages.
     * Registered as a listener on model changes.
     */
    @SuppressWarnings("unchecked")
    private void updatePages(Notification notification) {
        if (notification.isTouch())
            return;

        // close pages whose content was deleted, except temporary datasets/charts
        // (temporary objects are not deleted, but they can be moved into the persistent analysis)
        if (notification.getNotifier() instanceof EObject) {
            List<Object> deletedObjects = null;
            switch (notification.getEventType()) {
            case Notification.REMOVE:
                deletedObjects = new ArrayList<Object>();
                deletedObjects.add(notification.getOldValue());
                break;
            case Notification.REMOVE_MANY:
                deletedObjects = (List<Object>)notification.getOldValue();
                break;
            }

            if (deletedObjects != null) {
                for (Object object : deletedObjects) {
                    if (object instanceof EObject) {
                        TreeIterator<EObject> contents = ((EObject)object).eAllContents();
                        // iterate on contents including object
                        for (Object next = object; next != null; next = contents.hasNext() ? contents.next() : null) {
                            if (next instanceof Chart) {
                                closePage((Chart)next);
                                contents.prune();
                            }
                        }
                    }
                }
            }
        }

        // update contents of pages
        int pageCount = getPageCount();
        for (int pageIndex = 0; pageIndex < pageCount; ++pageIndex) {
            Control control = getControl(pageIndex);
            if (control instanceof ScaveEditorPage) {
                ScaveEditorPage page = (ScaveEditorPage)control;
                page.updatePage(notification);
            }
        }
    }

    @Override
    protected void pageChange(int newPageIndex) {
        super.pageChange(newPageIndex);
        Control page = getControl(newPageIndex);
        if (page instanceof ScaveEditorPage) {
            ((ScaveEditorPage)page).pageActivated();
        }

        fakeSelectionChange();
    }

    /**
     * Pretends that a selection change has taken place. This is e.g. useful for updating
     * the enabled/disabled/pushed etc state of actions (AbstractScaveAction) whose
     * isApplicable() method is hooked on selection changes.
     */
    public void fakeSelectionChange() {
        //TODO setSelection(getSelection());
    }

    /*
     * PageId
     */
    String getPageId(ScaveEditorPage page) {
        if (page == null)
            return null;
        else if (page.equals(inputsPage))
            return "Inputs";
        else if (page.equals(browseDataPage))
            return "BrowseData";
        else if (page.equals(datasetsPage))
            return "Datasets";
        else if (page.equals(chartsPage))
            return "Charts";
        else {
            for (Map.Entry<EObject, ScaveEditorPage> entry : closablePages.entrySet()) {
                EObject object = entry.getKey();
                ScaveEditorPage editorPage = entry.getValue();
                if (page.equals(editorPage)) {
                    Resource resource = object.eResource();
                    String uri = resource != null ? resource.getURIFragment(object) : null;
                    return uri != null ? uri : null;
                }
            }
        }
        return null;
    }

    ScaveEditorPage restorePage(String pageId) {
        if (pageId == null)
            return null;
        if (pageId.equals("Inputs")) {
            setActivePage(findPage(inputsPage));
            return inputsPage;
        }
        else if (pageId.equals("BrowseData")) {
            setActivePage(findPage(browseDataPage));
            return browseDataPage;
        }
        else if (pageId.equals("Datasets")) {
            setActivePage(findPage(datasetsPage));
            return datasetsPage;
        }
        else if (pageId.equals("Charts")) {
            setActivePage(findPage(chartsPage));
            return chartsPage;
        }
        else {
            EObject object = null;
            String uri = null;
            Resource resource = null;
            uri = pageId;
            resource = getResource();

            try {
                if (resource != null && uri != null)
                    object = resource.getEObject(uri);
            } catch (Exception e) {}

            if (object != null)
                return open(object);
        }
        return null;
    }

    /*
     * Per input persistent state.
     */
    private IFile getInputFile() {
        IEditorInput input = getEditorInput();
        if (input instanceof IFileEditorInput)
            return ((IFileEditorInput)input).getFile();
        else
            return null;
    }

    private void saveState(IMemento memento) {
        memento.putInteger(ACTIVE_PAGE, getActivePage());
        for (EObject openedObject : closablePages.keySet()) {
            ScaveEditorPage page = closablePages.get(openedObject);
            IMemento pageMemento = memento.createChild(PAGE);
            pageMemento.putString(PAGE_ID, getPageId(page));
            page.saveState(pageMemento);
        }
    }

    private void restoreState(IMemento memento) {
        for (IMemento pageMemento : memento.getChildren(PAGE)) {
            String pageId = pageMemento.getString(PAGE_ID);
            if (pageId != null) {
                ScaveEditorPage page = restorePage(pageId);
                    if (page != null)
                        page.restoreState(pageMemento);
            }
        }
        int activePage = memento.getInteger(ACTIVE_PAGE);
        if (activePage >= 0 && activePage < getPageCount())
            setActivePage(activePage);
    }

    private void saveState() {
        try {
            IFile file = getInputFile();
            if (file != null) {
                ScaveEditorMemento memento = new ScaveEditorMemento();
                saveState(memento);
                memento.save(file);
            }
        } catch (Exception e) {
//          MessageDialog.openError(getSite().getShell(),
//                                  "Saving editor state",
//                                  "Error occured while saving editor state: "+e.getMessage());
            ScavePlugin.logError(e);
        }
    }

    private void restoreState() {
        try {
            IFile file = getInputFile();
            if (file != null) {
                ScaveEditorMemento memento = new ScaveEditorMemento(file);
                restoreState(memento);
            }
        }
        catch (CoreException e) {
            ScavePlugin.log(e.getStatus());
        }
        catch (Exception e) {
//          MessageDialog.openError(getSite().getShell(),
//                  "Restoring editor state",
//                  "Error occured while restoring editor state: "+e.getMessage());
            ScavePlugin.logError(e);
        }
    }

    /*
     * Navigation
     */
    @Override
    public INavigationLocation createEmptyNavigationLocation() {
        return new ScaveNavigationLocation(this, true);
    }

    @Override
    public INavigationLocation createNavigationLocation() {
        return new ScaveNavigationLocation(this, false);
    }

    public void markNavigationLocation() {
        getSite().getPage().getNavigationHistory().markLocation(this);
    }

    public void pageChangedByUser(int newPageIndex) {
        Control page = getControl(newPageIndex);
        if (page instanceof ScaveEditorPage) {
            markNavigationLocation();
        }
    }

    /*
     * IGotoMarker
     */
    @Override
    public void gotoMarker(IMarker marker) {
        try {
            if (marker.getType().equals(Markers.COMPUTESCALAR_PROBLEMMARKER_ID)) {
                Object object = marker.getAttribute(Markers.EOBJECT_MARKERATTR_ID);
                if (object instanceof EObject && datasetsPage != null) {
                    gotoObject(object);
                    setSelectionToViewer(Collections.singleton(editingDomain.getWrapper(object)));
                }
            }
            else {
                List<?> targetObjects = markerHelper.getTargetObjects(editingDomain, marker);
                if (!targetObjects.isEmpty())
                    setSelectionToViewer(targetObjects);
            }
        }
        catch (CoreException exception) {
            ScavePlugin.logError(exception);
        }
    }
    
    protected void handleActivate() {
    }

    /**
     * This is here for the listener to be able to call it.
     */
    protected void firePropertyChange(int action) {
        super.firePropertyChange(action);
    }

    /**
     * This sets the selection into whichever viewer is active.
     */
    public void setSelectionToViewer(Collection<?> collection) {
        handleSelectionChange(new StructuredSelection(collection.toArray()));
    }

    /**
     * Utility function to update selection in a viewer without generating
     * further notifications.
     */
    public void setViewerSelectionNoNotify(Viewer target, ISelection selection) {
        if (target!=null) {
            target.removeSelectionChangedListener(selectionChangedListener);
            target.setSelection(selection,true);
            target.addSelectionChangedListener(selectionChangedListener);
        }
    }

    /**
     * Notify listeners on {@link org.eclipse.jface.viewers.ISelectionProvider} about a selection change.
     */
    protected void fireSelectionChangedEvent(ISelection selection) {
        for (ISelectionChangedListener listener : selectionChangedListeners)
            listener.selectionChanged(new SelectionChangedEvent(this, selection));
    }

    /**
     * This returns the editing domain as required by the {@link IEditingDomainProvider} interface.
     * This is important for implementing the static methods of {@link AdapterFactoryEditingDomain}
     * and for supporting {@link org.eclipse.emf.edit.ui.action.CommandAction}.
     */
    public EditingDomain getEditingDomain() {
        return editingDomain;
    }


    /**
     * This creates a context menu for the viewer and adds a listener as well registering the menu for extension.
     */
    protected void createContextMenuFor(StructuredViewer viewer) {
        createContextMenuFor(viewer.getControl());
    }
    
    protected void createContextMenuFor(Control control) {
        MenuManager contextMenu = new MenuManager("#PopUp");
        contextMenu.add(new Separator("additions"));
        contextMenu.setRemoveAllWhenShown(true);
        contextMenu.addMenuListener(this);
        Menu menu = contextMenu.createContextMenu(control);
        control.setMenu(menu);

        // Note: don't register the context menu, otherwise "Run As", "Debug As", "Team", and other irrelevant menu items appear...
        //getSite().registerContextMenu(contextMenu, viewer);
    }

    /**
     * Returns a diagnostic describing the errors and warnings listed in the resource
     * and the specified exception (if any).
     */
    public Diagnostic analyzeResourceProblems(Resource resource, Exception exception) {
        if (!resource.getErrors().isEmpty() || !resource.getWarnings().isEmpty()) {
            BasicDiagnostic basicDiagnostic = new BasicDiagnostic(Diagnostic.ERROR, "org.omnetpp.scave.model", 0, "Could not create model: " + resource.getURI(),
                    new Object [] { exception == null ? (Object)resource : exception });
            basicDiagnostic.merge(EcoreUtil.computeDiagnostic(resource, true));
            return basicDiagnostic;
        }
        else if (exception != null) {
            return new BasicDiagnostic(Diagnostic.ERROR, "org.omnetpp.scave.model", 0, "Could not create model: " + resource.getURI(), new Object[] { exception });
        }
        else {
            return Diagnostic.OK_INSTANCE;
        }
    }


    /**
     * This is the method used by the framework to install your own controls.
     */
    public void createPages() {
        createModel();
        doCreatePages();
    }

    public void configureTreeViewer(final TreeViewer modelViewer) {
        ILabelProvider labelProvider =
                new DecoratingLabelProvider(
                        new ScaveModelLabelProvider(new AdapterFactoryLabelProvider(adapterFactory)),
                        new ScaveModelLabelDecorator());
        modelViewer.setContentProvider(new AdapterFactoryContentProvider(adapterFactory));
        modelViewer.setLabelProvider(labelProvider);
        modelViewer.setAutoExpandLevel(TreeViewer.ALL_LEVELS);
        // new AdapterFactoryTreeEditor(modelViewer.getTree(), adapterFactory); //XXX this appears to be something about in-place editing - do we need it?

        modelViewer.addSelectionChangedListener(selectionChangedListener);

        createContextMenuFor(modelViewer);
        setupDragAndDropSupportFor(modelViewer);

        // on double-click, open (the dataset or chart), or bring up the Properties dialog
        modelViewer.getTree().addSelectionListener(new SelectionAdapter() {
            public void widgetDefaultSelected(SelectionEvent e) {
                ScaveEditorContributor contributor = ScaveEditorContributor.getDefault();
                if (contributor != null) {
                    if (contributor.getOpenAction().isEnabled())
                        contributor.getOpenAction().run();
                    else if (contributor.getEditAction().isEnabled())
                        contributor.getEditAction().run();
                }
            }
        });

        new HoverSupport().adapt(modelViewer.getTree(), new IHoverInfoProvider() {
            public HtmlHoverInfo getHoverFor(Control control, int x, int y) {
                Item item = modelViewer.getTree().getItem(new Point(x,y));
                Object element = item==null ? null : item.getData();
                if (element != null && modelViewer.getLabelProvider() instanceof DecoratingLabelProvider) {
                    ILabelProvider labelProvider = ((DecoratingLabelProvider)modelViewer.getLabelProvider()).getLabelProvider();
                    if (labelProvider instanceof ScaveModelLabelProvider) {
                        ScaveModelLabelProvider scaveLabelProvider = (ScaveModelLabelProvider)labelProvider;
                        return new HtmlHoverInfo(HoverSupport.addHTMLStyleSheet(scaveLabelProvider.getTooltipText(element)));
                    }
                }
                return null;
            }
        });
    }

    /**
     * Utility class to add content and label providers, context menu etc to a TreeViewer
     * that is used to edit the model.
     */
    public void configureIconGridViewer(IconGridViewer modelViewer) {
//        ILabelProvider labelProvider =
//            new DecoratingLabelProvider(
//                new ScaveModelLabelProvider(new AdapterFactoryLabelProvider(adapterFactory)),
//                new ScaveModelLabelDecorator());

        ILabelProvider labelProvider = new LabelProvider() {
            @Override
            public Image getImage(Object element) {
                if (element instanceof LineChart)
                    return ScavePlugin.getImage("icons/full/obj/linechart.png");
                else if (element instanceof BarChart)
                    return ScavePlugin.getImage("icons/full/obj/barchart.png");
                else if (element instanceof ScatterChart)
                    return ScavePlugin.getImage("icons/full/obj/scatterchart.png");
                else if (element instanceof HistogramChart)
                    return ScavePlugin.getImage("icons/full/obj/histogramchart.png");
                else 
                    return null;
            }
            
            @Override
            public String getText(Object element) {
                if (element instanceof Chart)
                    return StringUtils.defaultIfBlank(((Chart) element).getName(), "<unnamed>");
                else 
                    return element == null ? "" : element.toString();
            }
        };
        
        modelViewer.setContentProvider(new AdapterFactoryContentProvider(adapterFactory));
        modelViewer.setLabelProvider(labelProvider);
//        modelViewer.setAutoExpandLevel(TreeViewer.ALL_LEVELS);
        // new AdapterFactoryTreeEditor(modelViewer.getTree(), adapterFactory); //XXX this appears to be something about in-place editing - do we need it?

        modelViewer.addSelectionChangedListener(selectionChangedListener);

        createContextMenuFor(modelViewer.getControl());
        createContextMenuFor(modelViewer.getCanvas());
//        setupDragAndDropSupportFor(modelViewer);

        // on double-click, open (the dataset or chart), or bring up the Properties dialog
        modelViewer.addDoubleClickListener(new IDoubleClickListener() {
            @Override
            public void doubleClick(DoubleClickEvent event) {
                ScaveEditorContributor contributor = ScaveEditorContributor.getDefault();
                if (contributor != null) {
                    if (contributor.getOpenAction().isEnabled())
                        contributor.getOpenAction().run();
                    else if (contributor.getEditAction().isEnabled())
                        contributor.getEditAction().run();
                }
            }
        });
//
//        new HoverSupport().adapt(modelViewer.getTree(), new IHoverInfoProvider() {
//            @Override
//            public HtmlHoverInfo getHoverFor(Control control, int x, int y) {
//                Item item = modelViewer.getTree().getItem(new Point(x,y));
//                Object element = item==null ? null : item.getData();
//                if (element != null && modelViewer.getLabelProvider() instanceof DecoratingLabelProvider) {
//                    ILabelProvider labelProvider = ((DecoratingLabelProvider)modelViewer.getLabelProvider()).getLabelProvider();
//                    if (labelProvider instanceof ScaveModelLabelProvider) {
//                        ScaveModelLabelProvider scaveLabelProvider = (ScaveModelLabelProvider)labelProvider;
//                        return new HtmlHoverInfo(HoverSupport.addHTMLStyleSheet(scaveLabelProvider.getTooltipText(element)));
//                    }
//                }
//                return null;
//            }
//        });
    }

    /**
     * This is how the framework determines which interfaces we implement.
     */
    @SuppressWarnings("unchecked")
    public <T> T getAdapter(Class<T> key) {
        if (key.equals(IContentOutlinePage.class))
            return showOutlineView() ? (T)getContentOutlinePage() : null;
        else if (key.equals(IPropertySheetPage.class))
            return (T)getPropertySheetPage();
        else if (key.equals(IGotoMarker.class))
            return (T)this;
        else
            return super.getAdapter(key);
    }

    /**
     * This is for implementing {@link IEditorPart} and simply tests the command stack.
     */
    public boolean isDirty() {
        return ((BasicCommandStack)editingDomain.getCommandStack()).isSaveNeeded();
    }

    /**
     * This is for implementing {@link IEditorPart} and simply saves the model file.
     */
    public void doSave(IProgressMonitor progressMonitor) {
        // Save only resources that have actually changed.
        //
        final Map<Object, Object> saveOptions = new HashMap<Object, Object>();
        saveOptions.put(Resource.OPTION_SAVE_ONLY_IF_CHANGED, Resource.OPTION_SAVE_ONLY_IF_CHANGED_MEMORY_BUFFER);
        saveOptions.put(Resource.OPTION_LINE_DELIMITER, Resource.OPTION_LINE_DELIMITER_UNSPECIFIED);

        // Do the work within an operation because this is a long running activity that modifies the workbench.
        WorkspaceModifyOperation operation =
            new WorkspaceModifyOperation() {
                // This is the method that gets invoked when the operation runs.
                public void execute(IProgressMonitor monitor) {
                    // Save the resources to the file system.
                    EList<Resource> resources = editingDomain.getResourceSet().getResources();
                    Assert.isTrue(resources.size() == 1);
                    Resource resource = resources.get(0);
                    if (!editingDomain.isReadOnly(resource)) {
                        try {
                            resource.save(saveOptions);
                        }
                        catch (Exception e) {
                            ScavePlugin.logError("Could not save resource", e);
                        }
                    }
                }
            };

        try {
            // This runs the options, and shows progress.
            new ProgressMonitorDialog(getSite().getShell()).run(true, false, operation);

            // Refresh the necessary state.
            ((BasicCommandStack)editingDomain.getCommandStack()).saveIsDone();
            firePropertyChange(IEditorPart.PROP_DIRTY);
        }
        catch (Exception exception) {
            ScaveEditPlugin.INSTANCE.log(exception);
        }
    }

    protected boolean isSaveable(Resource resource) {
        return true;
    }

    /**
     * This always returns true because it is not currently supported.
     */
    public boolean isSaveAsAllowed() {
        return true;
    }

    /**
     * "Save As" also changes the editor's input.
     */
    public void doSaveAs() {
        SaveAsDialog saveAsDialog= new SaveAsDialog(getSite().getShell());
        saveAsDialog.open();
        IPath path= saveAsDialog.getResult();
        if (path != null) {
            IFile file = ResourcesPlugin.getWorkspace().getRoot().getFile(path);
            if (file != null)
                doSaveAs(URI.createPlatformResourceURI(file.getFullPath().toString(), true), new FileEditorInput(file));
        }
    }

    /**
     * Perform "Save As"
     */
    protected void doSaveAs(URI uri, IEditorInput editorInput) {
        editingDomain.getResourceSet().getResources().get(0).setURI(uri);
        setInputWithNotify(editorInput);
        setPartName(editorInput.getName());
        IProgressMonitor progressMonitor =
            getActionBars().getStatusLineManager() != null ?
                getActionBars().getStatusLineManager().getProgressMonitor() :
                new NullProgressMonitor();
        doSave(progressMonitor);
    }

    /**
     * This implements {@link org.eclipse.jface.viewers.ISelectionProvider}.
     */
    public void addSelectionChangedListener(ISelectionChangedListener listener) {
        selectionChangedListeners.add(listener);
    }

    /**
     * This implements {@link org.eclipse.jface.viewers.ISelectionProvider}.
     */
    public void removeSelectionChangedListener(ISelectionChangedListener listener) {
        selectionChangedListeners.remove(listener);
    }

    /**
     * This implements {@link org.eclipse.jface.viewers.ISelectionProvider} to return this editor's overall selection.
     */
    public ISelection getSelection() {
        return editorSelection;
    }

    /**
     * This implements {@link org.eclipse.jface.viewers.ISelectionProvider} to set this editor's overall selection.
     * Calling this will result in notifing the listeners.
     */
    public void setSelection(ISelection selection) {
        handleSelectionChange(selection);
    }

    /**
     * Utility method to update "Selected X objects" text on the status bar.
     */
    protected void updateStatusLineManager(IStatusLineManager statusLineManager, ISelection selection) {
        if (statusLineManager != null) {
            if (selection instanceof IDListSelection) {
                IDListSelection idlistSelection = (IDListSelection)selection;
                int scalars = idlistSelection.getScalarsCount();
                int vectors = idlistSelection.getVectorsCount();
                int statistics = idlistSelection.getStatisticsCount();
                int histograms = idlistSelection.getHistogramsCount();
                if (scalars + vectors + statistics + histograms == 0)
                    statusLineManager.setMessage("No item selected");
                else {
                    List<String> strings = new ArrayList<String>(3);
                    if (scalars > 0) strings.add(StringUtils.formatCounted(scalars, "scalar"));
                    if (vectors > 0) strings.add(StringUtils.formatCounted(vectors, "vector"));
                    if (statistics > 0) strings.add(StringUtils.formatCounted(statistics, "statistics"));
                    if (histograms > 0) strings.add(StringUtils.formatCounted(histograms, "histogram"));
                    String message = "Selected " + StringUtils.join(strings, ", ", " and ");
                    statusLineManager.setMessage(message);
                }
            }
            else if (selection instanceof IStructuredSelection) {
                Collection<?> collection = ((IStructuredSelection)selection).toList();
                if (collection.size()==0) {
                    statusLineManager.setMessage("No item selected");
                }
                else if (collection.size()==1) {
                    Object object = collection.iterator().next();
                     // XXX unify label providers
                    String text = new InputsViewLabelProvider().getText(object);
                    if (text == null)
                        text = new AdapterFactoryItemDelegator(adapterFactory).getText(object);
                    statusLineManager.setMessage("Selected: " + text);
                }
                else {
                        statusLineManager.setMessage("" + collection.size() + " items selected");
                }
            }
            else {
                statusLineManager.setMessage("");
            }
        }
    }

    /**
     * This implements {@link org.eclipse.jface.action.IMenuListener} to help fill the context menus with contributions from the Edit menu.
     */
    public void menuAboutToShow(IMenuManager menuManager) {
        ((IMenuListener)getEditorSite().getActionBarContributor()).menuAboutToShow(menuManager);
    }

    public ScaveEditorContributor getActionBarContributor() {
        if (getEditorSite() != null)
            return (ScaveEditorContributor)getEditorSite().getActionBarContributor();
        else
            return null;
    }

    public IActionBars getActionBars() {
        return getActionBarContributor().getActionBars();
    }

    public AdapterFactory getAdapterFactory() {
        return adapterFactory;
    }

    protected boolean showOutlineView() {
        return true;
    }
    
}
