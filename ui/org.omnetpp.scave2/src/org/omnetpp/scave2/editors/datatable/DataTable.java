package org.omnetpp.scave2.editors.datatable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.ListenerList;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.omnetpp.scave.engine.IDList;
import org.omnetpp.scave.engine.ResultItem;
import org.omnetpp.scave.engine.ScalarResult;
import org.omnetpp.scave.engine.VectorResult;
import org.omnetpp.scave.engineext.ResultFileManagerEx;
import org.omnetpp.scave.model.DatasetType;
import org.omnetpp.scave2.model.RunAttribute;

/**
 * This is a preconfigured VIRTUAL table, which displays a list of
 * output vectors, output scalars or histograms, given an IDList and
 * the corresponding ResultFileManager as input. It is optimized
 * for very large amounts of data. (Display time is constant,
 * so it can be used with even millions of table lines without
 * performance degradation).
 * 
 * The user is responsible to keep contents up-to-date in case
 * ResultFileManager or IDList contents change. Refreshing can be
 * done either by a call to setIDList(), or by refresh().
 *  
 * @author andras
 */
public class DataTable extends Table {
	public static final int TYPE_SCALAR = 0;
	public static final int TYPE_VECTOR = 1;
	public static final int TYPE_HISTOGRAM = 2;
	
	static class Column {
		private String text;
		private int weight;
		private boolean visible;
		
		public Column(String text, int weight, boolean visible) {
			this.text = text;
			this.weight = weight;
			this.visible = visible;
		}
		
		public Column clone() {
			return new Column(this.text, this.weight, this.visible);
		}
		
		public boolean equals(Object other) {
			return other instanceof Column && this.text.equals(((Column)other).text);
		}
		
		public int hashCode() {
			return text.hashCode();
		}
	}
	
	private static final Column COL_DIRECTORY = new Column("Directory", 60, true);
	private static final Column COL_FILE_RUN = new Column("File name#run number", 100, true);
	private static final Column COL_RUN_ID = new Column("Run id", 100, true);
	private static final Column COL_MODULE = new Column("Module name", 160, true);
	private static final Column COL_DATA = new Column("Data name", 100, true);
	private static final Column COL_VALUE = new Column("Value", 80, true);
	private static final Column COL_COUNT = new Column("Count", 50, true);
	private static final Column COL_MEAN = new Column("Mean", 60, true);
	private static final Column COL_STDDEV = new Column("Stddev", 60, true);
	private static final Column COL_EXPERIMENT = new Column("Experiment", 60, false);
	private static final Column COL_MEASUREMENT = new Column("Measurement", 60, false);
	private static final Column COL_REPLICATION = new Column("Replication", 60, false);
	
	private int type;
	private ResultFileManagerEx manager;
	private IDList idlist;
	private ListenerList listeners;
	private List<Column> columns;
	
	public DataTable(Composite parent, int style, int type) {
		super(parent, style | SWT.VIRTUAL | SWT.FULL_SELECTION);
		Assert.isTrue(type==TYPE_SCALAR || type==TYPE_VECTOR || type==TYPE_HISTOGRAM);
		this.type = type;
		setHeaderVisible(true);
		setLinesVisible(true);
		initColumns();

		addListener(SWT.SetData, new Listener() {
			public void handleEvent(Event e) {
				TableItem item = (TableItem)e.item;
				int lineNumber = indexOf(item);
				fillTableLine(item, lineNumber);
			}
		});
	}

	/**
	 * Override the ban on subclassing of Table, after having read the doc of 
	 * checkSubclass(). In this class we only build upon the public interface
	 * of Table, so there can be no unwanted side effects. We prefer subclassing
	 * to delegating all 1,000,000 Table methods to an internal Table instance.    
	 */
	@Override
	protected void checkSubclass() {
	}
	
	public int getType() {
		return type;
	}
	
	public DatasetType getDataType() {
		switch (type) {
		case DataTable.TYPE_SCALAR: return DatasetType.SCALAR_LITERAL;
		case DataTable.TYPE_VECTOR: return DatasetType.VECTOR_LITERAL;
		case DataTable.TYPE_HISTOGRAM: return DatasetType.HISTOGRAM_LITERAL;
		}
		return null; // XXX
	}
	

	public void setResultFileManager(ResultFileManagerEx manager) {
		this.manager = manager;
	}
	
	public ResultFileManagerEx getResultFileManager() {
		return manager;
	}

	public void setIDList(IDList idlist) {
		this.idlist = idlist;
		refresh();
		fireContentChangedEvent();
	}

	public IDList getIDList() {
		return idlist;
	}
	
	public String[] getColumnNames() {
		String[] columnNames = new String[columns.size()];
		for (int i = 0; i < columns.size(); ++i)
			columnNames[i] = columns.get(i).text;
		return columnNames;
	}
	
	public boolean isColumnVisible(int index) {
		return columns.get(index).visible;
	}
	
	public void setColumnVisible(int index, boolean visible) {
		if (visible != columns.get(index).visible) {
			columns.get(index).visible = visible;
			layoutColumns();
		}
	}
	
	public ResultItem[] getSelectedItems() {
		int[] selectionIndeces = getSelectionIndices();
		ResultItem[] items = new ResultItem[selectionIndeces.length];
		
		for (int i = 0; i < items.length; ++i) {
			items[i] = manager.getItem(idlist.get(selectionIndeces[i]));
		}
		
		return items;
	}

	public void refresh() {
		setItemCount((int)idlist.size());
		clearAll();
	}
	
	protected void initColumns() {
		columns = new ArrayList<Column>();
		addColumn(COL_DIRECTORY);
		addColumn(COL_FILE_RUN);
		addColumn(COL_RUN_ID);
		addColumn(COL_EXPERIMENT);
		addColumn(COL_MEASUREMENT);
		addColumn(COL_REPLICATION);
		addColumn(COL_MODULE);
		addColumn(COL_DATA);
		
		switch (type) {
		case TYPE_SCALAR:
			addColumn(COL_VALUE);
			break;
		case TYPE_VECTOR:
			addColumn(COL_COUNT);
			addColumn(COL_MEAN);
			addColumn(COL_STDDEV);
			break;
		case TYPE_HISTOGRAM:
			// TODO
			break;
		default:
			Assert.isTrue(false, "Unknown data type: " + type);
			break;
		}
	}
	
	protected void addColumn(Column column) {
		columns.add(column.clone());
		TableColumn tableColumn = new TableColumn(this, SWT.NONE);
		tableColumn.setText(column.text);
		tableColumn.setWidth(column.visible ? column.weight : 0);
	}
	
	protected void layoutColumns() {
		double unit = getWidthUnit();
		for (int i = 0; i < columns.size(); ++i) {
			Column column = columns.get(i);
			getColumn(i).setWidth(column.visible ? (int)(column.weight * unit) : 0);
		}
	}

	private double getWidthUnit() {
		int sumOfWeights = 0;
		for (Column column : columns)
			sumOfWeights += (column.visible ? column.weight : 0);

		return (double)getSize().x / sumOfWeights;
	}
	
	protected void fillTableLine(TableItem item, int lineNumber) {
		ResultItem result = manager.getItem(idlist.get(lineNumber));
		
		for (int i = 0; i < columns.size(); ++i) {
			Column column = columns.get(i);
			if (COL_DIRECTORY.equals(column))
				item.setText(i, result.getFileRun().getFile().getDirectory());
			else if (COL_FILE_RUN.equals(column)) {
				String fileName = result.getFileRun().getFile().getFileName();
				int runNumber = result.getFileRun().getRun().getRunNumber();
				item.setText(i, runNumber == 0 ? fileName : fileName + "#" + runNumber);
			}
			else if (COL_RUN_ID.equals(column))
				item.setText(i, result.getFileRun().getRun().getRunName());
			else if (COL_MODULE.equals(column))
				item.setText(i, result.getModuleName());
			else if (COL_DATA.equals(column))
				item.setText(i, result.getName());
			else if (COL_EXPERIMENT.equals(column)) {
				String experiment = result.getFileRun().getRun().getAttribute(RunAttribute.EXPERIMENT);
				item.setText(i, experiment != null ? experiment : "n.a.");
			}
			else if (COL_MEASUREMENT.equals(column)) {
				String measurement = result.getFileRun().getRun().getAttribute(RunAttribute.MEASUREMENT);
				item.setText(i, measurement != null ? measurement : "n.a.");
			}
			else if (COL_REPLICATION.equals(column)) {
				String replication = result.getFileRun().getRun().getAttribute(RunAttribute.REPLICATION);
				item.setText(i, replication != null ? replication : "n.a.");
			}
			else if (type == TYPE_SCALAR) {
				ScalarResult scalar = manager.getScalar(idlist.get(lineNumber));
				if (COL_VALUE.equals(column))
					item.setText(i, String.valueOf(scalar.getValue()));
			}
			else if (type == TYPE_VECTOR) {
				VectorResult vector = manager.getVector(idlist.get(lineNumber));
				if (COL_COUNT.equals(column))
					item.setText(i, "not counted");
				else if (COL_MEAN.equals(column))
					item.setText(i, "not yet");
				else if (COL_STDDEV.equals(column))
					item.setText(i, "not yet");
			}
			else if (type == TYPE_HISTOGRAM) {
				// TODO
			}
		}
	}
	
	public void addDataTableListener(IDataTableListener listener) {
		if (listeners == null)
			listeners = new ListenerList();
		listeners.add(listener);
	}
	
	public void removeDataTableListener(IDataTableListener listener) {
		if (listeners != null)
			listeners.remove(listener);
	}
	
	protected void fireContentChangedEvent() {
		if (listeners != null) {
			for (Object listener : new ArrayList(Arrays.asList(this.listeners.getListeners())))
				((IDataTableListener)listener).contentChanged(this);
		}
	}
}
