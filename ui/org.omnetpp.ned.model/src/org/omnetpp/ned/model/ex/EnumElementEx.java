/*--------------------------------------------------------------*
  Copyright (C) 2006-2015 OpenSim Ltd.

  This file is distributed WITHOUT ANY WARRANTY. See the file
  'License' for details on this and other legal matters.
*--------------------------------------------------------------*/

package org.omnetpp.ned.model.ex;

import java.util.Map;
import java.util.Set;

import org.omnetpp.ned.model.INedElement;
import org.omnetpp.ned.model.interfaces.IMsgTypeElement;
import org.omnetpp.ned.model.interfaces.IMsgTypeInfo;
import org.omnetpp.ned.model.interfaces.ITypeElement;
import org.omnetpp.ned.model.pojo.EnumElement;

public class EnumElementEx extends EnumElement implements IMsgTypeElement {
    private IMsgTypeInfo typeInfo;

    protected EnumElementEx() {
        super();
    }

    protected EnumElementEx(INedElement parent) {
        super(parent);
    }

    public IMsgTypeInfo getMsgTypeInfo() {
        if (typeInfo == null)
            typeInfo = getDefaultMsgTypeResolver().createTypeInfoFor(this);

        return typeInfo;
    }

    public String getFirstExtends() {
        return null; // "enum Foo extends Bar" is not an accepted syntax
    }

    public ITypeElement getSuperType() {
        return getMsgTypeInfo().getSuperType();
    }

    public Set<IMsgTypeElement> getLocalUsedTypes() {
        return getMsgTypeInfo().getLocalUsedTypes();
    }

    public Map<String, Map<String, PropertyElementEx>> getProperties() {
        return getMsgTypeInfo().getProperties();
    }
}
