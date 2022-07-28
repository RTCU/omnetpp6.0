//=========================================================================
//  CSIMULATION.CC - part of
//
//                  OMNeT++/OMNEST
//           Discrete System Simulation in C++
//
//   Definition of global object:
//    simulation
//
//   Member functions of
//    cSimulation  : holds modules and manages the simulation
//
//=========================================================================

/*--------------------------------------------------------------*
  Copyright (C) 1992-2017 Andras Varga
  Copyright (C) 2006-2017 OpenSim Ltd.

  This file is distributed WITHOUT ANY WARRANTY. See the file
  `license' for details on this and other legal matters.
*--------------------------------------------------------------*/

#include <cstring>
#include <cstdio>
#include <climits>
#include <algorithm> // copy_n()
#include "common/stringutil.h"
#include "omnetpp/cmodule.h"
#include "omnetpp/csimplemodule.h"
#include "omnetpp/cpacket.h"
#include "omnetpp/cchannel.h"
#include "omnetpp/csimulation.h"
#include "omnetpp/cconfiguration.h"
#include "omnetpp/cconfigoption.h"
#include "omnetpp/globals.h"
#include "omnetpp/regmacros.h"

#include "ctemporaryowner.h"
#include "omnetpp/cscheduler.h"
#include "omnetpp/ceventheap.h"
#include "omnetpp/cenvir.h"
#include "omnetpp/ccomponenttype.h"
#include "omnetpp/ccontextswitcher.h"
#include "omnetpp/cstatistic.h"
#include "omnetpp/cexception.h"
#include "omnetpp/cparimpl.h"
#include "omnetpp/cfingerprint.h"
#include "omnetpp/cconfiguration.h"
#include "omnetpp/ccoroutine.h"
#include "omnetpp/cnedloader.h"
#include "omnetpp/clifecyclelistener.h"
#include "omnetpp/crngmanager.h"
#include "omnetpp/platdep/platmisc.h"  // for DEBUG_TRAP

#ifdef WITH_PARSIM
#include "omnetpp/ccommbuffer.h"
#include "omnetpp/cparsimcomm.h"
#include "sim/parsim/cparsimpartition.h"
#include "sim/parsim/cparsimsynchr.h"
#endif


using namespace omnetpp::common;
using namespace omnetpp::internal;

namespace omnetpp {

using std::ostream;

#ifdef NDEBUG
#define CHECKSIGNALS_DEFAULT        "false"
#else
#define CHECKSIGNALS_DEFAULT        "true"
#endif

Register_PerRunConfigOption(CFGID_PARALLEL_SIMULATION, "parallel-simulation", CFG_BOOL, "false", "Enables parallel distributed simulation.");
Register_PerRunConfigOption(CFGID_FUTUREEVENTSET_CLASS, "futureeventset-class", CFG_STRING, "omnetpp::cEventHeap", "Part of the Envir plugin mechanism: selects the class for storing the future events in the simulation. The class has to implement the `cFutureEventSet` interface.");
Register_PerRunConfigOption(CFGID_SCHEDULER_CLASS, "scheduler-class", CFG_STRING, "omnetpp::cSequentialScheduler", "Part of the Envir plugin mechanism: selects the scheduler class. This plugin interface allows for implementing real-time, hardware-in-the-loop, distributed and distributed parallel simulation. The class has to implement the `cScheduler` interface.");
Register_PerRunConfigOption(CFGID_FINGERPRINT, "fingerprint", CFG_STRING, nullptr, "The expected fingerprints of the simulation. If you need multiple fingerprints, separate them with commas. When provided, the fingerprints will be calculated from the specified properties of simulation events, messages, and statistics during execution, and checked against the provided values. Fingerprints are suitable for crude regression tests. As fingerprints occasionally differ across platforms, more than one value can be specified for a single fingerprint, separated by spaces, and a match with any of them will be accepted. To obtain a fingerprint, enter a dummy value (such as `0000`), and run the simulation.");
Register_PerRunConfigOption(CFGID_FINGERPRINTER_CLASS, "fingerprintcalculator-class", CFG_STRING, "omnetpp::cSingleFingerprintCalculator", "Part of the Envir plugin mechanism: selects the fingerprint calculator class to be used to calculate the simulation fingerprint. The class has to implement the `cFingerprintCalculator` interface.");
Register_PerRunConfigOption(CFGID_RNGMANAGER_CLASS, "rngmanager-class", CFG_STRING, "omnetpp::cRngManager", "Part of the Envir plugin mechanism: selects the RNG manager class to be used for providing RNGs to modules and channels. The class has to implement the `cIRngManager` interface.");
Register_PerRunConfigOption(CFGID_CHECK_SIGNALS, "check-signals", CFG_BOOL, CHECKSIGNALS_DEFAULT, "Controls whether the simulation kernel will validate signals emitted by modules and channels against signal declarations (`@signal` properties) in NED files. The default setting depends on the build type: `true` in DEBUG, and `false` in RELEASE mode.");
Register_PerRunConfigOption(CFGID_PARAMETER_MUTABILITY_CHECK, "parameter-mutability-check", CFG_BOOL, "true", "Setting to false will disable errors raised when trying to change the values of module/channel parameters not marked as @mutable. This is primarily a compatibility setting intended to facilitate running simulation models that were not yet annotated with @mutable.");
Register_PerRunConfigOption(CFGID_ALLOW_OBJECT_STEALING_ON_DELETION, "allow-object-stealing-on-deletion", CFG_BOOL, "false", "Setting it to true disables the \"Context component is deleting an object it doesn't own\" error message. This option exists primarily for backward compatibility with pre-6.0 versions that were more permissive during object deletion.");


#ifdef DEVELOPER_DEBUG
#include <set>
extern std::set<cOwnedObject *> objectlist;
void printAllObjects();
#endif


/**
 * Stops the simulation at the time it is scheduled for.
 * Used internally by cSimulation.
 */
class SIM_API cEndSimulationEvent : public cEvent, public noncopyable
{
  public:
    cEndSimulationEvent(const char *name, simtime_t simTimeLimit) : cEvent(name) {
        setArrivalTime(simTimeLimit);
        setSchedulingPriority(SHRT_MAX);  // lowest priority
    }
    virtual cEvent *dup() const override { copyNotSupported(); return nullptr; }
    virtual cObject *getTargetObject() const override { return nullptr; }
    virtual void execute() override { delete this; throw cTerminationException(E_SIMTIME); }
};

cSimulation::cSimulation(const char *name, cEnvir *env) : cNamedObject(name, false)
{
    ASSERT(cStaticFlag::insideMain());  // cannot be instantiated as global variable

    envir = env;

    // these are not set inline because the declaring header is included here
    simulationStage = CTX_NONE;
    contextType = CTX_NONE;

    // install default objects
    setFES(new cEventHeap("fes"));
    setScheduler(new cSequentialScheduler());
    setRngManager(new cRngManager());
    setFingerprintCalculator(new cSingleFingerprintCalculator());
}

cSimulation::~cSimulation()
{
//    if (this == activeSimulation) {
//        // note: C++ forbids throwing in a destructor, and noexcept(false) is not workable
//        getEnvir()->alert(cRuntimeError(this, "Cannot delete the active simulation manager object, ABORTING").getFormattedMessage().c_str());
//        abort();
//    }

    deleteNetwork();  // note: requires this being the active simulation

    auto copy = listeners;
    for (auto& listener : copy)
        listener->listenerRemoved();

    dropAndDelete(fingerprint);
    dropAndDelete(rngManager);
    dropAndDelete(scheduler);
    dropAndDelete(fes);

#ifdef WITH_PARSIM
    delete parsimPartition;
#endif

    if (getActiveSimulation() == this)
        setActiveSimulation(nullptr);

    delete envir;  // after setActiveSimulation(nullptr), due to objectDeleted() callbacks
}

void cSimulation::setActiveSimulation(cSimulation *sim)
{
    activeSimulation = sim;
    activeEnvir = sim == nullptr ? staticEnvir : sim->envir;
}

void cSimulation::setStaticEnvir(cEnvir *env)
{
    if (!env)
        throw cRuntimeError("cSimulation::setStaticEnvir(): Argument cannot be nullptr");
    staticEnvir = env;
}

void cSimulation::forEachChild(cVisitor *v)
{
    if (systemModule != nullptr)
        v->visit(systemModule);
    if (fes)
        v->visit(fes);
    if (scheduler)
        v->visit(scheduler);
    if (rngManager)
        v->visit(rngManager);
    if (nedLoader)
        v->visit(nedLoader);
    if (fingerprint)
        v->visit(fingerprint);
}

std::string cSimulation::getFullPath() const
{
    return getFullName();
}

void cSimulation::configure(cConfiguration *cfg)
{
    parsim = cfg->getAsBool(CFGID_PARALLEL_SIMULATION);

#ifndef WITH_PARSIM
    if (parsim)
        throw cRuntimeError("Parallel simulation is turned on in the ini file, but OMNeT++ was compiled without parallel simulation support (WITH_PARSIM=no)");
#endif

    // parallel simulation
    if (parsim) {
#ifdef WITH_PARSIM
        delete parsimPartition;
        parsimPartition = new cParsimPartition();
        parsimPartition->configure(getSimulation(), cfg);
#else
        throw cRuntimeError("Parallel simulation is turned on in the ini file, but OMNeT++ was compiled without parallel simulation support (WITH_PARSIM=no)");
#endif
    }

    // install FES
    std::string futureeventsetClass = cfg->getAsString(CFGID_FUTUREEVENTSET_CLASS);
    cFutureEventSet *fes = createByClassName<cFutureEventSet>(futureeventsetClass.c_str(), "FES");
    setFES(fes);
    fes->configure(this, cfg);

    // install scheduler
    if (!parsim) {
        std::string schedulerClass = cfg->getAsString(CFGID_SCHEDULER_CLASS);
        cScheduler *scheduler = createByClassName<cScheduler>(schedulerClass.c_str(), "event scheduler");
        setScheduler(scheduler);
    }

    scheduler->configure(this, cfg);

    // rng manager
    std::string rngManagerClass = cfg->getAsString(CFGID_RNGMANAGER_CLASS);
    cIRngManager *rngManager = createByClassName<cIRngManager>(rngManagerClass.c_str(), "RNG manager");
    setRngManager(rngManager);

    // install fingerprint calculator object
    cFingerprintCalculator *fingerprint = nullptr;
    std::string expectedFingerprints = cfg->getAsString(CFGID_FINGERPRINT);
    if (expectedFingerprints.empty())
        setFingerprintCalculator(nullptr);
    else {
        // create calculator
        std::string fingerprintClass = cfg->getAsString(CFGID_FINGERPRINTER_CLASS);
        fingerprint = createByClassName<cFingerprintCalculator>(fingerprintClass.c_str(), "fingerprint calculator");
        if (expectedFingerprints.find(',') != expectedFingerprints.npos)
            fingerprint = new cMultiFingerprintCalculator(fingerprint);
        setFingerprintCalculator(fingerprint);
        fingerprint->configure(this, cfg, expectedFingerprints.c_str());
    }

    // init nextUniqueNumber
    setUniqueNumberRange(0, 0); // =until it wraps
#ifdef WITH_PARSIM
    if (parsim) {
        uint64_t myRank = parsimPartition->getProcId();
        uint64_t range = UINT64_MAX / parsimPartition->getNumPartitions();
        setUniqueNumberRange(myRank * range, (myRank+1) * range);
    }
#endif

    bool checkSignals = cfg->getAsBool(CFGID_CHECK_SIGNALS);
    cComponent::setCheckSignals(checkSignals);

    bool checkParamMutability = cfg->getAsBool(CFGID_PARAMETER_MUTABILITY_CHECK);
    setParameterMutabilityCheck(checkParamMutability);

    bool allowObjectStealing = cfg->getAsBool(CFGID_ALLOW_OBJECT_STEALING_ON_DELETION);
    cSoftOwner::setAllowObjectStealing(allowObjectStealing);

    getRngManager()->configure(this, cfg, getParsimProcId(), getParsimNumPartitions());
}

class cSnapshotWriterVisitor : public cVisitor
{
  protected:
    ostream& os;
    int indentLevel = 0;

  public:
    cSnapshotWriterVisitor(ostream& ostr) : os(ostr) {}

    virtual bool visit(cObject *obj) override {
        std::string indent(2 * indentLevel, ' ');
        os << indent << "<object class=\"" << obj->getClassName() << "\" fullpath=\"" << opp_xmlquote(obj->getFullPath()) << "\">\n";
        os << indent << "  <info>" << opp_xmlquote(obj->str()) << "</info>\n";
        indentLevel++;
        obj->forEachChild(this);
        indentLevel--;
        os << indent << "</object>\n\n";
        if (os.fail())
            return false;
        return true;
    }
};

void cSimulation::snapshot(cObject *object, const char *label)
{
    if (!object)
        throw cRuntimeError("snapshot(): Object pointer is nullptr");

    ostream *osptr = getEnvir()->getStreamForSnapshot();
    if (!osptr)
        throw cRuntimeError("Could not create stream for snapshot");

    ostream& os = *osptr;

    cModuleType *networkType = getNetworkType();

    os << "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\n";
    os << "<snapshot\n";
    os << "    object=\"" << opp_xmlquote(object->getFullPath()) << "\"\n";
    os << "    label=\"" << opp_xmlquote(label ? label : "") << "\"\n";
    os << "    simtime=\"" << opp_xmlquote(SIMTIME_STR(simTime())) << "\"\n";
    os << "    network=\"" << opp_xmlquote(networkType ? networkType->getName() : "") << "\"\n";
    os << "    >\n";

    cSnapshotWriterVisitor v(os);
    v.process(object);

    os << "</snapshot>\n";

    bool success = os.good();
    getEnvir()->releaseStreamForSnapshot(&os);
    if (!success)
        throw cRuntimeError("Could not write snapshot");
}

void cSimulation::setNedLoader(cINedLoader *loader)
{
    nedLoader = loader;
}

void cSimulation::setScheduler(cScheduler *sch)
{
    if (systemModule)
        throw cRuntimeError(this, "setScheduler(): Cannot switch schedulers when a network is already set up");
    if (!sch)
        throw cRuntimeError(this, "setScheduler(): New scheduler cannot be nullptr");

    if (scheduler)
        dropAndDelete(scheduler);

    scheduler = sch;
    take(scheduler);
}

void cSimulation::setFES(cFutureEventSet *f)
{
    if (systemModule)
        throw cRuntimeError(this, "setFES(): Cannot switch FES when a network is already set up");
    if (fes && !fes->isEmpty())
        throw cRuntimeError(this, "setFES(): Existing FES is not empty");
    if (!f)
        throw cRuntimeError(this, "setFES(): New FES cannot be nullptr");

    if (fes)
        dropAndDelete(fes);

    fes = f;
    fes->setName("scheduled-events");
    take(fes);
}

void cSimulation::setRngManager(cIRngManager *mgr)
{
    if (systemModule)
        throw cRuntimeError(this, "setRngManager(): Cannot switch RNG managers when a network is already set up");
    if (!mgr)
        throw cRuntimeError(this, "setRngManager(): New RNG manager cannot be nullptr");

    if (rngManager)
        dropAndDelete(rngManager);

    rngManager = mgr;
    take(rngManager);
}

void cSimulation::setSimulationTimeLimit(simtime_t limit)
{
    if (limit != SIMTIME_ZERO && limit < getSimTime())
        throw cRuntimeError(this, "setSimulationTimeLimit(): Requested time limit has already passed");

    simTimeLimit = limit;

    if (getSystemModule() != nullptr)
        scheduleEndSimulationEvent();
}

void cSimulation::scheduleEndSimulationEvent()
{
    if (endSimulationEvent) {
        getFES()->remove(endSimulationEvent);
        delete endSimulationEvent;
        endSimulationEvent = nullptr;
    }
    if (simTimeLimit != SIMTIME_ZERO) {
        endSimulationEvent = new cEndSimulationEvent("endSimulation", simTimeLimit);
        getFES()->insert(endSimulationEvent);
    }
}

simtime_t cSimulation::getSimulationTimeLimit() const
{
    return simTimeLimit;
}

int cSimulation::loadNedSourceFolder(const char *folder, const char *excludedPackages)
{
    if (!nedLoader)
        throw cRuntimeError("No NED loader installed");
    return nedLoader->loadNedSourceFolder(folder, excludedPackages);
}

void cSimulation::loadNedFile(const char *nedFilename, const char *expectedPackage, bool isXML)
{
    if (!nedLoader)
        throw cRuntimeError("No NED loader installed");
    nedLoader->loadNedFile(nedFilename, expectedPackage, isXML);
}

void cSimulation::loadNedText(const char *name, const char *nedText, const char *expectedPackage, bool isXML)
{
    if (!nedLoader)
        throw cRuntimeError("No NED loader installed");
    nedLoader->loadNedText(name, nedText, expectedPackage, isXML);
}

void cSimulation::checkLoadedTypes()
{
    if (!nedLoader)
        throw cRuntimeError("No NED loader installed");
    nedLoader->checkLoadedTypes();
}

std::string cSimulation::getNedPackageForFolder(const char *folder)
{
    if (!nedLoader)
        throw cRuntimeError("No NED loader installed");
    return nedLoader->getNedPackageForFolder(folder);
}

int cSimulation::registerComponent(cComponent *component)
{
    ASSERT(component->simulation == nullptr && component->componentId == -1);

    lastComponentId++;

    if (lastComponentId >= size) {
        // vector full, grow by delta
        cComponent **v = new cComponent *[size + delta];
        std::copy_n(componentv, size, v);
        for (int i = size; i < size + delta; i++)
            v[i] = nullptr;
        delete[] componentv;
        componentv = v;
        size += delta;
    }

    int id = lastComponentId;
    componentv[id] = component;
    component->simulation = this;
    component->componentId = id;
    return id;
}

void cSimulation::deregisterComponent(cComponent *component)
{
    ASSERT(component->simulation == this && component->componentId != -1);

    int id = component->componentId;
    component->simulation = nullptr;
    component->componentId = -1;
    componentv[id] = nullptr;

    if (component == systemModule) {
        cOwningContextSwitcher tmp(&globalOwningContext);
        drop(systemModule);
        systemModule = nullptr;
    }
}

void cSimulation::setSystemModule(cModule *module)
{
    if (systemModule != nullptr)
        throw cRuntimeError(this, "Cannot set module '%s' as toplevel (system) module: there is already a system module ", module->getFullName());
    if (module->parentModule != nullptr)
        throw cRuntimeError(this, "Cannot set module '%s' as toplevel (system) module: it already has a parent module", module->getFullName());
    if (module->vectorIndex != -1)
        throw cRuntimeError(this, "Cannot set module '%s' as toplevel (system) module: toplevel module cannot be part of a module vector", module->getFullName());

    systemModule = module;
    take(module);
}

cModule *cSimulation::getModuleByPath(const char *path) const
{
    cModule *module = findModuleByPath(path);
    if (!module)
        throw cRuntimeError("cSimulation::getModuleByPath(): Module at '%s' not found (Note: operation of getModuleByPath() has changed in OMNeT++ version 6.0, use findModuleByPath() in you want the original behavior)", path);
    return module;
}

cModule *cSimulation::findModuleByPath(const char *path) const
{
    cModule *root = getSystemModule();
    if (!root || !path || !path[0])
        return nullptr;
    if (path[0] == '.' || path[0] == '^')
        throw cRuntimeError(this, "cSimulation::getModuleByPath(): Path cannot be relative, i.e. start with '.' or '^' (path='%s')", path);
    return root->findModuleByPath(path);
}

void cSimulation::setupNetwork(cModuleType *networkType)
{
#ifdef DEVELOPER_DEBUG
    printf("DEBUG: before setupNetwork: %d objects\n", cOwnedObject::getLiveObjectCount());
    objectlist.clear();
#endif

    checkActive();
    if (!networkType)
        throw cRuntimeError("setupNetwork(): nullptr received");
    if (!networkType->isNetwork())
        throw cRuntimeError("Cannot set up network: '%s' is not a network type", networkType->getFullName());

    // just to be sure
    fes->clear();
    cComponent::clearSignalState();

    simulationStage = CTX_BUILD;

    try {
        // set up the network by instantiating the toplevel module
        cContextTypeSwitcher tmp(CTX_BUILD);
        notifyLifecycleListeners(LF_PRE_NETWORK_SETUP);
        cModule *module = networkType->create(networkType->getName(), this);
        module->finalizeParameters();
        module->buildInside();
        scheduleEndSimulationEvent();
        notifyLifecycleListeners(LF_POST_NETWORK_SETUP);
    }
    catch (std::exception& e) {
        // Note: no deleteNetwork() call here. We could call it here, but it's
        // dangerous: module destructors may try to delete uninitialized pointers
        // and crash. (Often pointers incorrectly get initialized in initialize()
        // and not in the constructor.)
        throw;
    }
}

void cSimulation::callInitialize()
{
    checkActive();

    // reset counters. Note fes->clear() was already called from setupNetwork()
    currentSimtime = 0;
    currentEventNumber = 0;  // initialize() has event number 0
    trapOnNextEvent = false;
    cMessage::resetMessageCounters();

    simulationStage = CTX_INITIALIZE;

    // prepare simple modules for simulation run:
    //    1. create starter message for all modules,
    //    2. then call initialize() for them (recursively)
    //  This order is important because initialize() functions might contain
    //  send() calls which could otherwise insert msgs BEFORE starter messages
    //  for the destination module and cause trouble in cSimpleMod's activate().
    if (systemModule) {
        cContextSwitcher tmp(systemModule);
        systemModule->scheduleStart(SIMTIME_ZERO);
        notifyLifecycleListeners(LF_PRE_NETWORK_INITIALIZE);
        systemModule->callInitialize();
        notifyLifecycleListeners(LF_POST_NETWORK_INITIALIZE);
    }

    simulationStage = CTX_EVENT;
}

void cSimulation::callFinish()
{
    checkActive();

    simulationStage = CTX_FINISH;

    // call user-defined finish() functions for all modules recursively
    if (systemModule) {
        notifyLifecycleListeners(LF_PRE_NETWORK_FINISH);
        systemModule->callFinish();
        notifyLifecycleListeners(LF_POST_NETWORK_FINISH);
    }
}

void cSimulation::callRefreshDisplay()
{
    if (systemModule) {
        systemModule->callRefreshDisplay();
        if (fingerprint)
            fingerprint->addVisuals();
    }
}

void cSimulation::deleteNetwork()
{
    if (!systemModule)
        return;  // network already deleted

    if (cSimulation::getActiveSimulation() != this)
        throw cRuntimeError("cSimulation: Cannot invoke deleteNetwork() on an instance that is not the active one (see cSimulation::getActiveSimulation())");  // because cModule::deleteModule() would crash

    if (getContextModule() != nullptr)
        throw cRuntimeError("Attempt to delete network during simulation");

    simulationStage = CTX_CLEANUP;

    notifyLifecycleListeners(LF_PRE_NETWORK_DELETE);

    // delete all modules recursively
    systemModule->deleteModule();

    // remove stray channel objects (created by cChannelType::create() but not inserted into the network)
    for (int i = 1; i < size; i++) {
        if (componentv[i]) {
            ASSERT(componentv[i]->isChannel() && componentv[i]->getParentModule()==nullptr);
            componentv[i]->setFlag(cComponent::FL_DELETING, true);
            delete componentv[i];
        }
    }

    // and clean up
    delete[] componentv;
    componentv = nullptr;
    size = 0;
    lastComponentId = 0;

    for (cComponentType *p : nedLoader->getComponentTypes())
        p->clearSharedParImpls();
    cModule::clearNamePools();

    notifyLifecycleListeners(LF_POST_NETWORK_DELETE);

    // clear remaining messages (module dtors may have cancelled & deleted some of them)
    fes->clear();

    simulationStage = CTX_NONE;

#ifdef DEVELOPER_DEBUG
    printf("DEBUG: after deleteNetwork: %d objects\n", cOwnedObject::getLiveObjectCount());
    printAllObjects();
#endif
}

cModuleType *cSimulation::getNetworkType() const
{
    return systemModule ? systemModule->getModuleType() : nullptr;
}

cEvent *cSimulation::takeNextEvent()
{
    // determine next event. Normally (with sequential simulation),
    // the scheduler just returns fes->peekFirst().
    cEvent *event = scheduler->takeNextEvent();
    if (!event)
        return nullptr;

    ASSERT(!event->isStale());  // it's the scheduler's task to discard stale events

    return event;
}

void cSimulation::putBackEvent(cEvent *event)
{
    scheduler->putBackEvent(event);
}

cEvent *cSimulation::guessNextEvent()
{
    return scheduler->guessNextEvent();
}

simtime_t cSimulation::guessNextSimtime()
{
    cEvent *event = guessNextEvent();
    return event == nullptr ? -1 : event->getArrivalTime();
}

cSimpleModule *cSimulation::guessNextModule()
{
    cEvent *event = guessNextEvent();
    cMessage *msg = dynamic_cast<cMessage *>(event);
    if (!msg)
        return nullptr;

    // check that destination module still exists and hasn't terminated yet
    if (msg->getArrivalModuleId() == -1)
        return nullptr;
    cComponent *component = componentv[msg->getArrivalModuleId()];
    cSimpleModule *module = dynamic_cast<cSimpleModule *>(component);
    if (!module || module->isTerminated())
        return nullptr;
    return module;
}

void cSimulation::transferTo(cSimpleModule *module)
{
    if (!module)
        throw cRuntimeError("transferTo(): Attempt to transfer to nullptr");

    // switch to activity() of the simple module
    exception = nullptr;
    currentActivityModule = module;
    cCoroutine::switchTo(module->coroutine);

    if (module->hasStackOverflow())
        throw cRuntimeError("Stack violation in module (%s)%s: Module stack too small? "
                            "Try increasing it in the class' Module_Class_Members() or constructor",
                module->getClassName(), module->getFullPath().c_str());

    // if exception occurred in activity(), re-throw it. This allows us to handle
    // handleMessage() and activity() in an uniform way in the upper layer.
    if (exception) {
        cException *e = exception;
        exception = nullptr;

        // ok, so we have an exception *pointer*, but we have to throw further
        // by *value*, and possibly without leaking it. Hence the following magic...
        if (dynamic_cast<cDeleteModuleException *>(e)) {
            cDeleteModuleException e2(*(cDeleteModuleException *)e);
            delete e;
            throw e2;
        }
        else if (dynamic_cast<cTerminationException *>(e)) {
            cTerminationException e2(*(cTerminationException *)e);
            delete e;
            throw e2;
        }
        else if (dynamic_cast<cRuntimeError *>(e)) {
            cRuntimeError e2(*(cRuntimeError *)e);
            delete e;
            throw e2;
        }
        else {
            cException e2(*(cException *)e);
            delete e;
            throw e2;
        }
    }
}


#ifdef NDEBUG
#define DEBUG_TRAP_IF_REQUESTED    /*no-op*/
#else
#define DEBUG_TRAP_IF_REQUESTED    { if (trapOnNextEvent) { trapOnNextEvent = false; if (getEnvir()->ensureDebugger()) DEBUG_TRAP; } }
#endif

void cSimulation::executeEvent(cEvent *event)
{
#ifndef NDEBUG
    checkActive();

    // Sanity check to prevent reentrant event execution - which might
    // happen for example in a faulty cEnvir::pausePoint() implementation.
    static OPP_THREAD_LOCAL bool inExecuteEvent = false;
    ASSERT(!inExecuteEvent);
    inExecuteEvent = true;
    struct ResetInExecEventFlag {
        ~ResetInExecEventFlag() {
            inExecuteEvent = false;
        }
    } flagResetter;
#endif

    setContextType(CTX_EVENT);

    // increment event count
    currentEventNumber++;

    // advance simulation time
    currentSimtime = event->getArrivalTime();

    // notify the environment about the event (writes eventlog, etc.)
    EVCB.simulationEvent(event);

    // store arrival event number of this message; it is useful input for the
    // sequence chart tool if the message doesn't get immediately deleted or
    // sent out again
    event->setPreviousEventNumber(currentEventNumber);

    // ignore fingerprint of plain events, as they tend to be internal (like cEndSimulationEvent)
    if (getFingerprintCalculator() && event->isMessage())
        getFingerprintCalculator()->addEvent(event);

    try {
        if (!event->isMessage())
            DEBUG_TRAP_IF_REQUESTED;  // ABOUT TO PROCESS THE EVENT YOU REQUESTED TO DEBUG -- SELECT "STEP INTO" IN YOUR DEBUGGER
        event->execute();
    }
    catch (cDeleteModuleException& e) {
        setGlobalContext();
        e.getModuleToDelete()->deleteModule();
    }
    catch (cException&) {
        // restore global context before throwing the exception further
        setGlobalContext();
        throw;
    }
    catch (std::exception& e) {
        // restore global context before throwing the exception further
        // but wrap into a cRuntimeError which captures the module before that
        cRuntimeError e2("%s: %s", opp_typename(typeid(e)), e.what());
        setGlobalContext();
        throw e2;
    }
    setGlobalContext();

    // Note: simulation time (as read via simTime() from modules) will be updated
    // in takeNextEvent(), called right before the next executeEvent().
    // Simtime must NOT be updated here, because it would interfere with parallel
    // simulation (cIdealSimulationProtocol, etc) that relies on simTime()
    // returning the time of the last executed event. If Qtenv wants to display
    // the time of the next event, it should call guessNextSimtime().
}

#undef DEBUG_TRAP_IF_REQUESTED

void cSimulation::transferToMain()
{
    if (currentActivityModule != nullptr) {
        currentActivityModule = nullptr;
        cCoroutine::switchToMain();  // stack switch
    }
}

void cSimulation::setContext(cComponent *p)
{
    contextComponent = p;
    cOwnedObject::setOwningContext(p);
}

cModule *cSimulation::getContextModule() const
{
    // cannot go inline (upward cast would require including cmodule.h in csimulation.h)
    if (!contextComponent || !contextComponent->isModule())
        return nullptr;
    return (cModule *)contextComponent;
}

cSimpleModule *cSimulation::getContextSimpleModule() const
{
    // cannot go inline (upward cast would require including cmodule.h in csimulation.h)
    if (!contextComponent || !contextComponent->isModule() || !((cModule *)contextComponent)->isSimple())
        return nullptr;
    return (cSimpleModule *)contextComponent;
}

uint64_t cSimulation::getUniqueNumber()
{
    uint64_t ret = nextUniqueNumber++;
    if (nextUniqueNumber == uniqueNumbersEnd)
        throw cRuntimeError("getUniqueNumber(): All values have been consumed");
    return ret;
}

int cSimulation::getParsimProcId() const
{
#ifdef WITH_PARSIM
    return parsimPartition? parsimPartition->getProcId() : 0;
#else
    return 0;
#endif
}

int cSimulation::getParsimNumPartitions() const
{
#ifdef WITH_PARSIM
    return parsimPartition? parsimPartition->getNumPartitions() : 0;
#else
    return 0;
#endif
}

void cSimulation::setFingerprintCalculator(cFingerprintCalculator *f)
{
    if (fingerprint)
        dropAndDelete(fingerprint);
    fingerprint = f;
    if (fingerprint)
        take(fingerprint);
}

void cSimulation::insertEvent(cEvent *event)
{
    event->setPreviousEventNumber(currentEventNumber);
    fes->insert(event);
}

void cSimulation::addLifecycleListener(cISimulationLifecycleListener *listener)
{
    auto it = std::find(listeners.begin(), listeners.end(), listener);
    if (it == listeners.end()) {
        listeners.push_back(listener);
        listener->listenerAdded(this);
    }
}

void cSimulation::removeLifecycleListener(cISimulationLifecycleListener *listener)
{
    auto it = std::find(listeners.begin(), listeners.end(), listener);
    if (it != listeners.end()) {
        listeners.erase(it);
        listener->listenerRemoved();
    }
}

std::vector<cISimulationLifecycleListener*> cSimulation::getLifecycleListeners() const
{
    return listeners;
}

void cSimulation::notifyLifecycleListeners(SimulationLifecycleEventType eventType, cObject *details)
{
    // make a copy of the listener list, to avoid problems from listeners getting added/removed during notification
    auto copy = listeners;
    for (auto& listener : copy)
        listener->lifecycleEvent(eventType, details);  // let exceptions through, because errors must cause exitCode!=0
}

//----

/**
 * A dummy implementation of cEnvir, only provided so that one
 * can use simulation library classes outside simulations, that is,
 * in programs that only link with the simulation library (<i>sim_std</i>)
 * and not with the <i>envir</i>, <i>cmdenv</i>, <i>qtenv</i>,
 * etc. libraries.
 *
 * Many simulation library classes make calls to <i>ev</i> methods,
 * which would crash if <tt>evPtr</tt> was nullptr; one example is
 * cObject's destructor which contains an <tt>getEnvir()->objectDeleted()</tt>.
 * The solution provided here is that <tt>evPtr</tt> is initialized
 * to point to a StaticEnv instance, thus enabling library classes to work.
 *
 * StaticEnv methods either do nothing, or throw an "Unsupported method"
 * exception, so StaticEnv is only useful for the most basic usage scenarios.
 * For anything more complicated, <tt>evPtr</tt> must be set in <tt>main()</tt>
 * to point to a proper cEnvir implementation, like the Cmdenv or
 * Qtenv classes. (The <i>envir</i> library provides a <tt>main()</tt>
 * which does exactly that.)
 *
 * @ingroup Envir
 */
class StaticEnv : public cEnvir
{
  protected:
    void unsupported() const { throw opp_runtime_error("StaticEnv: Unsupported method called"); }
    virtual void alert(const char *msg) override { ::printf("\n<!> %s\n\n", msg); }
    virtual bool askYesNo(const char *msg) override  { unsupported(); return false; }

  public:
    // constructor, destructor
    StaticEnv() {}
    virtual ~StaticEnv() {}

    // eventlog callback interface
    virtual void objectDeleted(cObject *object) override {}
    virtual void simulationEvent(cEvent *event) override  {}
    virtual void messageScheduled(cMessage *msg) override  {}
    virtual void messageCancelled(cMessage *msg) override  {}
    virtual void beginSend(cMessage *msg, const SendOptions& options) override  {}
    virtual void messageSendDirect(cMessage *msg, cGate *toGate, const ChannelResult& result) override  {}
    virtual void messageSendHop(cMessage *msg, cGate *srcGate) override  {}
    virtual void messageSendHop(cMessage *msg, cGate *srcGate, const cChannel::Result& result) override  {}
    virtual void endSend(cMessage *msg) override  {}
    virtual void messageCreated(cMessage *msg) override  {}
    virtual void messageCloned(cMessage *msg, cMessage *clone) override  {}
    virtual void messageDeleted(cMessage *msg) override  {}
    virtual void moduleReparented(cModule *module, cModule *oldparent, int oldId) override  {}
    virtual void componentMethodBegin(cComponent *from, cComponent *to, const char *methodFmt, va_list va, bool silent) override  {}
    virtual void componentMethodEnd() override  {}
    virtual void moduleCreated(cModule *newmodule) override  {}
    virtual void moduleDeleted(cModule *module) override  {}
    virtual void gateCreated(cGate *newgate) override  {}
    virtual void gateDeleted(cGate *gate) override  {}
    virtual void connectionCreated(cGate *srcgate) override  {}
    virtual void connectionDeleted(cGate *srcgate) override  {}
    virtual void displayStringChanged(cComponent *component) override  {}
    virtual void undisposedObject(cObject *obj) override;
    virtual void log(cLogEntry *entry) override {}

    // configuration, model parameters
    virtual void preconfigureComponent(cComponent *component) override  {}
    virtual void configureComponent(cComponent *component) override {}
    virtual void readParameter(cPar *parameter) override  { unsupported(); }
    virtual cXMLElement *getXMLDocument(const char *filename, const char *xpath = nullptr) override  { unsupported(); return nullptr; }
    virtual cXMLElement *getParsedXMLString(const char *content, const char *xpath = nullptr) override  { unsupported(); return nullptr; }
    virtual void forgetXMLDocument(const char *filename) override {}
    virtual void forgetParsedXMLString(const char *content) override {}
    virtual void flushXMLDocumentCache() override {}
    virtual void flushXMLParsedContentCache() override {}
    virtual unsigned getExtraStackForEnvir() const override  { return 0; }
    virtual cConfiguration *getConfig() override  { unsupported(); return nullptr; }
    virtual std::string resolveResourcePath(const char *fileName, cComponentType *context) override {return "";}
    virtual bool isGUI() const override  { return false; }
    virtual bool isExpressMode() const override  { return false; }

    // UI functions (see also protected ones)
    virtual void bubble(cComponent *component, const char *text) override  {}
    virtual std::string gets(const char *prompt, const char *defaultreply = nullptr) override  { unsupported(); return ""; }
    virtual cEnvir& flush() { ::fflush(stdout); return *this; }

    // output vectors
    virtual void *registerOutputVector(const char *modulename, const char *vectorname) override  { return nullptr; }
    virtual void deregisterOutputVector(void *vechandle) override  {}
    virtual void setVectorAttribute(void *vechandle, const char *name, const char *value) override  {}
    virtual bool recordInOutputVector(void *vechandle, simtime_t t, double value) override  { return false; }

    // output scalars
    virtual void recordScalar(cComponent *component, const char *name, double value, opp_string_map *attributes = nullptr) override  {}
    virtual void recordStatistic(cComponent *component, const char *name, cStatistic *statistic, opp_string_map *attributes = nullptr) override  {}
    virtual void recordParameter(cPar *par) override {}
    virtual void recordComponentType(cComponent *component) override {}

    virtual void addResultRecorders(cComponent *component, simsignal_t signal, const char *statisticName, cProperty *statisticTemplateProperty) override {}

    // snapshot file
    virtual std::ostream *getStreamForSnapshot() override  { unsupported(); return nullptr; }
    virtual void releaseStreamForSnapshot(std::ostream *os) override  { unsupported(); }

    // misc
    virtual bool idle() override  { return false; }
    virtual void pausePoint() override {}
    virtual void refOsgNode(osg::Node *scene) override {}
    virtual void unrefOsgNode(osg::Node *scene) override {}
    virtual bool ensureDebugger(cRuntimeError *) override { return false; }
    virtual bool shouldDebugNow(cRuntimeError *error) override {return false;}

    virtual void getImageSize(const char *imageName, double& outWidth, double& outHeight) override {unsupported();}
    virtual void getTextExtent(const cFigure::Font& font, const char *text, double& outWidth, double& outHeight, double& outAscent) override {unsupported();}
    virtual void appendToImagePath(const char *directory) override {unsupported();}
    virtual void loadImage(const char *fileName, const char *imageName=nullptr) override {unsupported();}
    virtual cFigure::Rectangle getSubmoduleBounds(const cModule *submodule) override {return cFigure::Rectangle(NAN, NAN, NAN, NAN);}
    virtual std::vector<cFigure::Point> getConnectionLine(const cGate *sourceGate) override {return {};}
    virtual double getZoomLevel(const cModule *module) override {return NAN;}
    virtual double getAnimationTime() const override {return 0;}
    virtual double getAnimationSpeed() const override {return 0;}
    virtual double getRemainingAnimationHoldTime() const override {return 0;}
};

void StaticEnv::undisposedObject(cObject *obj)
{
    if (!cStaticFlag::insideMain()) {
        ::printf("<!> WARNING: global object variable (DISCOURAGED) detected: "
                 "(%s)'%s' at %p\n", obj->getClassName(), obj->getFullPath().c_str(), obj);
    }
}

OPP_THREAD_LOCAL StaticEnv staticEnv;

OPP_THREAD_LOCAL cEnvir *cSimulation::activeEnvir = &staticEnv;
OPP_THREAD_LOCAL cEnvir *cSimulation::staticEnvir = &staticEnv;
OPP_THREAD_LOCAL cSimulation *cSimulation::activeSimulation = nullptr;

}  // namespace omnetpp

