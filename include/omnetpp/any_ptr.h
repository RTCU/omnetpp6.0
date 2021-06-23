//==========================================================================
//   ANY_PTR.H  - part of
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

#ifndef __OMNETPP_ANY_PTR_H
#define __OMNETPP_ANY_PTR_H

#include <typeinfo>
#include <cassert>
#include "simkerneldefs.h"
#include "cexception.h"
#include "simutil.h"

namespace omnetpp {

/**
 * @brief A type-safe equivalent of the void* pointer.
 *
 * any_ptr accepts a pointer of any type, and keeps the memory address
 * along with the pointer's type. The pointer can be extracted from
 * any_ptr with the exact same type it was used to store it. This was
 * achieved with the use of templates methods, typeid() and std::type_info.
 *
 * The primary use of any_ptr is with cClassDescriptor.
 *
 * IMPORTANT: Since std::type_info knows nothing about inheritance relationships,
 * any_ptr cannot perform any upcast or downcast when extracting the pointer.
 * The following code snippet raises a runtime error:
 *
 * <pre>
 * cMessage *msg = new cMessage();
 * any_ptr ptr = any_ptr(msg);
 * cObject *obj = ptr.get<cObject>(); // ==> "Attempt to read any_ptr(cMessage) as cObject*"
 * </pre>
 *
 * When casting is important (~always), the toAnyPtr()/fromAnyPtr() functions should be
 * used for converting an object pointer to and from any_ptr. Most toAnyPtr()/fromAnyPtr()
 * functions are generated by the message compiler from .msg files.
 *
 * Updated code snippet:
 *
 * <pre>
 * cMessage *msg = new cMessage();
 * any_ptr ptr = toAnyPtr(msg);
 * cObject *obj = fromAnyPtr<cObject>(ptr); // works as expected
 * </pre>
 *
 * @ingroup Utilities
 */
class SIM_API any_ptr
{
  private:
    void *ptr;
    const std::type_info *type;
  private:
    void checkType(const std::type_info& asType) const;
  public:
    any_ptr() : any_ptr(nullptr) {}

    explicit any_ptr(std::nullptr_t) : ptr(nullptr), type(&typeid(std::nullptr_t)) {}

    template<typename T>
    explicit any_ptr(T *ptr) : ptr(ptr), type(&typeid(T*)) {}

    template<typename T>
    explicit any_ptr(const T *ptr) : any_ptr(const_cast<T*>(ptr)) {}

    any_ptr(const any_ptr &other) : ptr(other.ptr), type(other.type) {}

    const any_ptr& operator=(const any_ptr& other) {ptr=other.ptr; type=other.type; return *this;}

    bool operator==(const any_ptr& other) const {return ptr==other.ptr && type==other.type;}
    bool operator!=(const any_ptr& other) const {return !operator==(other);}

    bool operator==(std::nullptr_t) const {return ptr==nullptr;}
    bool operator!=(std::nullptr_t) const {return ptr!=nullptr;}

    template<typename T>
    bool contains() const {return typeid(T*) == *type;}

    void *raw() const {return ptr;}
    const std::type_info& pointerType() const {return *type;}
    const char *pointerTypeName() const {return opp_typename(*type);}

    template<typename T>
    T *get() { checkType(typeid(T*)); return reinterpret_cast<T*>(ptr); }

    template<typename T>
    const T *get() const { checkType(typeid(T*)); return reinterpret_cast<const T*>(ptr);}

    std::string str() const;
};

class cObject;

template<typename T>
T *fromAnyPtr(any_ptr object) = delete; // =delete prevents undeclared specializations

template<>
inline omnetpp::cObject *fromAnyPtr(any_ptr ptr) { return ptr.get<omnetpp::cObject>(); }

inline any_ptr toAnyPtr(const cObject *obj) { return any_ptr(const_cast<cObject*>(obj)); }

}  // namespace omnetpp

#endif

