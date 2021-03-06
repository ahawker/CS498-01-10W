package team276;

import battlecode.common.*;
import java.util.Arrays;

public class ArchonBot extends Bot {
	//private static final double MINIMUM_ENERGY_TO_SPAWN = RobotType.ARCHON.maxEnergon()*.75;
    private static final int MINIMUM_ENERGY_TO_SPAWN = 70;
    private static final int MINIMUM_ENERGY_TO_TRANSFER = 25;
    private static final int UNITENERGY_TRANSFER = 10;
    private static final double SOLDIER_TO_ARCHON_RATIO = 2.5;
    private boolean didSpawn;
    private int lastSpawnRound;

    public ArchonBot(RobotController rc) throws Exception {
        super(rc);

        didSpawn = false;
        lastSpawnRound = 0;
        this.LOW_HP_THRESH = 75;
    }

    public void AI() throws Exception {
       // status = rc.senseRobotInfo(self);
        //while(status.energonLevel < MINIMUM_ENERGY_TO_SPAWN) {
        //    yield();
        //    status = rc.senseRobotInfo(self);
       // }

        while (true) {
           status = rc.senseRobotInfo(self);
           senseEdge();
           senseNear();
           recvKilledArchonMsg();
           sendHighPriorityEnemy();
           if(Clock.getRoundNum() % 10 == 0)
           	   sendPulseMsg();
           spawnUnit();
           if(didSpawn)
           	   transferEnergonArchon();
           transferEnergon();
           handleMovement();

           yield();
        }
    }
    
    
    public void transferEnergonArchon() throws Exception {
        Robot r;
        RobotInfo ri;
        MapLocation ahead;
        int spawnTurns = Clock.getRoundNum() - lastSpawnRound;
 
        if(status.energonLevel < MINIMUM_ENERGY_TO_TRANSFER)
            return;
 
        // Give buckets of energon to our new unit
        if(didSpawn) {
            ahead = status.location.add(status.directionFacing);
            r = rc.senseGroundRobotAtLocation(ahead);
 
            if(r == null) {
                if(spawnTurns == 0)
                    return;
 
                didSpawn = false;
                lastSpawnRound = 0;
                return;
            }
 
            ri = rc.senseRobotInfo(r);
 
            // Apparently the guy we spawned got killaxed. Don't try to transfer
            // energon to another unit that may be there.
            if(!status.team.equals(ri.team)) {
                didSpawn = false;
                lastSpawnRound = 0;
                return;
            }
 
            if(ri.energonReserve < GameConstants.ENERGON_RESERVE_SIZE) {
                double need = GameConstants.ENERGON_RESERVE_SIZE - ri.energonReserve;
 
                rc.transferUnitEnergon(need, ahead, RobotLevel.ON_GROUND);
            }
 
            // He's full, fuck him.
            else if(GameConstants.ENERGON_RESERVE_SIZE - ri.energonReserve < .5
                && ri.maxEnergon - ri.energonLevel < .5) {
                
                didSpawn = false;
                lastSpawnRound = 0;
                return;
            }
        }
 
        else {
            double enerToGive = status.energonLevel - MINIMUM_ENERGY_TO_TRANSFER;
 
            if(enerToGive < 0)
                return;
 
            double perBot = enerToGive/nNeedEnergon;
 
            // Give the peasents around us some energon
            for(int i = 0; i < nNeedEnergon; i++) {
                ri = alliedGround[needEnergon[i]];
                double botEnerNeed = GameConstants.ENERGON_RESERVE_SIZE - ri.energonReserve;
 
                if(botEnerNeed > perBot)
                    botEnerNeed = perBot;
                    
                rc.transferUnitEnergon(botEnerNeed, alliedGround[needEnergon[i]].location, RobotLevel.ON_GROUND);
            }
        }
    }
    
    
    
    
    
    
/*
    public void transferEnergonArchon() throws Exception {
        Robot r;
        RobotInfo ri;
        MapLocation ahead;

        // Give buckets of energon to our new unit
        if(didSpawn) {
            ahead = status.location.add(status.directionFacing);
            r = rc.senseGroundRobotAtLocation(ahead);

            if(r == null) {
                if(Clock.getRoundNum() - lastSpawnRound == 0)
                    return;

                didSpawn = false;
                lastSpawnRound = 0;
                return;
            }

            ri = rc.senseRobotInfo(r);
            
            if(!status.team.equals(ri.team)) {
            	didSpawn = false;
            	lastSpawnRound = 0;
            	return;
            }

            // Last turn before awakened. Fill it up if we can!
            if(ri.maxEnergon -  ri.energonLevel < 1) {
                double need = GameConstants.ENERGON_RESERVE_SIZE - ri.energonReserve;
                double toGive = status.energonLevel - MINIMUM_ENERGY_TO_TRANSFER;

                if(need < toGive)
                    toGive = need;

                if(toGive > 0)
                    rc.transferUnitEnergon(toGive, ahead, RobotLevel.ON_GROUND);

            }

            // Give 1 energon until full
            else if(ri.energonReserve < GameConstants.ENERGON_RESERVE_SIZE) {
                if(status.energonLevel > MINIMUM_ENERGY_TO_TRANSFER)
                    rc.transferUnitEnergon(1, ahead, RobotLevel.ON_GROUND);

            }
        }

        else {
            double enerToGive = status.energonLevel - MINIMUM_ENERGY_TO_TRANSFER;

            if(enerToGive < 0)
                return;

            double perBot = enerToGive/nNeedEnergon;

            // Give the peasents around us some energon
            for(int i = 0; i < nNeedEnergon; i++) {
                ri = alliedGround[needEnergon[i]];
                double botEnerNeed = GameConstants.ENERGON_RESERVE_SIZE - ri.energonReserve;

                if(botEnerNeed > perBot)
                    botEnerNeed = perBot;
                    
                rc.transferUnitEnergon(botEnerNeed, alliedGround[needEnergon[i]].location, RobotLevel.ON_GROUND);
            }
        }
    }*/

    public boolean canSpawn() throws Exception {
        MapLocation ahead;
        Robot r;

        if(rc.hasActionSet())
            return false;

        if(didSpawn)
            return false;

        if(status.energonLevel < MINIMUM_ENERGY_TO_SPAWN)
            return false;

        ahead = status.location.add(status.directionFacing);
        if(rc.senseTerrainTile(ahead).getType() != TerrainTile.TerrainType.LAND)
            return false;

        r = rc.senseGroundRobotAtLocation(ahead);
        if(r != null)
            return false;

        return true;
    }

    private boolean needToSpawn() {
        int nearbyArchons = rc.senseNearbyAirRobots().length;
        nearbyArchons = (nearbyArchons == 0 ? 1 : nearbyArchons);

        double ratio = nAlliedGround/nearbyArchons;

       // Debugger.debug_print("Ratio: " + ratio);

        if(ratio < SOLDIER_TO_ARCHON_RATIO)
            return true;
        return false;
    }

    public void spawnUnit() throws Exception {
        if(!canSpawn())
            return;

        // TODO Spawn a unit based on numbers of needed units
        if(needToSpawn()) {
        	int nChainers = Math.max(alliedUnits[2], 1);
        	int nSoliders = Math.max(alliedUnits[3], 1);
        	
        	if(nChainers * 2 < nSoliders)
        		rc.spawn(RobotType.CHAINER);
        	else
        		rc.spawn(RobotType.SOLDIER);
        	
        	
        	
            //rc.spawn(RobotType.SOLDIER);
            didSpawn = true;
            lastSpawnRound = Clock.getRoundNum();
        }
    }
}
