package team276;

import battlecode.common.*;
import java.util.Arrays;

public class ArchonBot extends Bot {
    private static final double MINIMUM_ENERGY_TO_SPAWN = RobotType.ARCHON.maxEnergon()*.75;
    private static final int MINIMUM_ENERGY_TO_TRANSFER = 25;
    private static final int UNITENERGY_TRANSFER = 10;
    private static final double SOLDIER_TO_ARCHON_RATIO = 3;
    private boolean didSpawn;
    private int lastSpawnRound;

    public ArchonBot(RobotController rc) throws Exception {
        super(rc);

        didSpawn = false;
        lastSpawnRound = 0;
        this.LOW_HP_THRESH = 75;
    }

    public void AI() throws Exception {
        while (true) {
            status = rc.senseRobotInfo(self);
            senseEdge();
            senseNear();
            sendHighPriorityEnemy();
            spawnUnit();

            if(didSpawn)
            	transferEnergonArchon();
            transferEnergon();

            if(!didSpawn && status.roundsUntilMovementIdle == 0 && !rc.hasActionSet())
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
            rc.spawn(RobotType.SOLDIER);
            didSpawn = true;
            lastSpawnRound = Clock.getRoundNum();
        }
    }
}
