package org.omnetpp.eventlog.entry;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;

import org.omnetpp.eventlog.IChunk;
public class CustomCreatedEntry extends CustomDescriptionEntry
{

    public CustomCreatedEntry() {
    }

    public CustomCreatedEntry(IChunk chunk, int entryIndex) {
        super(chunk, entryIndex);
    }


    public void parse(String[] tokens, int numTokens) {
        super.parse(tokens, numTokens);
    }

    public void print(OutputStream stream) {
        try {
            stream.write(("CUC").getBytes());
            super.print(stream);
            stream.write(("\n").getBytes());
            stream.flush();
        }
        catch (IOException e) {
            throw new RuntimeException (e);
        }
    }

    public int getClassIndex() { return 60; }

    public String getAsString() { return "CUC"; }

    public ArrayList<String> getAttributeNames() {
        ArrayList<String> names = new ArrayList<String>();
        names.addAll(super.getAttributeNames());
        return names;
    }

    @SuppressWarnings("unused")
    public String getAsString(String attribute) {
        if (false)
            return null;
        else
            return super.getAsString(attribute);

    }

    public String getClassName() { return "CustomCreatedEntry"; }
}

