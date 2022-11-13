//=========================================================================
//  CCOMPONENTTYPE.CC - part of
//
//                    OMNeT++/OMNEST
//             Discrete System Simulation in C++
//
//=========================================================================

/*--------------------------------------------------------------*
  Copyright (C) 1992-2017 Andras Varga
  Copyright (C) 2006-2017 OpenSim Ltd.

  This file is distributed WITHOUT ANY WARRANTY. See the file
  `license' for details on this and other legal matters.
*--------------------------------------------------------------*/

#include <algorithm>
#include <cstring>
#include <mutex>
#include "ctemporaryowner.h"
#include "common/patternmatcher.h"
#include "common/fileutil.h"
#include "common/stringutil.h"
#include "omnetpp/ccomponenttype.h"
#include "omnetpp/crngmanager.h"
#include "omnetpp/ccontextswitcher.h"
#include "omnetpp/cmodule.h"
#include "omnetpp/csimplemodule.h"
#include "omnetpp/cchannel.h"
#include "omnetpp/cenvir.h"
#include "omnetpp/csimulation.h"
#include "omnetpp/cparimpl.h"
#include "omnetpp/cexception.h"
#include "omnetpp/globals.h"
#include "omnetpp/cdelaychannel.h"
#include "omnetpp/cdataratechannel.h"
#include "omnetpp/cmodelchange.h"
#include "omnetpp/cproperties.h"
#include "omnetpp/cproperty.h"
#include "omnetpp/cenum.h"
#include "omnetpp/ccanvas.h"
#include "omnetpp/cobjectfactory.h"
#include "omnetpp/cinedloader.h"

#ifdef WITH_PARSIM
#include "omnetpp/ccommbuffer.h"
#include "sim/parsim/cplaceholdermod.h"
#include "sim/parsim/cparsimpartition.h"
#endif

using namespace omnetpp::common;
using namespace omnetpp::internal;

namespace omnetpp {

OPP_THREAD_LOCAL std::map<const cComponentType*,cComponentType::PerThreadPerTypeData> cComponentType::perTypeData;

static std::mutex mutex;

static const char *getSignalTypeName(SimsignalType type)
{
    switch (type) {
        case SIMSIGNAL_BOOL: return "bool";
        case SIMSIGNAL_INT: return "int";
        case SIMSIGNAL_UINT: return "unsigned int";
        case SIMSIGNAL_DOUBLE: return "double";
        case SIMSIGNAL_SIMTIME: return "simtime_t";
        case SIMSIGNAL_STRING: return "string";
        case SIMSIGNAL_OBJECT: return "object";
        default: return nullptr;
    }
};

static SimsignalType getSignalType(const char *name, SimsignalType fallback=SIMSIGNAL_UNDEF)
{
    if (!strcmp(name, "bool")) return SIMSIGNAL_BOOL;
    if (!strcmp(name, "int") || !strcmp(name, "long")) return SIMSIGNAL_INT;
    if (!strcmp(name, "unsigned int") || !strcmp(name, "unsigned long")) return SIMSIGNAL_UINT;
    if (!strcmp(name, "double")) return SIMSIGNAL_DOUBLE;
    if (!strcmp(name, "simtime_t")) return SIMSIGNAL_SIMTIME;
    if (!strcmp(name, "string")) return SIMSIGNAL_STRING;
    if (!strcmp(name, "object")) return SIMSIGNAL_OBJECT;
    return fallback;
};

//----

cComponentType:: cComponentType(const char *qname) : cNoncopyableOwnedObject(qname, false),
    qualifiedName(qname)
{
    // store fully qualified name, and set name to simple (unqualified) name
    const char *lastDot = strrchr(qname, '.');
    setName(!lastDot ? qname : lastDot + 1);
}

cComponentType::~cComponentType()
{
    clearSharedParImpls();
}

cComponentType *cComponentType::find(const char *qname)
{
    return cSimulation::getActiveSimulation()->getNedLoader()->lookupComponentType(qname);
}

cComponentType *cComponentType::get(const char *qname)
{
    cComponentType *componentType = find(qname);
    if (!componentType) {
        const char *hint = (!qname || !strchr(qname, '.')) ? " (fully qualified type name expected)" : "";
        throw cRuntimeError("NED type '%s' not found%s", qname, hint);
    }
    return componentType;
}

template<typename T>
std::vector<T*> filter(const std::vector<cComponentType*>& types, const char *name=nullptr)
{
    std::vector<T*> result;
    for (cComponentType *t : types)
        if (name == nullptr || opp_streq(t->getName(), name))
            if (T *tt = dynamic_cast<T*>(t))
                result.push_back(tt);
    return result;
}

std::vector<cComponentType*> cComponentType::findAll(const char *name)
{
    //TODO allow user to add custom types (that DID NOT come from the NED loader) -- e.g. add getComponentTypes() to cSimulation? TODO store cSimulation* in cComponentType?
    return filter<cComponentType>(cSimulation::getActiveSimulation()->getNedLoader()->getComponentTypes(), name);
}

void cComponentType::clearSharedParImpls()
{
    auto& d = perTypeData[this];
    for (auto & it : d.sharedParMap)
        delete it.second;
    for (auto it : d.sharedParSet)
        delete it;
    d.sharedParMap.clear();
    d.sharedParSet.clear();
}

internal::cParImpl *cComponentType::getSharedParImpl(const char *key) const
{
    auto& d = perTypeData[this];
    auto it = d.sharedParMap.find(key);
    return it == d.sharedParMap.end() ? nullptr : it->second;
}

void cComponentType::putSharedParImpl(const char *key, cParImpl *value)
{
    auto& d = perTypeData[this];
    ASSERT(d.sharedParMap.find(key) == d.sharedParMap.end());  // not yet in there
    value->setIsShared(true);
    d.sharedParMap[key] = value;
}

// cannot go inline due to declaration order
bool cComponentType::Less::operator()(cParImpl *a, cParImpl *b) const
{
    return a->compare(b) < 0;
}

internal::cParImpl *cComponentType::getSharedParImpl(cParImpl *value) const
{
    auto& d = perTypeData[this];
    auto it = d.sharedParSet.find(value);
    return it == d.sharedParSet.end() ? nullptr : *it;
}

void cComponentType::putSharedParImpl(cParImpl *value)
{
    auto& d = perTypeData[this];
    ASSERT(d.sharedParSet.find(value) == d.sharedParSet.end());  // not yet in there
    value->setIsShared(true);
    d.sharedParSet.insert(value);
}

bool cComponentType::isAvailable()
{
    std::lock_guard<std::mutex> guard(mutex);

    if (!availabilityTested) {
        const char *className = getImplementationClassName();
        available = classes.getInstance()->lookup(className) != nullptr;
        availabilityTested = true;
    }
    return available;
}

void cComponentType::checkSignal(simsignal_t signalID, SimsignalType type, cObject *obj)
{
    // check that this signal is allowed
    auto& d = perTypeData[this];
    std::map<simsignal_t, SignalDesc>::const_iterator it = d.signalsSeen.find(signalID);
    if (it == d.signalsSeen.end()) {
        // ignore built-in signals
        if (signalID == PRE_MODEL_CHANGE || signalID == POST_MODEL_CHANGE)
            return;

        // find @signal property for this signal
        const char *signalName = cComponent::getSignalName(signalID);
        cProperty *prop = getSignalDeclaration(signalName);
        if (!prop)
            throw cRuntimeError("Undeclared signal '%s' emitted (@signal missing from NED file?)", signalName);

        // found; extract info from it, and add signal to signalsSeen
        const char *declaredType = prop->getValue("type");
        SignalDesc& desc = d.signalsSeen[signalID];
        const char *typeWhereSignalTypeWasDeclared = prop->getValueOriginType("type");
        desc.type = !declaredType ? SIMSIGNAL_UNDEF : getSignalType(declaredType, SIMSIGNAL_OBJECT);
        desc.objectType = nullptr;
        desc.isNullable = false;
        if (desc.type == SIMSIGNAL_OBJECT) {
            // if declaredType ends in a question mark, the signal allows nullptr to be emitted as well
            if (declaredType[strlen(declaredType) - 1] == '?') {
                std::string netDeclaredType = std::string(declaredType, strlen(declaredType) - 1);
                desc.objectType = lookupClass(netDeclaredType.c_str(), typeWhereSignalTypeWasDeclared);
                desc.isNullable = true;
            }
            else {
                desc.objectType = lookupClass(declaredType, typeWhereSignalTypeWasDeclared);
            }
            if (!desc.objectType)
                throw cRuntimeError("Wrong type '%s' in the @signal[%s] property in the '%s' NED type, "
                                    "should be one of: long, unsigned long, double, simtime_t, string, or a "
                                    "registered class name optionally followed by a question mark",
                        declaredType, prop->getIndex(), getFullName());
        }
        it = d.signalsSeen.find(signalID);
    }

    // check data type
    const SignalDesc& desc = it->second;
    if (type == SIMSIGNAL_OBJECT) {
        if (desc.type == SIMSIGNAL_OBJECT) {
            if (desc.objectType && !desc.objectType->isInstance(obj)) {
                cITimestampedValue *timestampedValue = dynamic_cast<cITimestampedValue *>(obj);
                cObject *innerObj;
                if (!timestampedValue) {
                    if (obj)
                        throw cRuntimeError("Signal '%s' emitted with wrong class (%s does not subclass from %s as declared)",
                                cComponent::getSignalName(signalID), obj->getClassName(), desc.objectType->getFullName());
                    else if (!desc.isNullable)
                        throw cRuntimeError("Signal '%s' emitted a nullptr (specify 'type=%s?' in @signal to allow nullptr)",
                                cComponent::getSignalName(signalID), desc.objectType->getFullName());
                }
                else if (timestampedValue->getValueType(signalID) != SIMSIGNAL_OBJECT)
                    throw cRuntimeError("Signal '%s' emitted as timestamped value with wrong type (%s, but object expected)",
                            cComponent::getSignalName(signalID), getSignalTypeName(timestampedValue->getValueType(signalID)));
                else if ((innerObj = timestampedValue->objectValue(signalID)) == nullptr) {
                    if (!desc.isNullable)
                        throw cRuntimeError("Signal '%s' emitted as timestamped value with nullptr in it (specify 'type=%s?' in @signal to allow nullptr)",
                                cComponent::getSignalName(signalID), desc.objectType->getFullName());
                }
                else if (!desc.objectType->isInstance(innerObj))
                    throw cRuntimeError("Signal '%s' emitted as timestamped value with wrong class in it (%s does not subclass from %s as declared)",
                            cComponent::getSignalName(signalID), innerObj->getClassName(), desc.objectType->getFullName());
            }
        }
        else if (desc.type != SIMSIGNAL_UNDEF) {
            // additionally allow time-stamped value of the appropriate type
            cITimestampedValue *timestampedValue = dynamic_cast<cITimestampedValue *>(obj);
            if (!timestampedValue)
                throw cRuntimeError("Signal '%s' emitted with wrong data type (expected=%s, actual=%s)",
                        cComponent::getSignalName(signalID), getSignalTypeName(desc.type), obj ? obj->getClassName() : "nullptr");
            SimsignalType actualType = timestampedValue->getValueType(signalID);
            if (timestampedValue->getValueType(signalID) != desc.type)
                throw cRuntimeError("Signal '%s' emitted as timestamped value with wrong data type (expected=%s, actual=%s)",
                        cComponent::getSignalName(signalID), getSignalTypeName(desc.type), getSignalTypeName(actualType));
        }
    }
    else if (type != desc.type && desc.type != SIMSIGNAL_UNDEF) {
        throw cRuntimeError("Signal '%s' emitted with wrong data type (expected=%s, actual=%s)",
                cComponent::getSignalName(signalID), getSignalTypeName(desc.type), getSignalTypeName(type));
    }
}

cProperty *cComponentType::getSignalDeclaration(const char *signalName)
{
    std::vector<const char *> declaredSignalNames = getProperties()->getIndicesFor("signal");
    unsigned int i;
    for (i = 0; i < declaredSignalNames.size(); i++) {
        const char *declaredSignalName = declaredSignalNames[i];
        if (declaredSignalName == nullptr)
            continue;  // skip index-less @signal
        if (strcmp(signalName, declaredSignalName) == 0)
            break;
        if (PatternMatcher::containsWildcards(declaredSignalName) &&
            PatternMatcher(declaredSignalName, false, true, true).matches(signalName))
            break;
    }
    bool found = (i != declaredSignalNames.size());
    return found ? getProperties()->get("signal", declaredSignalNames[i]) : nullptr;
}

cObjectFactory *cComponentType::lookupClass(const char *className, const char *sourceType) const
{
    std::string cxxNamespace = getCxxNamespaceForType(sourceType);
    return cObjectFactory::find(className, cxxNamespace.c_str(), true);
}

const char *cComponentType::getSourceFileDirectory() const
{
    std::lock_guard<std::mutex> guard(mutex);

    if (!sourceFileDirectoryCached) {
        const char *fname = getSourceFileName();
        sourceFileDirectory = fname ? directoryOf(fname) : "";
        sourceFileDirectoryCached = true;
    }
    return sourceFileDirectory.empty() ? nullptr : sourceFileDirectory.c_str();
}

//----

cModuleType::cModuleType(const char *qname) : cComponentType(qname)
{
}

cModule *cModuleType::create(const char *moduleName, cModule *parentModule, int index)
{
    if (!parentModule)
        throw cRuntimeError("cModuleType::create(): parentModule may not be nullptr, use other create() overload to create root module");
    return doCreate(parentModule->getSimulation(), parentModule, moduleName, index);
}

cModule *cModuleType::create(const char *moduleName, cSimulation *simulation)
{
    return doCreate(simulation, nullptr, moduleName);
}

cModule *cModuleType::doCreate(cSimulation *simulation, cModule *parentModule, const char *moduleName, int index)
{
    cEnvir *envir = simulation->getEnvir();

    // create the new module object
    cTemporaryOwner tmp(cTemporaryOwner::DestructorMode::ASSERTNONE); // for collecting members of the new object
#ifdef WITH_PARSIM
    bool isLocal = simulation->isParsimEnabled() ? simulation->getParsimPartition()->isModuleLocal(parentModule, moduleName, index) : true;
    cModule *module = isLocal ? createModuleObject() : new cPlaceholderModule();
#else
    cModule *module = createModuleObject();
#endif
    tmp.restoreOriginalOwner();
    tmp.drop(module);
    module->takeAllObjectsFrom(&tmp);

    // set up module: set parent, module type, name, vector size
    module->setComponentType(this);
    module->setInitialNameAndIndex(moduleName, index);

    // notify pre-change listeners
    if (parentModule && parentModule->hasListeners(PRE_MODEL_CHANGE)) {
        cPreModuleAddNotification tmp;
        tmp.module = module;
        tmp.parentModule = parentModule;
        parentModule->emit(PRE_MODEL_CHANGE, &tmp);
    }

    // insert into network
    try {
        if (parentModule)
            parentModule->insertSubmodule(module);
        else
            simulation->setSystemModule(module);
    }
    catch (std::exception&) {
        delete module;
        throw;
    }

    // register with cSimulation
    simulation->registerComponent(module);

    // set up RNG mapping, etc.
    envir->preconfigureComponent(module);
    simulation->getRngManager()->configureRNGs(module);

    // should be called before any gateCreated calls on this module
    EVCB.moduleCreated(module);

    // add parameters and gates to the new module;
    // note: setupGateVectors() will be called from finalizeParameters()
    addParametersAndGatesTo(module);

    // initialize canvas
    if (cCanvas::containsCanvasItems(module->getProperties()))
        module->getCanvas()->addFiguresFrom(module->getProperties());

    // let envir perform additional configuration
    envir->configureComponent(module);

    // notify post-change listeners
    if (module->hasListeners(POST_MODEL_CHANGE)) {
        cPostModuleAddNotification tmp;
        tmp.module = module;
        module->emit(POST_MODEL_CHANGE, &tmp);
    }

    // done -- if it's a compound module, buildInside() will do the rest
    return module;
}

cModule *cModuleType::instantiateModuleClass(const char *className)
{
    cObject *obj = cObjectFactory::createOne(className);
    cModule *module = dynamic_cast<cModule *>(obj);
    if (!module)
        throw cRuntimeError("Incorrect class '%s' (not a subclass of cModule) for NED module type '%s'", className, getFullName());
    ASSERT(module->isModule());

    if (isSimple()) {
        if (dynamic_cast<cSimpleModule *>(module) == nullptr)
            throw cRuntimeError("Incorrect class '%s' (not a subclass of cSimpleModule) for NED simple module type '%s'", className, getFullName());
        ASSERT(module->isSimple());
    }

    return module;
}

cModule *cModuleType::createScheduleInit(const char *name, cModule *parentModule, int index)
{
    if (!parentModule)
        throw cRuntimeError("createScheduleInit(): Parent module pointer cannot be nullptr "
                            "when creating module named '%s' of type %s", name, getFullName());
    cSimulation *simulation = parentModule->getSimulation();
    cModule *module = create(name, parentModule, index);
    module->finalizeParameters();
    module->buildInside();
    module->scheduleStart(simulation->getSimTime());
    module->callInitialize();
    return module;
}

cModuleType *cModuleType::find(const char *qname)
{
    return dynamic_cast<cModuleType *>(cComponentType::find(qname));
}

cModuleType *cModuleType::get(const char *qname)
{
    cModuleType *p = find(qname);
    if (!p) {
        const char *hint = (!qname || !strchr(qname, '.')) ? " (fully qualified type name expected)" : "";
        throw cRuntimeError("NED module type '%s' not found%s", qname, hint);
    }
    return p;
}

std::vector<cModuleType*> cModuleType::findAll(const char *name)
{
    return filter<cModuleType>(cSimulation::getActiveSimulation()->getNedLoader()->getComponentTypes(), name);
}

//----

OPP_THREAD_LOCAL cChannelType *cChannelType::idealChannelType;
OPP_THREAD_LOCAL cChannelType *cChannelType::delayChannelType;
OPP_THREAD_LOCAL cChannelType *cChannelType::datarateChannelType;

cChannelType::cChannelType(const char *qname) : cComponentType(qname)
{
}

cChannel *cChannelType::instantiateChannelClass(const char *className)
{
    cObject *obj = cObjectFactory::createOne(className);  // this won't return nullptr
    cChannel *channel = dynamic_cast<cChannel *>(obj);
    if (!channel)
        throw cRuntimeError("Incorrect class '%s' (not a subclass of cChannel) for NED channel type '%s'", className, getFullName());
    return channel;
}

cChannel *cChannelType::create(const char *name, cSimulation *simulation)
{
    // create channel object
    cTemporaryOwner tmp(cTemporaryOwner::DestructorMode::ASSERTNONE); // for collecting members of the new object
    cChannel *channel = createChannelObject();
    tmp.restoreOriginalOwner();
    tmp.drop(channel);
    channel->takeAllObjectsFrom(&tmp);

    // determine channel name
    if (!name) {
        cProperty *prop = getProperties()->get("defaultname");
        if (prop)
            name = prop->getValue(cProperty::DEFAULTKEY);
        if (!name)
            name = "channel";
    }

    // set up channel: set name, channel type, etc
    channel->setName(name);
    channel->setComponentType(this);

    if (!simulation)
        simulation = cSimulation::getActiveSimulation();
    cEnvir *envir = simulation->getEnvir();

    // register with cSimulation
    simulation->registerComponent(channel);

    // set up RNG mapping, etc.
    envir->preconfigureComponent(channel);
    simulation->getRngManager()->configureRNGs(channel);

    // add parameters to the new module
    addParametersTo(channel);

    // let envir perform additional configuration
    envir->configureComponent(channel);

    return channel;
}

cChannelType *cChannelType::find(const char *qname)
{
    return dynamic_cast<cChannelType *>(cComponentType::find(qname));
}

cChannelType *cChannelType::get(const char *qname)
{
    cChannelType *p = find(qname);
    if (!p) {
        const char *hint = (!qname || !strchr(qname, '.')) ? " (fully qualified type name expected)" : "";
        throw cRuntimeError("NED channel type '%s' not found%s", qname, hint);
    }
    return p;
}

std::vector<cChannelType*> cChannelType::findAll(const char *name)
{
    return filter<cChannelType>(cSimulation::getActiveSimulation()->getNedLoader()->getComponentTypes(), name);
}

cChannelType *cChannelType::getIdealChannelType()
{
    if (!idealChannelType) {
        idealChannelType = find("ned.IdealChannel");
        ASSERT(idealChannelType);
    }
    return idealChannelType;
}

cChannelType *cChannelType::getDelayChannelType()
{
    if (!delayChannelType) {
        delayChannelType = find("ned.DelayChannel");
        ASSERT(delayChannelType);
    }
    return delayChannelType;
}

cChannelType *cChannelType::getDatarateChannelType()
{
    if (!datarateChannelType) {
        datarateChannelType = find("ned.DatarateChannel");
        ASSERT(datarateChannelType);
    }
    return datarateChannelType;
}

cIdealChannel *cChannelType::createIdealChannel(const char *name)
{
    return cIdealChannel::create(name);
}

cDelayChannel *cChannelType::createDelayChannel(const char *name)
{
    return cDelayChannel::create(name);
}

cDatarateChannel *cChannelType::createDatarateChannel(const char *name)
{
    return cDatarateChannel::create(name);
}

}  // namespace omnetpp

