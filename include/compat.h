//==========================================================================
//  COMPAT.H - part of
//                     OMNeT++/OMNEST
//            Discrete System Simulation in C++
//
//
//  Backwards compatibility
//
//==========================================================================

/*--------------------------------------------------------------*
  Copyright (C) 1992-2005 Andras Varga

  This file is distributed WITHOUT ANY WARRANTY. See the file
  `license' for details on this and other legal matters.
*--------------------------------------------------------------*/

#ifndef __COMPAT_H
#define __COMPAT_H


//
// This header needs to be included *last* in omnetpp.h.
//

// cQueue
#define peekTail()     tail()
#define peekHead()     head()

// cModule
#define displayStringAsParent()  backgroundDisplayString()
#define setDisplayStringAsParent setBackgroundDisplayString

//XXX can typedefs be deprecated?

// following ones became inner classes:
typedef cKSplit::Iterator      cKSplitIterator;
typedef cKSplit::Grid          sGrid;
//XXX remove, as not compatible anyway: typedef cQueue::Iterator       cQueueIterator;
typedef cLinkedList::Iterator  cLinkedListIterator;
typedef cMessageHeap::Iterator cMessageHeapIterator;
typedef cTopology::Link        sTopoLink;
typedef cTopology::LinkIn      sTopoLinkIn;
typedef cTopology::LinkOut     sTopoLinkOut;
typedef cTopology::Node        sTopoNode;

// renamed classes:
typedef cDisplayString cDisplayStringParser;
typedef cBasicChannel  cSimpleChannel;
typedef cObject        cPolymorphic;
#endif

