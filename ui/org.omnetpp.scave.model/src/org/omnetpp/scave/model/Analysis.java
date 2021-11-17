/*--------------------------------------------------------------*
  Copyright (C) 2006-2020 OpenSim Ltd.

  This file is distributed WITHOUT ANY WARRANTY. See the file
  'License' for details on this and other legal matters.
*--------------------------------------------------------------*/

package org.omnetpp.scave.model;

import java.util.HashSet;

public class Analysis extends ModelObject {
    protected Inputs inputs = new Inputs();
    protected Folder rootFolder = new Folder();

    public Analysis() {
        inputs.parent = this;
        rootFolder.parent = this;
    }

    public Inputs getInputs() {
        return inputs;
    }

    public void setInputs(Inputs inputs) {
        this.inputs.parent = null;
        this.inputs = inputs;
        this.inputs.parent = this;
        notifyListeners();
    }

    public Folder getRootFolder() {
        return rootFolder;
    }

    public void setRootFolder(Folder folder) {
        this.rootFolder.parent = null;
        this.rootFolder = folder;
        this.rootFolder.parent = this;
        notifyListeners();
    }

    public boolean contains(AnalysisItem item) {
        return rootFolder.contains(item);
    }

    public void checkIdUniqueness() {
        checkIdUniqueness(rootFolder, new HashSet<Integer>());
    }

    private static void checkIdUniqueness(AnalysisItem item, HashSet<Integer> idsSeen) {
        if (idsSeen.contains(item.getId()))
            throw new RuntimeException("id=" + item.getId() + " of "  + item.toString() + " not unique");
        idsSeen.add(item.getId());
        if (item instanceof Folder)
            for (AnalysisItem child : ((Folder)item).getItems())
                checkIdUniqueness(child, idsSeen);
    }

    @Override
    public Analysis clone() throws CloneNotSupportedException {
        Analysis clone = (Analysis)super.clone();
        clone.inputs = inputs.clone();
        clone.rootFolder = rootFolder.clone();
        return clone;
    }
}

