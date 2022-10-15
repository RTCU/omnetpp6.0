//==========================================================================
//  CMDENV.H - part of
//                     OMNeT++/OMNEST
//            Discrete System Simulation in C++
//
//==========================================================================

/*--------------------------------------------------------------*
  Copyright (C) 1992-2017 Andras Varga
  Copyright (C) 2006-2017 OpenSim Ltd.

  This file is distributed WITHOUT ANY WARRANTY. See the file
  `license' for details on this and other legal matters.
*--------------------------------------------------------------*/

#ifndef __OMNETPP_CMDENV_CMDENV_H
#define __OMNETPP_CMDENV_CMDENV_H

#include <map>
#include <atomic>
#include <thread>
#include "envir/appbase.h"
#include "envir/speedometer.h"
#include "omnetpp/csimulation.h"
#include "fakegui.h"

namespace omnetpp {
namespace cmdenv {

using namespace omnetpp::envir;

class CmdEnvir;

/**
 * Command line user interface.
 */
class CMDENV_API Cmdenv : public AppBase
{
     friend class CmdEnvir;
     friend class CmdenvSimulationHolder;
   protected:
     cINedLoader *nedLoader = nullptr;

     // set to true on SIGINT/SIGTERM signals
     static bool sigintReceived;

     // the number of runs already started (>1 if multiple runs are running in the same process)
     std::atomic_int numRuns;
     std::atomic_int runsTried;
     std::atomic_int numErrors;

     bool stopBatchOnError = true;

   protected:
     virtual void alert(const char *msg) override;
     virtual void displayException(std::exception& ex) override;

   public:
     Cmdenv();
     virtual ~Cmdenv();

   protected:
     virtual int doRunApp() override;
     virtual void printUISpecificHelp() override;

     bool runSimulation(const char *configName, int runNumber);
     void runSimulations(const char *configName, const std::vector<int>& runNumbers);
     void runSimulationsInThreads(const char *configName, const std::vector<int>& runNumbers, int numThreads);
     std::thread startThread(const char *configName, const std::vector<int>& runNumbers);

     void installSigintHandler();
     void deinstallSigintHandler();
     static void sigintHandler(int signum);
};

}  // namespace cmdenv
}  // namespace omnetpp

#endif

