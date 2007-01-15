//=========================================================================
//  FORCEDIRECTEDPARAMETERSBASE.H - part of
//                  OMNeT++/OMNEST
//           Discrete System Simulation in C++
//
//=========================================================================

/*--------------------------------------------------------------*
  Copyright (C) 1992-2006 Andras Varga

  This file is distributed WITHOUT ANY WARRANTY. See the file
  `license' for details on this and other legal matters.
*--------------------------------------------------------------*/

#ifndef __FORCEDIRECTEDPARAMETERSBASE_H_
#define __FORCEDIRECTEDPARAMETERSBASE_H_

#include <math.h>
#include "geometry.h"

class ForceDirectedEmbedding;

/**
 * Various parameters driving the algorithm.
 */
struct ForceDirectedParameters
{
    /**
     * For bodies not initialized with a particular size.
     */
    Rs defaultBodySize;

    /**
     * For bodies not initialized with a particular mass.
     */
    double defaultBodyMass;

    /**
     * For bodies not initialized with a particular charge.
     */
    double defaultBodyCharge;

    /**
     * For springs not initialized with a particular coefficient.
     */
    double defaultSpringCoefficient;

    /**
     * Default spring repose length.
     */
    double defaultSpringReposeLength;

    /**
     * Electric repulsion force coefficient.
     */
    double electricRepulsionCoefficient;

    /**
     * Friction reduces the energy of the system. The friction force points in the opposite direction of the current velocity.
     */
    double frictionCoefficient;

    /**
     * The default time step when solution starts.
     */
    double timeStep;

    /**
     * Lower limit for time step update.
     */
    double minTimeStep;

    /**
     * Lower limit for time step update.
     */
    double maxTimeStep;

    /**
     * Multiplier used to update the time step.
     */
    double timeStepMultiplier;    

    /**
     * Lower limit of acceleration approximation difference (between a1, a2, a3 and a4 in RK-4).
     * During updating the time step this is the lower limit to accept the current time step.
     */
    double minAccelerationError;

    /**
     * Upper limit of acceleration approximation difference (between a1, a2, a3 and a4 in RK-4).
     */
    double maxAccelerationError;

    /**
     * Velocity limit during the solution.
     * When all bodies has lower velocity than this limit then the algorithm may be stopped.
     */
    double velocityRelaxLimit;

    /**
     * Acceleration limit during the solution.
     * When all bodies has lower acceleration than this limit then the algorithm may be stopped.
     */
    double accelerationRelaxLimit;

    /**
     * For force providers not initialized with a particular maximum force.
     */
    double defaultMaxForce;

    /**
     * Maximim velocity that a body may have.
     */
    double maxVelocity;

    /**
     * Maximum number of calculation cycles to run.
     */
    int maxCycle;

    /**
     * Maximum time to be spent on the calculation in milliseconds.
     * The algorithm will return after this time has been elapsed.
     */
    double maxCalculationTime;
};

/**
 * Base class for things that have position.
 */
class IPositioned {
    public:
        virtual Pt getPosition() = 0;
};

/**
 * A variable used in the differential equation.
 * The actual value of the variable is the position, the first derivative is the velocity
 * and the second derivative is the acceleration.
 */
class Variable : public IPositioned {
    protected:
        /**
         * Value of the variable.
         */
	    Pt position;

        /**
         * First derivative.
         */
	    Pt velocity;

        /**
         * Second derivative.
         */
        Pt acceleration;

        /**
         * Total applied force.
         */
        Pt force;

        /**
         * The list of all applied forces for debug purposes.
         */
	    std::vector<Pt> forces;

        /**
         * Total mass of bodies appling forces to this variable.
         */
	    double mass;

    private:
        void constructor(const Pt& position, const Pt& velocity) {
		    this->position = position;
		    this->velocity = velocity;

            mass = 0;
            force = Pt::getZero();
        }
	
    public:
	    Variable(const Pt& position) {
            constructor(position, Pt::getZero());
	    }

	    Variable(const Pt& position, const Pt& velocity) {
            constructor(position, velocity);
	    }

	    virtual Pt getPosition() {
		    return position;
	    }

	    virtual void assignPosition(const Pt& position) {
		    this->position.assign(position);
	    }

	    Pt getVelocity() {
		    return velocity;
	    }

	    virtual void assignVelocity(const Pt& velocity) {
		    this->velocity.assign(velocity);
	    }

	    virtual Pt getAcceleration() {
		    return acceleration.assign(force).divide(mass);
	    }

        double getKineticEnergy() {
            double vlen = velocity.getLength();
            return 0.5 * mass * vlen * vlen;
        }

	    void resetForce() {
            force = Pt::getZero();
	    }

	    double getMass() {
		    return mass;
	    }
    	
	    void addMass(double mass) {
		    this->mass += mass;
	    }

        Pt getForce() {
            return force;
        }

	    void addForce(const Pt& vector, double power, bool inspected = false) {
		    Pt f(vector);

            if (!f.isZero() && f.isFullySpecified()) {
                f.normalize().multiply(power);
		        force.add(f);

                if (inspected)
    		        forces.push_back(f);
            }
	    }

	    void resetForces() {
		    forces.clear();
	    }
    	
	    const std::vector<Pt>& getForces() {
		    return forces;
	    }
};

/**
 * A variable which has fix x and y coordinates but still has a free z coordinate.
 */
class PointConstrainedVariable : public Variable {
    public:
        PointConstrainedVariable(Pt position) : Variable(position) {
        }

	    virtual void assignPosition(const Pt& position) {
            this->position.z = position.z;
        }

	    virtual void assignVelocity(const Pt& velocity) {
            this->velocity.z = velocity.z;
        }

	    virtual Pt getAcceleration() {
		    return acceleration.assign(0, 0, force.z).divide(mass);
	    }
};

/**
 * Interface class for bodies.
 */
class IBody : public IPositioned {
    protected:
        ForceDirectedEmbedding *embedding;

    public:
        virtual void setForceDirectedEmbedding(ForceDirectedEmbedding *embedding) {
            this->embedding = embedding;
        }

        virtual const char *getClassName() = 0;

        virtual Rs& getSize() = 0;

        virtual double getMass() = 0;

        virtual double getCharge() = 0;

        virtual Variable *getVariable() = 0;
};

/**
 * Interface class used by the force directed embedding to generate forces among bodies.
 */
class IForceProvider {
    protected:
        ForceDirectedEmbedding *embedding;

        double maxForce;

    public:
        IForceProvider() {
            maxForce = -1;
        }

        virtual void setForceDirectedEmbedding(ForceDirectedEmbedding *embedding) {
            this->embedding = embedding;

// TODO:
            //if (maxForce == -1)
            //    maxForce = embedding->parameters.defaultMaxForce;
        }

	    double getMaxForce() {
		    return maxForce;
	    }
    	
	    double getValidForce(double force) {
		    ASSERT(force >= 0);
            return std::min(maxForce, force);
	    }
    	
	    double getValidSignedForce(double force) {
            if (force < 0)
                return -getValidForce(fabs(force));
            else
                return getValidForce(fabs(force));
	    }

        Pt getStandardDistanceAndVector(IBody *body1, IBody *body2, double &distance) {
            Pt vector = Pt(body1->getPosition()).subtract(body2->getPosition());
            // TODO: subtract body sizes (intersected along the vector) to avoid overlapping huge bodies
            distance = vector.getLength();
            return vector;
        }

        // TODO: allow infinite sizes and calculate distance by that?
        Pt getStandardHorizontalDistanceAndVector(IBody *body1, IBody *body2, double &distance) {
            Pt vector = Pt(body1->getPosition()).subtract(body2->getPosition());
            vector.y = 0;
            vector.z = 0;
            distance = vector.getLength();
            return vector;
        }

        Pt getStandardVerticalDistanceAndVector(IBody *body1, IBody *body2, double &distance) {
            Pt vector = Pt(body1->getPosition()).subtract(body2->getPosition());
            vector.x = 0;
            vector.z = 0;
            distance = vector.getLength();
            return vector;
        }

        Pt getSlipperyDistanceAndVector(IBody *body1, IBody *body2, double &distance) {
            Rc rc1 = Rc::getRcFromCenterSize(body1->getPosition(), body1->getSize());
            Rc rc2 = Rc::getRcFromCenterSize(body2->getPosition(), body2->getSize());
            Ln ln = rc1.getBasePlaneProjectionDistance(rc2, distance);
            Pt vector = ln.begin;
            vector.subtract(ln.end);
            vector.setNaNToZero();
            return vector;
        }

        virtual const char *getClassName() = 0;

        virtual void applyForces() = 0;

        virtual double getPotentialEnergy() = 0;
};

#endif
