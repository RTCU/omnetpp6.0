//=========================================================================
//  SCAVEDEFS.H - part of
//                  OMNeT++/OMNEST
//           Discrete System Simulation in C++
//
//=========================================================================

/*--------------------------------------------------------------*
  Copyright (C) 1992-2005 Andras Varga

  This file is distributed WITHOUT ANY WARRANTY. See the file
  `license' for details on this and other legal matters.
*--------------------------------------------------------------*/

#ifndef _SCAVEDEFS_H_
#define _SCAVEDEFS_H_

#include "inttypes.h" // for int64, our equivalent of Java's "long" type
#include "platdefs.h"

#ifdef BUILDING_SCAVE
#  define SCAVE_API  OPP_DLLEXPORT
#else
#  define SCAVE_API  OPP_DLLIMPORT
#endif

#define DEFAULT_PRECISION  14

#endif


