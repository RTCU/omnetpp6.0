package org.omnetpp.inifile.editor.text;

import org.eclipse.core.filebuffers.IDocumentSetupParticipant;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentExtension3;
import org.eclipse.jface.text.IDocumentPartitioner;
import org.eclipse.jface.text.rules.FastPartitioner;
import org.omnetpp.inifile.editor.text.assist.NedContentAssistPartitionScanner;
import org.omnetpp.inifile.editor.text.highlight.NedSyntaxHighlightPartitionScanner;

/**
 * Configures the NED editor by adding all sorts of features into it 
 */
//XXX TODO rename, revise, possibly remove...
//TODO "F3 Goto declaration" or ctrl-click "Follow Hyperlink" feature
//FIXME: trying to use ctrl-H "Search" with NED files open result in "SWTException: Invalid thread access"!
public class NedDocumentSetupParticipant implements IDocumentSetupParticipant {
	
	/**
	 */
	public NedDocumentSetupParticipant() {
	}

	/*
	 * @see org.eclipse.core.filebuffers.IDocumentSetupParticipant#setup(org.eclipse.jface.text.IDocument)
	 */
	public void setup(IDocument document) {
		if (document instanceof IDocumentExtension3) {
			IDocumentExtension3 extension3= (IDocumentExtension3) document;
            
            // content assist partitioner setup
            IDocumentPartitioner contentPartitioner = 
                new FastPartitioner(new NedContentAssistPartitionScanner(), NedContentAssistPartitionScanner.SUPPORTED_PARTITION_TYPES);
			extension3.setDocumentPartitioner(NedContentAssistPartitionScanner.PARTITIONING_ID, contentPartitioner);
			contentPartitioner.connect(document);

            // syntax highlighter partitioner setup
            IDocumentPartitioner highlightPartitioner = 
                new FastPartitioner(new NedSyntaxHighlightPartitionScanner(), NedSyntaxHighlightPartitionScanner.SUPPORTED_PARTITION_TYPES);
            extension3.setDocumentPartitioner(NedSyntaxHighlightPartitionScanner.PARTITIONING_ID, highlightPartitioner);
            highlightPartitioner.connect(document);
            
            // outline partitioner setup
            // XXX unused code
            //IDocumentPartitioner outlinePartitioner = 
            //    new FastPartitioner(new NedOutlinePartitionScanner(), NedOutlinePartitionScanner.SUPPORTED_PARTITION_TYPES);
            //extension3.setDocumentPartitioner(NedOutlinePartitionScanner.PARTITIONING_ID, outlinePartitioner);
            //outlinePartitioner.connect(document);
		}
	}
}
