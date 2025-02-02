/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper licenses this file to you under the Apache License, 
 *  Version 2.0 (the "License"); you may not use this file except in 
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.routing.util;

import com.graphhopper.reader.OSMWay;
import com.graphhopper.reader.OSMRelation;
import static com.graphhopper.routing.util.BikeCommonFlagEncoder.PriorityCode.*;
import com.graphhopper.util.Helper;
import com.graphhopper.util.InstructionAnnotation;
import com.graphhopper.util.Translation;

import java.util.*;

/**
 * Defines bit layout of bicycles (not motorbikes) for speed, access and relations (network).
 * <p/>
 * @author Peter Karich
 * @author Nop
 * @author ratrun
 */
public class BikeCommonFlagEncoder extends AbstractFlagEncoder
{
    protected static final int PUSHING_SECTION_SPEED = 4;
    private long unpavedBit = 0;
    // Pushing section heighways are parts where you need to get off your bike and push it (German: Schiebestrecke)
    protected final HashSet<String> pushingSections = new HashSet<String>();
    protected final HashSet<String> oppositeLanes = new HashSet<String>();
    protected final Set<String> preferHighwayTags = new HashSet<String>();
    protected final Set<String> avoidHighwayTags = new HashSet<String>();
    protected final Set<String> unpavedSurfaceTags = new HashSet<String>();
    private final Map<String, Integer> trackTypeSpeed = new HashMap<String, Integer>();
    private final Map<String, Integer> surfaceSpeed = new HashMap<String, Integer>();
    private final Set<String> roadValues = new HashSet<String>();
    private final Map<String, Integer> highwaySpeed = new HashMap<String, Integer>();
    //Convert network tag of bicycle routes into a way route code stored in the wayMAP
    private final Map<String, Integer> bikeNetworkToCode = new HashMap<String, Integer>();
    EncodedValue relationCodeEncoder;
    private EncodedValue wayTypeEncoder;
    private EncodedValue preferWayEncoder;

    /**
     * Should be only instantied via EncodingManager
     */
    protected BikeCommonFlagEncoder()
    {
        this(4, 2);
    }

    protected BikeCommonFlagEncoder( int speedBits, double speedFactor )
    {
        super(speedBits, speedFactor);
        // strict set, usually vehicle and agricultural/forestry are ignored by cyclists
        restrictions = new ArrayList<String>(Arrays.asList("bicycle", "access"));
        restrictedValues.add("private");
        restrictedValues.add("no");
        restrictedValues.add("restricted");

        intendedValues.add("yes");
        intendedValues.add("designated");
        intendedValues.add("official");
        intendedValues.add("permissive");

        oppositeLanes.add("opposite");
        oppositeLanes.add("opposite_lane");
        oppositeLanes.add("opposite_track");

        blockByDefault = false;
        potentialBarriers.add("gate");
        // potentialBarriers.add("lift_gate");
        potentialBarriers.add("swing_gate");

        absoluteBarriers.add("kissing_gate");
        absoluteBarriers.add("stile");
        absoluteBarriers.add("turnstile");

        unpavedSurfaceTags.add("unpaved");
        unpavedSurfaceTags.add("gravel");
        unpavedSurfaceTags.add("ground");
        unpavedSurfaceTags.add("dirt");
        unpavedSurfaceTags.add("grass");
        unpavedSurfaceTags.add("compacted");
        unpavedSurfaceTags.add("earth");
        unpavedSurfaceTags.add("fine_gravel");
        unpavedSurfaceTags.add("grass_paver");
        unpavedSurfaceTags.add("ice");
        unpavedSurfaceTags.add("mud");
        unpavedSurfaceTags.add("salt");
        unpavedSurfaceTags.add("sand");
        unpavedSurfaceTags.add("wood");

        roadValues.add("living_street");
        roadValues.add("road");
        roadValues.add("service");
        roadValues.add("unclassified");
        roadValues.add("residential");
        roadValues.add("trunk");
        roadValues.add("trunk_link");
        roadValues.add("primary");
        roadValues.add("primary_link");
        roadValues.add("secondary");
        roadValues.add("secondary_link");
        roadValues.add("tertiary");
        roadValues.add("tertiary_link");

        setTrackTypeSpeed("grade1", 18); // paved
        setTrackTypeSpeed("grade2", 12); // now unpaved ...
        setTrackTypeSpeed("grade3", 8);
        setTrackTypeSpeed("grade4", 6);
        setTrackTypeSpeed("grade5", 4); // like sand/grass     

        setSurfaceSpeed("paved", 18);
        setSurfaceSpeed("asphalt", 18);
        setSurfaceSpeed("cobblestone", 8);
        setSurfaceSpeed("cobblestone:flattened", 10);
        setSurfaceSpeed("sett", 10);
        setSurfaceSpeed("concrete", 18);
        setSurfaceSpeed("concrete:lanes", 16);
        setSurfaceSpeed("concrete:plates", 16);
        setSurfaceSpeed("paving_stones", 12);
        setSurfaceSpeed("paving_stones:30", 12);
        setSurfaceSpeed("unpaved", 14);
        setSurfaceSpeed("compacted", 16);
        setSurfaceSpeed("dirt", 10);
        setSurfaceSpeed("earth", 12);
        setSurfaceSpeed("fine_gravel", 18);
        setSurfaceSpeed("grass", 8);
        setSurfaceSpeed("grass_paver", 8);
        setSurfaceSpeed("gravel", 12);
        setSurfaceSpeed("ground", 12);
        setSurfaceSpeed("ice", PUSHING_SECTION_SPEED / 2);
        setSurfaceSpeed("metal", 10);
        setSurfaceSpeed("mud", 10);
        setSurfaceSpeed("pebblestone", 16);
        setSurfaceSpeed("salt", 6);
        setSurfaceSpeed("sand", 6);
        setSurfaceSpeed("wood", 6);

        setHighwaySpeed("living_street", 6);
        setHighwaySpeed("steps", PUSHING_SECTION_SPEED / 2);

        setHighwaySpeed("cycleway", 18);
        setHighwaySpeed("path", 18);
        setHighwaySpeed("footway", 6);
        setHighwaySpeed("pedestrian", 6);
        setHighwaySpeed("track", 12);
        setHighwaySpeed("service", 14);
        setHighwaySpeed("residential", 18);
        // no other highway applies:
        setHighwaySpeed("unclassified", 16);
        // unknown road:
        setHighwaySpeed("road", 12);

        setHighwaySpeed("trunk", 18);
        setHighwaySpeed("trunk_link", 18);
        setHighwaySpeed("primary", 18);
        setHighwaySpeed("primary_link", 18);
        setHighwaySpeed("secondary", 18);
        setHighwaySpeed("secondary_link", 18);
        setHighwaySpeed("tertiary", 18);
        setHighwaySpeed("tertiary_link", 18);

        // special case see tests and #191
        setHighwaySpeed("motorway", 18);
        setHighwaySpeed("motorway_link", 18);
        avoidHighwayTags.add("motorway");
        avoidHighwayTags.add("motorway_link");

        setCyclingNetworkPreference("icn", PriorityCode.BEST.getValue());
        setCyclingNetworkPreference("ncn", PriorityCode.BEST.getValue());
        setCyclingNetworkPreference("rcn", PriorityCode.VERY_NICE.getValue());
        setCyclingNetworkPreference("lcn", PriorityCode.PREFER.getValue());
        setCyclingNetworkPreference("mtb", PriorityCode.UNCHANGED.getValue());

        setCyclingNetworkPreference("deprecated", PriorityCode.AVOID_AT_ALL_COSTS.getValue());
    }

    @Override
    public int defineWayBits( int index, int shift )
    {
        // first two bits are reserved for route handling in superclass
        shift = super.defineWayBits(index, shift);
        speedEncoder = new EncodedDoubleValue("Speed", shift, speedBits, speedFactor, highwaySpeed.get("cycleway"), 30);
        shift += speedEncoder.getBits();

        unpavedBit = 1L << shift++;
        // 2 bits
        wayTypeEncoder = new EncodedValue("WayType", shift, 2, 1, 0, 3, true);
        shift += wayTypeEncoder.getBits();

        preferWayEncoder = new EncodedValue("PreferWay", shift, 3, 1, 0, 7);
        shift += preferWayEncoder.getBits();

        return shift;
    }

    @Override
    public int defineRelationBits( int index, int shift )
    {
        relationCodeEncoder = new EncodedValue("RelationCode", shift, 3, 1, 0, 7);
        return shift + relationCodeEncoder.getBits();
    }

    @Override
    public long acceptWay( OSMWay way )
    {
        String highwayValue = way.getTag("highway");
        if (highwayValue == null)
        {
            if (way.hasTag("route", ferries))
            {
                // if bike is NOT explictly tagged allow bike but only if foot is not specified
                String bikeTag = way.getTag("bicycle");
                if (bikeTag == null && !way.hasTag("foot") || "yes".equals(bikeTag))
                    return acceptBit | ferryBit;
            }
            return 0;
        }

        if (!highwaySpeed.containsKey(highwayValue))
            return 0;

        // use the way if it is tagged for bikes
        if (way.hasTag("bicycle", intendedValues))
            return acceptBit;

        // accept only if explicitely tagged for bike usage
        if ("motorway".equals(highwayValue) || "motorway_link".equals(highwayValue))
            return 0;

        if (way.hasTag("motorroad", "yes"))
            return 0;

        // do not use fords with normal bikes, flagged fords are in included above
        if (way.hasTag("highway", "ford") || way.hasTag("ford"))
            return 0;

        // check access restrictions
        if (way.hasTag(restrictions, restrictedValues))
            return 0;

        // do not accept railways (sometimes incorrectly mapped!)
        if (way.hasTag("railway") && !way.hasTag("railway", acceptedRailways))
            return 0;

        String sacScale = way.getTag("sac_scale");
        if (sacScale != null)
        {
            // other scales are nearly impossible by bike, see http://wiki.openstreetmap.org/wiki/Key:sac_scale
            if (!"hiking".equals(sacScale) && !"mountain_hiking".equals(sacScale))
                return 0;
        }
        return acceptBit;
    }

    @Override
    public long handleRelationTags( OSMRelation relation, long oldRelationFlags )
    {
        int code = 0;
        if (relation.hasTag("route", "bicycle"))
        {
            Integer val = bikeNetworkToCode.get(relation.getTag("network"));
            if (val != null)
                code = val;
        } else if (relation.hasTag("route", "ferry"))
        {
            code = PriorityCode.AVOID_IF_POSSIBLE.getValue();
        }

        int oldCode = (int) relationCodeEncoder.getValue(oldRelationFlags);
        if (oldCode < code)
            return relationCodeEncoder.setValue(0, code);
        return oldRelationFlags;
    }

    @Override
    public long handleWayTags( OSMWay way, long allowed, long relationFlags )
    {
        if (!isAccept(allowed))
            return 0;

        long encoded = 0;
        if (!isFerry(allowed))
        {
            double speed = getSpeed(way);
            int priorityFromRelation = 0;
            if (relationFlags != 0)
                priorityFromRelation = (int) relationCodeEncoder.getValue(relationFlags);

            encoded = setLong(encoded, K_PRIORITY_LONG, handlePriority(way, priorityFromRelation));

            // bike maxspeed handling is different from car as we don't increase speed
            speed = reduceToMaxSpeed(way, speed);
            encoded = handleSpeed(way, speed, encoded);
            encoded = handleBikeRelated(way, encoded, relationFlags > UNCHANGED.getValue());

        } else
        {
            encoded = handleFerryTags(way,
                    highwaySpeed.get("living_street"),
                    highwaySpeed.get("track"),
                    highwaySpeed.get("primary"));
            encoded |= directionBitMask;
        }
        return encoded;
    }

    protected double reduceToMaxSpeed( OSMWay way, double speed )
    {
        double maxSpeed = getMaxSpeed(way);
        // apply only if smaller maxSpeed
        if (maxSpeed >= 0)
        {
            if (maxSpeed < speed)
                return maxSpeed * 0.9;
        }
        return speed;
    }

    int getSpeed( OSMWay way )
    {
        int speed = PUSHING_SECTION_SPEED;
        String s = way.getTag("surface");
        if (!Helper.isEmpty(s))
        {
            Integer sInt = surfaceSpeed.get(s);
            if (sInt != null)
                speed = sInt;
        } else
        {
            String tt = way.getTag("tracktype");
            if (!Helper.isEmpty(tt))
            {
                Integer tInt = trackTypeSpeed.get(tt);
                if (tInt != null)
                    speed = tInt;
            } else
            {
                String highway = way.getTag("highway");
                if (!Helper.isEmpty(highway))
                {
                    Integer hwInt = highwaySpeed.get(highway);
                    if (hwInt != null)
                    {
                        if (way.getTag("service") == null)
                            speed = hwInt;
                        else
                            speed = highwaySpeed.get("living_street");
                    }
                }
            }
        }

        // Until now we assumed that the way is no pusing section
        // Now we check, but only in case that our speed is bigger compared to the PUSHING_SECTION_SPEED
        if ((speed > PUSHING_SECTION_SPEED)
                && (!way.hasTag("bicycle", intendedValues) && way.hasTag("highway", pushingSections)))
        {
            if (way.hasTag("highway", "steps"))
                speed = PUSHING_SECTION_SPEED / 2;
            else
                speed = PUSHING_SECTION_SPEED;
        }

        return speed;
    }

    @Override
    public InstructionAnnotation getAnnotation( long flags, Translation tr )
    {
        int paveType = 0; // paved
        if (isBool(flags, K_UNPAVED))
            paveType = 1; // unpaved        

        int wayType = (int) wayTypeEncoder.getValue(flags);
        String wayName = getWayName(paveType, wayType, tr);
        return new InstructionAnnotation(0, wayName);
    }

    String getWayName( int pavementType, int wayType, Translation tr )
    {
        String pavementName = "";
        if (pavementType == 1)
            pavementName = tr.tr("unpaved");

        String wayTypeName = "";
        switch (wayType)
        {
            case 0:
                wayTypeName = tr.tr("road");
                break;
            case 1:
                wayTypeName = tr.tr("off_bike");
                break;
            case 2:
                wayTypeName = tr.tr("cycleway");
                break;
            case 3:
                wayTypeName = tr.tr("way");
                break;
        }

        if (pavementName.isEmpty())
        {
            if (wayType == 0 || wayType == 3)
                return "";
            return wayTypeName;
        } else
        {
            if (wayTypeName.isEmpty())
                return pavementName;
            else
                return wayTypeName + ", " + pavementName;
        }
    }

    /**
     * In this method we prefer cycleways or roads with designated bike access and avoid big roads
     * or roads with trams or pedestrian.
     * <p>
     * @return new priority based on priorityFromRelation and on the tags in OSMWay.
     */
    protected int handlePriority( OSMWay way, int priorityFromRelation )
    {
        TreeMap<Double, Integer> weightToPrioMap = new TreeMap<Double, Integer>();
        if (priorityFromRelation == 0)
            weightToPrioMap.put(0d, UNCHANGED.getValue());
        else
            weightToPrioMap.put(110d, priorityFromRelation);

        collect(way, weightToPrioMap);

        // pick priority with biggest order value
        return weightToPrioMap.lastEntry().getValue();
    }

    /**
     * @param weightToPrioMap associate a weight with every priority. This sorted map allows
     * subclasses to 'insert' more important priorities as well as overwrite determined priorities.
     */
    void collect( OSMWay way, TreeMap<Double, Integer> weightToPrioMap )
    {
        String service = way.getTag("service");
        String highway = way.getTag("highway");
        if (way.hasTag("bicycle", "designated"))
            weightToPrioMap.put(100d, PREFER.getValue());
        if ("cycleway".equals(highway))
            weightToPrioMap.put(100d, VERY_NICE.getValue());

        if (preferHighwayTags.contains(highway))
        {
            weightToPrioMap.put(40d, PREFER.getValue());
            if (way.hasTag("tunnel", intendedValues))
                weightToPrioMap.put(40d, UNCHANGED.getValue());
        }

        if (pushingSections.contains(highway) || "parking_aisle".equals(service))
            weightToPrioMap.put(50d, AVOID_IF_POSSIBLE.getValue());

        double maxSpeed = getMaxSpeed(way);
        if (avoidHighwayTags.contains(highway) || maxSpeed > 80)
        {
            weightToPrioMap.put(50d, REACH_DEST.getValue());
            if (way.hasTag("tunnel", intendedValues))
                weightToPrioMap.put(50d, AVOID_AT_ALL_COSTS.getValue());
        }

        if (way.hasTag("railway", "tram"))
            weightToPrioMap.put(50d, AVOID_AT_ALL_COSTS.getValue());
    }

    /**
     * Handle surface and wayType encoding
     */
    long handleBikeRelated( OSMWay way, long encoded, boolean partOfCycleRelation )
    {
        String surfaceTag = way.getTag("surface");
        String highway = way.getTag("highway");
        String trackType = way.getTag("tracktype");

        // Populate bits at wayTypeMask with wayType            
        WayType wayType = WayType.OTHER_SMALL_WAY;
        boolean isPusingSection = isPushingSection(way);
        if (isPusingSection && !partOfCycleRelation || "steps".equals(highway))
            wayType = WayType.PUSHING_SECTION;

        if ("track".equals(highway) && (trackType == null || !"grade1".equals(trackType))
                || "path".equals(highway) && surfaceTag == null
                || unpavedSurfaceTags.contains(surfaceTag))
        {
            encoded = setBool(encoded, K_UNPAVED, true);
        }

        if (way.hasTag("bicycle", intendedValues))
        {
            if (isPusingSection && !way.hasTag("bicycle", "designated"))
                wayType = WayType.OTHER_SMALL_WAY;
            else
                wayType = WayType.CYCLEWAY;
        } else if ("cycleway".equals(highway))
            wayType = WayType.CYCLEWAY;
        else if (roadValues.contains(highway))
            wayType = WayType.ROAD;

        return wayTypeEncoder.setValue(encoded, wayType.getValue());
    }

    /**
     * Reports wether this edge is unpaved.
     */
    public static final int K_UNPAVED = 100;
    public static final int K_PRIORITY = 101, K_PRIORITY_LONG = 102;

    @Override
    public long setBool( long flags, int key, boolean value )
    {
        switch (key)
        {
            case K_UNPAVED:
                return value ? flags | unpavedBit : flags & ~unpavedBit;
            default:
                return super.setBool(flags, key, value);
        }
    }

    @Override
    public boolean isBool( long flags, int key )
    {
        switch (key)
        {
            case K_UNPAVED:
                return (flags & unpavedBit) != 0;
            default:
                return super.isBool(flags, key);
        }
    }

    @Override
    public double getDouble( long flags, int key )
    {
        switch (key)
        {
            case K_PRIORITY:
                double prio = preferWayEncoder.getValue(flags);
                if (prio == 0)
                    return (double) UNCHANGED.getValue() / BEST.getValue();

                return prio / BEST.getValue();
            default:
                return super.getDouble(flags, key);
        }
    }

    @Override
    public long getLong( long flags, int key )
    {
        switch (key)
        {
            case K_PRIORITY_LONG:
                return preferWayEncoder.getValue(flags);
            default:
                return super.getLong(flags, key);
        }
    }

    @Override
    public long setLong( long flags, int key, long value )
    {
        switch (key)
        {
            case K_PRIORITY_LONG:
                return preferWayEncoder.setValue(flags, value);
            default:
                return super.setLong(flags, key, value);
        }
    }

    boolean isPushingSection( OSMWay way )
    {
        return way.hasTag("highway", pushingSections);
    }

    protected long handleSpeed( OSMWay way, double speed, long encoded )
    {
        encoded = setSpeed(encoded, speed);

        // handle oneways
        if ((way.hasTag("oneway", oneways) || way.hasTag("junction", "roundabout"))
                && !way.hasTag("oneway:bicycle", "no")
                && !way.hasTag("cycleway", oppositeLanes))
        {
            if (way.hasTag("oneway", "-1"))
                encoded |= backwardBit;
            else
                encoded |= forwardBit;

        } else
        {
            encoded |= directionBitMask;
        }
        return encoded;
    }

    enum PriorityCode
    {
        /* Inspired by http://wiki.openstreetmap.org/wiki/Class:bicycle
         "-3" = Avoid at all cost. 
         "-2" = Only use to reach your destination, not well suited. 
         "-1" = Better take another way 
         "0" = as well as other ways around. 
         Try to to avoid using 0 but decide on -1 or +1. 
         class:bicycle shall only be used as an additional key. 
         "1" = Prefer 
         "2" = Very Nice way to cycle 
         "3" = This way is so nice, it pays out to make a detour also if this means taking 
         many unsuitable ways to get here. Outstanding for its intendedValues usage class.
         */
        //We can't store negative numbers into our map, therefore we add 
        //unspecifiedRelationWeight=4 to the schema from above
        WORST(0),
        AVOID_AT_ALL_COSTS(1),
        REACH_DEST(2),
        AVOID_IF_POSSIBLE(3),
        UNCHANGED(4),
        PREFER(5),
        VERY_NICE(6),
        BEST(7);

        private final int value;

        private PriorityCode( int value )
        {
            this.value = value;
        }

        public int getValue()
        {
            return value;
        }
    };

    private enum WayType
    {
        ROAD(0),
        PUSHING_SECTION(1),
        CYCLEWAY(2),
        OTHER_SMALL_WAY(3);

        private final int value;

        private WayType( int value )
        {
            this.value = value;
        }

        public int getValue()
        {
            return value;
        }
    };

    protected void setHighwaySpeed( String highway, int speed )
    {
        highwaySpeed.put(highway, speed);
    }

    protected int getHighwaySpeed( String key )
    {
        return highwaySpeed.get(key);
    }

    void setTrackTypeSpeed( String tracktype, int speed )
    {
        trackTypeSpeed.put(tracktype, speed);
    }

    void setSurfaceSpeed( String surface, int speed )
    {
        surfaceSpeed.put(surface, speed);
    }

    void setCyclingNetworkPreference( String network, int code )
    {
        bikeNetworkToCode.put(network, code);
    }

    void addPushingSection( String highway )
    {
        pushingSections.add(highway);
    }
}
