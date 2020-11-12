//==========================================================================
//   CHASHER.H  - part of
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

#ifndef __OMNETPP_CHASHER_H
#define __OMNETPP_CHASHER_H

#include <cstdint>
#include <cstring>
#include <cstdlib>
#include "simkerneldefs.h"
#include "cobject.h"
#include "cexception.h"

namespace omnetpp {


/**
 * @brief Utility class to calculate the hash of some data.
 *
 * We are trying to achieve that the same calls gives the same hash
 * on a 32-bit machine and on a 64-bit machine. Longs can be either 32-bit or
 * 64-bit, so we always convert them to 64 bits. We do not try to convert
 * endianness, it would be too costly.
 */
class SIM_API cHasher : noncopyable
{
  private:
    uint32_t value;

    void merge(uint32_t x) {
        // rotate value left by one bit, and xor with new data
        value = ((value << 1) | (value >> 31)) ^ x;
    }

    void merge2(uint64_t x) {
        merge((uint32_t)x);
        merge((uint32_t)(x>>32));
    }

  public:
    /**
     * Constructor.
     */
    cHasher() {ASSERT(sizeof(uint32_t)==4); ASSERT(sizeof(double)==8); value = 0;}

    /** @name Updating the hash */
    //@{
    void reset() {value = 0;}
    void add(const char *p, size_t length);
    cHasher& operator<<(char d)           {merge((uint32_t)d); return *this;}
    cHasher& operator<<(short d)          {merge((uint32_t)d); return *this;}
    cHasher& operator<<(int d)            {merge((uint32_t)d); return *this;}
    cHasher& operator<<(long d)           {int64_t tmp=d; merge2((uint64_t)tmp); return *this;}
    cHasher& operator<<(long long d)      {merge2((uint64_t)d); return *this;}
    cHasher& operator<<(unsigned char d)  {merge((uint32_t)d); return *this;}
    cHasher& operator<<(unsigned short d) {merge((uint32_t)d); return *this;}
    cHasher& operator<<(unsigned int d)   {merge((uint32_t)d); return *this;}
    cHasher& operator<<(unsigned long d)  {uint64_t tmp=d; merge2(tmp); return *this;}
    cHasher& operator<<(unsigned long long d)  {merge2(d); return *this;}
    // note: safe(r) type punning, see http://cocoawithlove.decenturl.com/type-punning
    cHasher& operator<<(double d)         {union _ {double d; uint64_t i;}; merge2(((union _ *)&d)->i); return *this;}
    cHasher& operator<<(simtime_t t)      {merge2(t.raw()); return *this;}
    cHasher& operator<<(const char *s)    {if (s) add(s, strlen(s)+1); else *this << 0; return *this;}
    //@}

    /** @name Obtaining the result */
    //@{
    /**
     * Returns the hash value.
     */
    uint32_t getHash() const {return value;}

    /**
     * Converts the given string to a numeric hash value. The object is
     * not changed. Throws an error if the string does not contain a valid
     * hash.
     */
    uint32_t parse(const char *hash) const;

    /**
     * Parses the given hash string, and compares it to the stored hash.
     */
    bool equals(const char *hash) const;

    /**
     * Returns the textual representation (hex string) of the stored hash.
     */
    std::string str() const;
    //@}
};

}  // namespace omnetpp

#endif


