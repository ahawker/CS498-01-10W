package team276;

import battlecode.common.*;
import java.util.PriorityQueue;
import java.util.Arrays;
import java.util.ArrayList;

public abstract class Bot {
    private static final int RANDOM_SEED        = 0x5B125AB;
    protected static final int MAX_BOTS_SCAN    = 20;
    protected static final int MAX_ARCHON_SCAN  = 6;
    protected static final int MAX_MAP_DIM_SQ   = GameConstants.MAP_MAX_HEIGHT*GameConstants.MAP_MAX_HEIGHT;
    protected final int MAX_GROUP_SZ            = 10;
    protected final int MSG_MAX = 7;
    
    protected static final int NORTH = 0x01;
    protected static final int EAST  = 0x02;
    protected static final int SOUTH = 0x04;
    protected static final int WEST  = 0x08;

    protected MapLocation highPriorityArchonEnemy;
    protected int highPriorityArchonEnemyType;

    protected int LOW_HP_THRESH;

    protected final RobotController rc;
    protected final Robot self;
    protected RobotInfo status;

    protected int bcCounterStart;
    protected PriorityQueue<ParsedMsg> msgQueue;

    protected MapLocation alliedArchons[];
    protected MapLocation highPriorityAlliedArchon;

    protected final RobotInfo alliedGround[];
    protected final RobotInfo[] alliedAir;
    protected final RobotInfo enemyAir[];
    protected final RobotInfo enemyGround[];
    
    protected final int needEnergon[];      // Offsets of the alliedGround array that need energon.
    protected final int needEnergonArchon[]; //offsets into archon array
    
    protected RobotInfo highPriorityEnemy;
    protected RobotInfo highPriorityAlliedGround;
    protected int nAlliedAir;
    protected int nAlliedGround;
    protected int nNeedEnergon;
    protected int nNeedEnergonArchon;
    protected int nEnemyAir;
    protected int nEnemyGround;
    protected int[] alliedUnits;
    
    protected int enemyArchonsKilled;
    protected ArrayList<Integer> enemyArchonIDs;
    
    private static MapLocation edges[];
    protected Direction mapBoundry;

    protected Direction queuedMoveDirection;
    protected boolean movementDelay = false;
    protected Message msg = new Message();

    public abstract void AI() throws Exception;

    public Bot(RobotController rc) throws Exception {
        this.rc = rc;
        this.self = rc.getRobot();
        this.status = rc.senseRobotInfo(self);
        this.msgQueue = new PriorityQueue<ParsedMsg>(10, new Util.MessageComparator());
        this.alliedArchons = null;
        this.alliedGround = new RobotInfo[MAX_BOTS_SCAN];
        this.alliedAir = new RobotInfo[MAX_BOTS_SCAN];
        this.needEnergon = new int[MAX_BOTS_SCAN];
        this.needEnergonArchon = new int[MAX_BOTS_SCAN];
        this.enemyAir = new RobotInfo[MAX_BOTS_SCAN];
        this.enemyGround = new RobotInfo[MAX_BOTS_SCAN];
        this.highPriorityEnemy = null;
        this.highPriorityAlliedArchon = null;
        this.highPriorityAlliedGround = null;
        this.nAlliedAir = 0;
        this.nAlliedGround = 0;
        this.nNeedEnergon = 0;
        this.nNeedEnergonArchon = 0;
        this.nEnemyAir = 0;
        this.nEnemyGround = 0;
        this.queuedMoveDirection = null;
        this.LOW_HP_THRESH = 10;
        this.highPriorityArchonEnemy = null;
        this.enemyArchonsKilled = 0;
        this.edges = new MapLocation[9];
        this.mapBoundry = null;
        this.enemyArchonIDs = new ArrayList<Integer>(6);
        this.alliedUnits = new int[8];
    }

    public final void resetMsgQueue() {
        msgQueue = new PriorityQueue<ParsedMsg>(10, new Util.MessageComparator());
    }

    public int calcAlliedArchonPriority(MapLocation ml) {
        return MAX_MAP_DIM_SQ - status.location.distanceSquaredTo(ml);
    }

    public int calcAlliedPriority(RobotInfo ri) {
        return -1;
    }

    // We assign units a priority based on the scanning robot's ability to attack, "LH" threshold,
    // and amount of remaining health. Of two robots of the same type and "LH/HH" class, the one 
    // with lower remaining health should recieve the higher priority.
    public int calcEnemyPriority(RobotInfo ri) {
        // FIXME: Tweak these for best results
        final int LH_ARCHON         = 15;       // When do we consider these units "low health?"
        final int LH_CHAINER        = 10;
        final int LH_SOLDIER        = 10;
        final int LH_TURRET         = 15;
        final int LH_WOUT           = 10;
        final int LH_TOWER          = 10;

        final int LH_ARCHON_PV      = 1200;     // Base priority value for this low health unit
        final int LH_CHAINER_PV     = 1000;
        final int LH_SOLDIER_PV     = 900;
        final int LH_TURRET_PV      = 800;
        final int LH_WOUT_PV        = 700;
        final int LH_TOWER_PV       = 600;

        final int HH_CHAINER_PV     = 500;      // Base priority value for high health units
        final int HH_SOLDIER_PV     = 400;
        final int HH_TURRET_PV      = 300;
        final int HH_WOUT_PV        = 200;
        final int HH_ARCHON_PV      = 1100;
        final int HH_TOWER_PV       = 100;

        int hv;
        int tel;

        // If we can't attack it, ignore it
        // FIXME: If we can't attack as far as we can sense, maybe this should get reworked
        // so that movement "pulls" this bot towards this enemy if it's the only target
        // available. At the same time, that might pull him out of flocking and force a 1v1?
        if(status.type != RobotType.ARCHON && !rc.canAttackSquare(ri.location))
            return -1;

        // If the game updates the way I *HOPE* it does (the robots energon is calculated at the
        // end of every other robots turn in case it's attacked) then this will filter out any
        // bots that could be in a state of "limbo" if they're not cleaned off the map until the
        // end of the round.
        // FIXME: Figure out what the game engine behavior is here.. Does it immediately remove FIXME
        // FIXME: dead bots at the end of every robots turn? Or are they stuck here until the   FIXME
        // FIXME: end of the round? Is they're health updated at the end of every turn? Ugh.    FIXME

        tel = (int)ri.energonLevel;

        // If the robot is "dead", ignore it.
        // FIXME: Read above comment.. does this work the way I think/hope it does?
        if(ri.energonLevel <= 0)
            return -1;

        hv = (int)ri.maxEnergon - tel;

        switch(ri.type) {
            case ARCHON: return (tel <= LH_ARCHON) ? LH_ARCHON_PV + hv : HH_ARCHON_PV + hv; 
            case CHAINER: return (tel <= LH_CHAINER) ? LH_CHAINER_PV + hv : HH_CHAINER_PV + hv; 
            case SOLDIER: return (tel <= LH_SOLDIER) ? LH_SOLDIER_PV + hv : HH_SOLDIER_PV + hv; 
            case WOUT: return (tel <= LH_WOUT) ? LH_WOUT_PV + hv : HH_WOUT_PV + hv; 
            case TURRET: return (tel <= LH_TURRET) ? LH_TURRET_PV + hv : HH_TURRET_PV + hv; 

            // Towers just ignore specializations for now
            case AURA:
            case TELEPORTER:
            case COMM: return (tel <= LH_TOWER) ? LH_TOWER_PV + hv : HH_TOWER_PV + hv; 
        }
        return -1;
    }

    public RobotController getRC() {
        return rc;
    }

    
    
    
    
    
    
    
    /*
    public Direction flock(double SEPERATION, double COHESION, double ALIGNMENT, double COLLISION, double GOAL, double ENEMY_GOAL) throws Exception {
        int[] seperation=new int[2], align=new int[2], goal=new int[2], collision=new int[2], enemies=new int[2];
        MapLocation myloc = status.location;
        // General swarm 
        int c = 0;
        int len;

        len = nAlliedGround;
        if(len > MAX_GROUP_SZ)
            len = MAX_GROUP_SZ;

        for(c = 0; c < len; c++) {
            RobotInfo ri = alliedGround[c];

            seperation[0]+= myloc.getX() - ri.location.getX();
            seperation[1]+= myloc.getY() - ri.location.getY();
            align[0]+=ri.directionFacing.dx;
            align[1]+=ri.directionFacing.dy;
        }

        if(ENEMY_GOAL != 0) {
            if(nEnemyAir > 0) {
                for(c = 0; c < nEnemyAir; c++) {
                    enemies[0] -= myloc.getX() - enemyAir[c].location.getX();
                    enemies[1] -= myloc.getY() - enemyAir[c].location.getY();
                }
            }

            else if(nEnemyGround > 0) {
                for(c = 0; c < nEnemyGround; c++) {
                    enemies[0] -= myloc.getX() - enemyGround[c].location.getX();
                    enemies[1] -= myloc.getY() - enemyGround[c].location.getY();
                }
            }
        }

         //COLLISION GOAL 
        if (COLLISION != 0) {
            for (Direction d : Direction.values()) {
                if (d == Direction.OMNI || d == Direction.NONE) continue;
                TerrainTile t = rc.senseTerrainTile(myloc.add(d));
                if (t != null && t.getType() != TerrainTile.TerrainType.LAND) {
                    collision[0] -= d.dx;
                    collision[1] -= d.dy;
                }
            }
        }
        // LEADER GOAL 
        if (GOAL != 0) {
            MapLocation leader = highPriorityAlliedArchon;
            if (leader != null && rc.canSenseSquare(leader)) {
                Direction leader_dir = rc.senseRobotInfo(rc.senseAirRobotAtLocation(leader)).directionFacing;
                goal[0] = leader.getX()+5*leader_dir.dx - myloc.getX();
                goal[1] = leader.getY()+5*leader_dir.dy - myloc.getY();
            } else if (leader != null) {
                goal[0] = leader.getX() - myloc.getX();
                goal[1] = leader.getY() - myloc.getY();
            }
        }

        // Calculate Vector lengths 
        double slen = Util.ZERO.distanceSquaredTo(new MapLocation(seperation[0], seperation[1]));
        double alen = Util.ZERO.distanceSquaredTo(new MapLocation(align[0], align[1]));
        double clen = Util.ZERO.distanceSquaredTo(new MapLocation(collision[0], collision[1]));
        double glen = GOAL != 0 ? Math.sqrt(Util.ZERO.distanceSquaredTo(new MapLocation(goal[0], goal[1]))) : 1;
        double elen = Util.ZERO.distanceSquaredTo(new MapLocation(enemies[0], enemies[1]));

        slen = slen == 0 ? 1 : Math.sqrt(slen);    //Prevent divide by zero
        alen = alen == 0 ? 1 : Math.sqrt(alen);
        clen = clen == 0 ? 1 : Math.sqrt(clen);
        elen = elen == 0 ? 1 : Math.sqrt(elen);

        // Sum the vectors 
        double outx = seperation[0]/slen*(SEPERATION - COHESION)    //Cohesion == -Seperation
                      + align[0]*ALIGNMENT/alen
                      + collision[0]*COLLISION/clen
                      + goal[0]*GOAL/glen
                      + enemies[0]*ENEMY_GOAL/elen;
        double outy = seperation[1]/slen*(SEPERATION - COHESION)
                      + align[1]*ALIGNMENT/alen
                      + collision[1]*COLLISION/clen
                      + goal[1]*GOAL/glen
                      + enemies[1]*ENEMY_GOAL/elen;
        return Util.coordToDirection((int)(outx*10), (int)(outy*10));
    }
    */
    
    public Direction flock(double SEPERATION, double COHESION, double ALIGNMENT, double COLLISION, double GOAL, double ENEMY_GOAL, double AVOID_MAP_EDGE) throws Exception {
        int[] seperation=new int[2], align=new int[2], goal=new int[2], collision=new int[2], enemies=new int[2], edge=new int[2];
        MapLocation myloc = status.location;
        /* General swarm */
        int c = 0;
        int len;
 
        len = nAlliedGround;
        if(len > MAX_GROUP_SZ)
            len = MAX_GROUP_SZ;
 
        for(c = 0; c < len; c++) {
            RobotInfo ri = alliedGround[c];
 
            seperation[0]+= myloc.getX() - ri.location.getX();
            seperation[1]+= myloc.getY() - ri.location.getY();
            align[0]+=ri.directionFacing.dx;
            align[1]+=ri.directionFacing.dy;
        }
 
        if(ENEMY_GOAL != 0) {
            if(nEnemyAir > 0) {
                for(c = 0; c < nEnemyAir; c++) {
                    enemies[0] -= myloc.getX() - enemyAir[c].location.getX();
                    enemies[1] -= myloc.getY() - enemyAir[c].location.getY();
                }
            }
 
            else if(nEnemyGround > 0) {
                for(c = 0; c < nEnemyGround; c++) {
                    enemies[0] -= myloc.getX() - enemyGround[c].location.getX();
                    enemies[1] -= myloc.getY() - enemyGround[c].location.getY();
                }
            }
        }
 
        /* COLLISION GOAL */
        if (COLLISION != 0) {
            for (Direction d : Direction.values()) {
                if (d == Direction.OMNI || d == Direction.NONE) continue;
                TerrainTile t = rc.senseTerrainTile(myloc.add(d));
                if (t != null && t.getType() != TerrainTile.TerrainType.LAND) {
                    collision[0] -= d.dx;
                    collision[1] -= d.dy;
                }
            }
        }
        /* LEADER GOAL */
        if (GOAL != 0) {
            MapLocation leader = highPriorityAlliedArchon;
            if (leader != null && rc.canSenseSquare(leader)) {
                Direction leader_dir = rc.senseRobotInfo(rc.senseAirRobotAtLocation(leader)).directionFacing;
                goal[0] = leader.getX()+5*leader_dir.dx - myloc.getX();
                goal[1] = leader.getY()+5*leader_dir.dy - myloc.getY();
            } else if (leader != null) {
                goal[0] = leader.getX() - myloc.getX();
                goal[1] = leader.getY() - myloc.getY();
            }
        }
 
        /* AVOID MAP EDGE */
        if(AVOID_MAP_EDGE != 0 && mapBoundry != null) {
            MapLocation oppositeML = status.location.add(mapBoundry.opposite());
            edge[0] = oppositeML.getX() - status.location.getX();
            edge[1] = oppositeML.getY() - status.location.getY();
        }
 
        /* Calculate Vector lengths */
        double slen  = Util.ZERO.distanceSquaredTo(new MapLocation(seperation[0], seperation[1]));
        double alen  = Util.ZERO.distanceSquaredTo(new MapLocation(align[0], align[1]));
        double clen  = Util.ZERO.distanceSquaredTo(new MapLocation(collision[0], collision[1]));
        double glen  = GOAL != 0 ? Math.sqrt(Util.ZERO.distanceSquaredTo(new MapLocation(goal[0], goal[1]))) : 1;
        double elen  = Util.ZERO.distanceSquaredTo(new MapLocation(enemies[0], enemies[1]));
        double melen = Util.ZERO.distanceSquaredTo(new MapLocation(edge[0], edge[1]));
 
        slen  = slen  == 0 ? 1 : Math.sqrt(slen);    //Prevent divide by zero
        alen  = alen  == 0 ? 1 : Math.sqrt(alen);
        clen  = clen  == 0 ? 1 : Math.sqrt(clen);
        elen  = elen  == 0 ? 1 : Math.sqrt(elen);
        melen = melen == 0 ? 1 : Math.sqrt(melen);
 
        /* Sum the vectors */
        double outx = seperation[0]/slen*(SEPERATION - COHESION)    //Cohesion == -Seperation
                      + align[0]*ALIGNMENT/alen
                      + collision[0]*COLLISION/clen
                      + goal[0]*GOAL/glen
                      + enemies[0]*ENEMY_GOAL/elen
                      + edge[0]*AVOID_MAP_EDGE/melen;
        double outy = seperation[1]/slen*(SEPERATION - COHESION)
                      + align[1]*ALIGNMENT/alen
                      + collision[1]*COLLISION/clen
                      + goal[1]*GOAL/glen
                      + enemies[1]*ENEMY_GOAL/elen
                      + edge[1]*AVOID_MAP_EDGE/melen;
        return Util.coordToDirection((int)(outx*10), (int)(outy*10));
    }
    
    
    
    
    

    //Uses RobotInfo highPriorityEnemy as our target.
    public boolean attack() throws Exception {
        //If we...
        //  1.) Don't have a target
        //  2.) Are On attack cooldown
        //  3.) Can't attack the square our enemy is on
        //We can't attack this round. Return out so we can try and move.

        if(highPriorityArchonEnemy == null && highPriorityEnemy == null)
            return false;

       if(status.roundsUntilAttackIdle != 0
            || (highPriorityArchonEnemy != null && !rc.canAttackSquare(highPriorityArchonEnemy))
            || (highPriorityEnemy != null && !rc.canAttackSquare(highPriorityEnemy.location))) {
            return false;
        }
        //Attacking takes higher priority than movement.
        //If we have an action set from the previous round (movement or direction),
        //reset their global flags and remove it from the queue since we have a target to attack.
        if(rc.hasActionSet()) {
            rc.clearAction();
        }

        resetMovementFlags();
        
        //Call the proper attack call if we recv a target from an archon that we can attack.
        //Check to make sure you can still sense a robot at that location.
        //The target came from an archon message, meaning that if the enemy has a lower ID than you
        //it could have possibly moved between the time it was sensed/msg sent vs the time you processed
        //the message and then decided to attack that square.
        if(highPriorityArchonEnemy != null) {
        	
            if(highPriorityArchonEnemyType == 0) { //enemy archon
            	Robot enemy = rc.senseAirRobotAtLocation(highPriorityArchonEnemy);
            	if(enemy != null) {
            		RobotInfo info = rc.senseRobotInfo(enemy);
            		
            		if(info.energonLevel >= 0) {
            			rc.attackAir(highPriorityArchonEnemy);	
            			
            			if(isKillingBlow(info)) {
            				enemyArchonIDs.add(info.id);
            				sendKilledArchonMsg(++enemyArchonsKilled, enemyArchonIDs);
            			}
            			
            			return true;
            		}
            	}
            }
            else { //Non-archon (Ground unit or tower)
            	Robot enemy = rc.senseAirRobotAtLocation(highPriorityArchonEnemy);
            	if(enemy != null) {
            		RobotInfo info = rc.senseRobotInfo(enemy);
            		if(info.energonLevel >= 0) {
            			rc.attackGround(highPriorityArchonEnemy);
            			
            			return true;
            		}
            	}
             }
        }

        //We didn't recv a valid target from an archon message,
        //so attack our best target that we found within our sense range.
        if(highPriorityEnemy != null){
        	if(highPriorityEnemy.energonLevel <= 0)
        		return false;
        	
            if(highPriorityEnemy.type == RobotType.ARCHON) {
            	
            	rc.attackAir(highPriorityEnemy.location);
            		
            	if(isKillingBlow(highPriorityEnemy)) {
            		enemyArchonIDs.add(highPriorityEnemy.id);
            		sendKilledArchonMsg(++enemyArchonsKilled, enemyArchonIDs);
            	}
            	return true;
            }
            else {
                rc.attackGround(highPriorityEnemy.location);  
                return true;
            }
        }
        return false;
    }
    
    
    private final boolean isKillingBlow(RobotInfo target) {
    	double attackPower = status.type.attackPower() * ((target.aura == AuraType.DEF) ? 0.2 : 1);
    	double targetHP = target.energonLevel 
    						+ ((target.type == RobotType.ARCHON) ? 1 : 0) 
    						+ ((target.energonReserve >= 1) ? 1 : 0);
    						
    	return (attackPower > targetHP);
    	
    }
    
    
    
    
    
    
    
    public void handleMovement() throws Exception {
        //On movement cooldown, can't do anything here anyways.
       // rc.setIndicatorString(3,"Dir: "+queuedMoveDirection);
        if (status.roundsUntilMovementIdle != 0 || rc.hasActionSet())
            return;
 
        if (highPriorityEnemy != null && status.type != RobotType.ARCHON)
            return;
 
        //Have an attack action in our queue.
        //Attack has higher priority, so we concede movement on this round.
        if (rc.hasActionSet() && queuedMoveDirection == null)
            return;
        // WHERE ARE WE GOING? 
        Direction flock = queuedMoveDirection;
        if (flock == null) {  //Need a direction!
            if (status.type == RobotType.ARCHON) {
            	if(enemyArchonsKilled >= 3)
            		flock = flock(20, 5, 1, 0, 0, 1000, 5);	
            	else
            		flock = flock(2, 5, 1, 0, 0, 10, 5);
            }
            else {
                if (status.energonLevel < LOW_HP_THRESH)
                    flock = flock(1, 2, 2, 1, 10, -3, 0);    //Run away!
                else
                    flock = flock(5, 1, 4, 1, 1, 1000, 0);
            }
 
        }
        if (flock == Direction.OMNI || flock == Direction.NONE) //Flocking failed
            return;
        //Check your near three squares if the flock direction isn't valid.
        if (rc.canMove(flock))
            queuedMoveDirection = flock;
        else if (rc.canMove(flock.rotateLeft()))
            queuedMoveDirection = flock.rotateLeft();
        else if (rc.canMove(flock.rotateLeft().rotateLeft()))   //Hack, should fix
            queuedMoveDirection = flock.rotateLeft().rotateLeft();
        else if (rc.canMove(flock.rotateRight()))
            queuedMoveDirection = flock.rotateRight();
        else if (rc.canMove(flock.rotateRight().rotateRight()))   //Hack, should fix
            queuedMoveDirection = flock.rotateRight().rotateRight();
        else { //Don't move otherwise, we're stuck.
            queuedMoveDirection = null;    //Possibly an edge
            return; //Wait until next round
        }
        //queuedMoveDirection *must* be a valid movement spot by this point.
        //If we're currently facing our target direction...
        // MOVE IT! 
        if (status.directionFacing.equals(queuedMoveDirection)) {
            rc.moveForward();
            resetMovementFlags();
            return;
        } else if (status.directionFacing.opposite() == queuedMoveDirection) {
            //If we want to move backward, don't waste the round turning
            rc.moveBackward();
            resetMovementFlags();
            return;
        } else {
            rc.setDirection(queuedMoveDirection);   //Turn, waiting til next turn to move.
        }
    }
    
    
    
    
    
    
/*
    public void handleMovement() throws Exception {
        //On movement cooldown, can't do anything here anyways.
        if(status.roundsUntilMovementIdle != 0)
            return;

        if(highPriorityEnemy != null && status.type != RobotType.ARCHON)
            return;

        //Have an attack action in our queue.
        //Attack has higher priority, so we concede movement on this round.
        if(rc.hasActionSet() && queuedMoveDirection == null && !movementDelay)
            return;


        //No action on the queue and no direction set, lets find our next move.
        if(queuedMoveDirection == null) {
            Direction flock;

            // This needs to be fixed
            //else
            if(status.type == RobotType.ARCHON) {
            	if(enemyArchonsKilled <= 2)
            		flock = flock(100, 4, 1, 1, 2, 20);
            	else
            		flock = flock(1, 4, 1, 1, 2, 20);
                
            }
            else {
                if(status.energonLevel < LOW_HP_THRESH)
                    flock = flock(1, 1, 2, 2, 3, 1000);
                else
                    flock = flock(1, 1, 2, 2, 2, 1000);
            }

            //If we magically got a direction to our current location, or worse, just quit now.
            if(flock == Direction.OMNI || flock == Direction.NONE)
                return;
            
            //Yeah thats right >.<
            //Check your front three squares if the flock direction isn't valid.
            queuedMoveDirection = (rc.canMove(flock)) 
           							? flock 
            						: (rc.canMove(flock.rotateLeft()))
            							? flock.rotateLeft()
            							: flock.rotateRight();

            //If we're currently facing our target direction...
            
            if(status.directionFacing.equals(queuedMoveDirection)) {
                //If we can move forward, do it.
                
                if(rc.canMove(queuedMoveDirection)) {
                	//Debugger.debug_print("straight");
                	rc.moveForward();
                    resetMovementFlags();
                    return;
                }
                //If we cant move forward this round, flag our single round delay.
                else movementDelay = true;
            } 
            //If our target destination is behind us, don't change direction...
            else if(status.directionFacing == queuedMoveDirection.opposite()) {
                //If we can move backward, do it.
                if(rc.canMove(queuedMoveDirection.opposite())) {
                    resetMovementFlags();
                    rc.moveBackward();
                    return;
                }
                //If we can't move backward, just flag our single round delay.
                else movementDelay = true;
            }
            else {
            	rc.setDirection(queuedMoveDirection);	
          //  }
        }
        //We have a movement direction in our queue...
        else {
            //If we can move forward, do it.
            if(rc.canMove(queuedMoveDirection)) {
                rc.moveForward();
                resetMovementFlags();
            }
            //If we can't move forward...
            else {
                if(!movementDelay){
                    movementDelay = true;
                    return;
                }
                resetMovementFlags();
            }
        }
    } */
    
    
    
    
    

    private final void resetMovementFlags() {
        queuedMoveDirection = null;
        movementDelay = false;
    }
    
    
    
    
   /* protected void sendHighPriorityArchonEnemy() throws Exception {
    	if(highPriorityArchonEnemy == null)
    		return;
    	
    	if(rc.hasBroadcastMessage())
    		rc.clearBroadcast();
    	
    	
    	msg.ints = new int[] { RANDOM_SEED, highPriorityArchonEnemyType };
    	msg.locations = new MapLocation[] { highPriorityArchonEnemy };
    	rc.broadcast(msg);
    	
    	if(rc.getBroadcastCost() > status.energonLevel) {
    		rc.clearBroadcast();	
    	}
    }*/
    
    
    
    
    
    
    protected void recvKilledArchonMsg() throws Exception {
    	Message[] msgs = rc.getAllMessages();
    	int sz = msgs.length;
    	
    	for(int i=0; i<sz; i++) {
    		if(msgs[i] == null)
    			continue;
    		
    		if(msgs[i].ints == null
    			|| msgs[i].ints.length == 0
    			|| msgs[i].ints[0] != RANDOM_SEED
    			|| msgs[i].ints[1] != RANDOM_SEED)
    		continue;
    		
    		if(i >= MSG_MAX) break;
    		
    		boolean foundNew = false;
    		for(int j=3; j<msgs[i].ints.length; j++) {
    				if(enemyArchonIDs.contains(msgs[i].ints[j]))
    					continue;
    				
    				enemyArchonIDs.add(msgs[i].ints[j]);
    				foundNew = true;
    		}
    		if(foundNew) {
    			enemyArchonsKilled = enemyArchonIDs.size();
    		}
    		return;
    	}
    }
    
    private final void clearKilledArchonIDs() {
    	enemyArchonIDs = new ArrayList<Integer>(6);	
    }
    

    
    
    
    
     protected void recvHighPriorityEnemy() throws Exception{
        Message[] msgs = rc.getAllMessages();
        int sz = msgs.length;
        boolean alreadyUpdated = false;
        
        for(int i=0; i<sz; i++) {
        	
        	if(i == MSG_MAX) break;
            
            if(msgs[i] == null) 
                continue;
            
            if( msgs[i].ints == null 
                || msgs[i].ints.length == 0 
                || msgs[i].ints[0] != RANDOM_SEED) {
            
                continue;
            }
            
            if(msgs[i].locations == null && msgs[i].ints[1] == RANDOM_SEED) {
            	
            	if(alreadyUpdated)
            		continue;
            	
            	if(msgs[i].ints[2] < enemyArchonsKilled) {
            		sendKilledArchonMsg(enemyArchonsKilled, enemyArchonIDs);
            		return;
            	}
            	//else if(msgs[i].ints[2] > enemyArchonsKilled) {
					boolean foundNew = false;
					for(int j=3; j<msgs[i].ints.length; j++) {
						if(enemyArchonIDs.contains(msgs[i].ints[j])) 
							continue;
						
						enemyArchonIDs.add(msgs[i].ints[j]);
						enemyArchonsKilled++;
						foundNew = true;
					}
					if(foundNew) {
						alreadyUpdated = true;
						sendKilledArchonMsg(enemyArchonsKilled, enemyArchonIDs);
					}
					
				//}
            }
            
            if( msgs[i].locations == null 
                || msgs[i].locations.length == 0
                || msgs[i].locations[0] == null) {
            
                continue;
            }
       
            if(rc.canAttackSquare(msgs[i].locations[0])) {
                highPriorityArchonEnemy = msgs[i].locations[0];
                highPriorityArchonEnemyType = msgs[i].ints[1];
                break;
            }
        }
    }

    protected void sendHighPriorityEnemy() throws Exception {
        if(highPriorityEnemy == null) {
            //Debugger.debug_print("WE DON'T HAVE A TARGET!");
            return;
        }
        
        //If we already have a message in our queue, remove it.
        if(rc.hasBroadcastMessage()) {
            rc.clearBroadcast();    
        }
        
        //Debugger.debug_print("send high priority enemy");
        //Message contains:
            //probably chksum some random number in our int array for cheap checks?
            //ints[0] -- Random seed
            //ints[1] -- robot type
            //locations[0] -- MapLocation of our highPriorityEnemy
        //msg.ints = new int[] { RANDOM_SEED, highPriorityEnemy.type.ordinal(), enemyArchonsKilled };
        //msg.locations = new MapLocation[] { highPriorityEnemy.location };
        
        int sz = enemyArchonIDs.size();
        
        msg.ints = new int[sz + 3];
        msg.ints[0] = RANDOM_SEED;
        msg.ints[1] = highPriorityEnemy.type.ordinal();
        msg.ints[2] = enemyArchonsKilled;
        
        for(int i=0; i<sz; i++) {
        	msg.ints[i+3] = enemyArchonIDs.get(i).intValue();	
        }
        
        msg.locations = new MapLocation[] { highPriorityEnemy.location };
        
        //Debugger.debug_print("ARCHON HIGH PRIORITY ENEMY: " + RobotType.values()[highPriorityEnemy.type.ordinal()] + ", " + highPriorityEnemy.location);
        rc.broadcast(msg);
        
        if(rc.getBroadcastCost() > status.energonLevel) {
            rc.clearBroadcast();    
        }
    }
    
    protected void sendPulseMsg() throws Exception {
    	if(rc.hasBroadcastMessage())
    		return;
    	
    	int sz = enemyArchonIDs.size();
    	
    	msg.ints = new int[sz+3];
    	msg.ints[0] = RANDOM_SEED;
    	msg.ints[1] = RANDOM_SEED;
    	msg.ints[2] = enemyArchonsKilled;
    	
    	for(int i=0; i<sz; i++) {
    		msg.ints[i+3] = enemyArchonIDs.get(i).intValue();
    	}
    	
    	msg.locations = null;
    	
    	rc.broadcast(msg);
    	
    	if(rc.getBroadcastCost() > status.energonLevel)
    		rc.clearBroadcast();
    }
    
    protected void sendKilledArchonMsg(int archonsRemaining, ArrayList<Integer> deadArchonIds) throws Exception {
    	if(rc.hasBroadcastMessage())
    		rc.clearBroadcast();
    	
    	int sz = deadArchonIds.size();
    	
    	msg.ints = new int[sz + 3];
    	msg.ints[0] = RANDOM_SEED;
    	msg.ints[1] = RANDOM_SEED;
    	msg.ints[2] = archonsRemaining;
    	
    	for(int i=0; i<sz; i++) {
    		msg.ints[i+3] = deadArchonIds.get(i).intValue();
    	}
    	
    	msg.locations = null;
    	
    	//Debugger.debug_print("KILLED AN ARCHON: " + enemyArchonsKilled);
    	//for(int i=2; i<msg.ints.length; i++) {
    	//	Debugger.debug_print(msg.ints[i] + "");	
    	//}
    	
    	rc.broadcast(msg);
    	
    	if(rc.getBroadcastCost() > status.energonLevel)
    		rc.clearBroadcast();
    }
    

    // Sense the nearby robots
    // TODO: Figure out where to deal with attacker messgaes from others:
    // "Only care about those messgaes when you don't have a good local target"
    public final void senseNear() throws Exception {
        Robot[] airUnits;
        Robot[] groundUnits;
        RobotInfo tri;
        int highPriorityEnemyValue, highPriorityAlliedValue;
        int i, len, thpa, thpe;

        highPriorityEnemyValue = 0;
        highPriorityAlliedValue = 0;

        highPriorityArchonEnemy = null;
        highPriorityArchonEnemyType = 0;

        nAlliedAir = 0;
        nAlliedGround = 0;
        nEnemyAir = 0;
        nEnemyGround = 0;
        nNeedEnergon = 0;
        nNeedEnergonArchon = 0;
        alliedUnits = new int[8];

        highPriorityEnemy = null;
        highPriorityAlliedArchon = null;

        airUnits = rc.senseNearbyAirRobots();
        groundUnits = rc.senseNearbyGroundRobots();

        // Air units
        len = airUnits.length;
        if(len > MAX_BOTS_SCAN)
            len = MAX_BOTS_SCAN;

        // Only deal with enemy air
        for(i = 0; i < len; i++) {
            tri = rc.senseRobotInfo(airUnits[i]);
            
            if(status.team.equals(tri.team.opponent())) {
                enemyAir[nEnemyAir++] = tri;

                thpe = calcEnemyPriority(tri);
                if(thpe > highPriorityEnemyValue) {
                    highPriorityEnemyValue = thpe;
                    highPriorityEnemy = tri;
                }
            }
            else {
            	alliedAir[nAlliedAir++] = tri;
            	alliedUnits[tri.type.ordinal()]++;
            	
            	if(tri.location.isAdjacentTo(status.location) && tri.energonReserve < GameConstants.ENERGON_RESERVE_SIZE)
                    needEnergonArchon[nNeedEnergonArchon++] = nAlliedAir - 1;
            }
        }

        //get our closed archon still
        //FLOCKING
        //don't use nAlliedAir though
        alliedArchons = rc.senseAlliedArchons();
        len = alliedArchons.length;
        
        for(i=0; i<len; i++) {
        	thpa = calcAlliedArchonPriority(alliedArchons[i]);
        	
        	if(thpa > highPriorityAlliedValue) {
        		highPriorityAlliedValue = thpa;
        		highPriorityAlliedArchon = alliedArchons[i];
        	}
        }

        // Our archons
        /*
        for(nAlliedAir = 0; nAlliedAir < len; nAlliedAir++) {
            thpa = calcAlliedArchonPriority(alliedArchons[nAlliedAir]);

            if(thpa > highPriorityAlliedValue) {
                highPriorityAlliedValue = thpa;
                highPriorityAlliedArchon = alliedArchons[nAlliedAir];
            }
        }

        nAlliedAir++;
        */

        // Repeat for ground units.
        highPriorityAlliedValue = 0;
        len = groundUnits.length;
        if(len > MAX_BOTS_SCAN)
            len = MAX_BOTS_SCAN;

        for(i = 0; i < len; i++) {
            tri = rc.senseRobotInfo(groundUnits[i]);

            if(status.team.equals(tri.team)) {
                alliedGround[nAlliedGround++] = tri;
                alliedUnits[tri.type.ordinal()]++;

                if(tri.location.isAdjacentTo(status.location) && tri.energonReserve < GameConstants.ENERGON_RESERVE_SIZE)
                    needEnergon[nNeedEnergon++] = nAlliedGround - 1;

                thpa = calcAlliedPriority(tri);
                if(thpa > highPriorityAlliedValue) {
                    highPriorityAlliedValue = thpa;
                    highPriorityAlliedGround = tri;
                }
            }
 
            else {
                enemyGround[nEnemyGround++] = tri;

                thpe = calcEnemyPriority(tri);
                if(thpe > highPriorityEnemyValue) {
                    highPriorityEnemyValue = thpe;
                    highPriorityEnemy = tri;
                }
            }
        }
    }
    
    
    
    private final double getAdjacentEnergonAvg() {
        double total = 0;
        for (int i = 0; i < nNeedEnergon; i++)
            total += alliedGround[needEnergon[i]].energonReserve;
 
        for(int i=0; i<nNeedEnergonArchon; i++)
         	total += alliedAir[needEnergonArchon[i]].energonReserve;
 
        return total/(nNeedEnergon + nNeedEnergonArchon);
    }
 
    public void transferEnergon() throws Exception {
        double adjAvg;
        double toGive;
 
        if (status.type.isBuilding())
            return;
 
        if (status.energonLevel < LOW_HP_THRESH-15)
            return;
 
 
        adjAvg = getAdjacentEnergonAvg();
        if (adjAvg > status.energonLevel)
            return;
 
        toGive = GameConstants.ENERGON_RESERVE_SIZE - adjAvg;
        toGive = toGive - status.energonLevel < 0 ? status.energonLevel*.1 : toGive;
        toGive /= (nNeedEnergon + nNeedEnergonArchon);
 
        rc.setIndicatorString(1,"toGive: "+toGive+", nNeed: "+nNeedEnergon+", nA: "+nNeedEnergonArchon+", adjAvg: "+adjAvg);
        rc.setIndicatorString(2,"hp: "+status.energonLevel);
        for (int i = 0; i < nNeedEnergon; i++) {
            RobotInfo ri = alliedGround[needEnergon[i]];
            double botEnerNeed = GameConstants.ENERGON_RESERVE_SIZE - ri.energonReserve;
 
            if (botEnerNeed > toGive)
                botEnerNeed = toGive;
 
            rc.transferUnitEnergon(botEnerNeed, ri.location, RobotLevel.ON_GROUND);
        }
 
        for (int i=0; i<nNeedEnergonArchon; i++) {
            RobotInfo ri = alliedAir[needEnergonArchon[i]];
            double energonNeeded = GameConstants.ENERGON_RESERVE_SIZE - ri.energonReserve;
 
            if (energonNeeded > toGive)
                energonNeeded = toGive;
 
            rc.transferUnitEnergon(energonNeeded, ri.location, RobotLevel.IN_AIR);
        }
    }
    
    
    
    
    
/*
    private final double getAdjacentEnergonAvg() {
        double total = 0;

        for(int i = 0; i < nNeedEnergon; i++)
            total += alliedGround[needEnergon[i]].energonLevel;
        
       // for(int i=0; i<nNeedEnergonArchon; i++)
       // 	total += alliedAir[needEnergonArchon[i]].energonLevel;

        return total/(nNeedEnergon + nNeedEnergonArchon);
    }
    
    
    
    public void transferEnergon() throws Exception {
        double adjAvg;
        double toGive;

        if(status.type.isBuilding())
            return;
        
        if(status.energonLevel < status.maxEnergon-15)
            return;
        

        adjAvg = getAdjacentEnergonAvg();
        if(adjAvg > status.energonLevel)
            return;

        toGive = status.energonLevel - adjAvg;
        toGive /= (nNeedEnergon + nNeedEnergonArchon);

        for(int i = 0; i < nNeedEnergon; i++) {
            RobotInfo ri = alliedGround[needEnergon[i]];
            double botEnerNeed = GameConstants.ENERGON_RESERVE_SIZE - ri.energonReserve;

            if(botEnerNeed > toGive)
                botEnerNeed = toGive;

            rc.transferUnitEnergon(botEnerNeed, ri.location, RobotLevel.ON_GROUND);
        }
        
        for(int i=0; i<nNeedEnergonArchon; i++) {
        	RobotInfo ri = alliedAir[needEnergonArchon[i]];
        	double energonNeeded = GameConstants.ENERGON_RESERVE_SIZE - ri.energonReserve;
        	
        	if(energonNeeded > toGive)
        		energonNeeded = toGive;
        	
        	rc.transferUnitEnergon(energonNeeded, ri.location, RobotLevel.IN_AIR);
        }
    }*/
    
    
    
    
    
    public void senseEdge() {
        TerrainTile.TerrainType sensedEdges[] = new TerrainTile.TerrainType[9]; 
        int dx = status.location.getX();
        int dy = status.location.getY();
        int total = 0;
 
        edges[NORTH] = new MapLocation(dx, dy - 6);
        edges[EAST] = new MapLocation(dx + 6, dy);
        edges[SOUTH] = new MapLocation(dx, dy + 6);
        edges[WEST] = new MapLocation(dx - 6, dy);
 
        sensedEdges[NORTH] = rc.senseTerrainTile(edges[NORTH]).getType();
        sensedEdges[EAST] = rc.senseTerrainTile(edges[EAST]).getType();
        sensedEdges[SOUTH] = rc.senseTerrainTile(edges[SOUTH]).getType();
        sensedEdges[WEST] = rc.senseTerrainTile(edges[WEST]).getType();
 
        total += (sensedEdges[NORTH] == TerrainTile.TerrainType.OFF_MAP) ? NORTH : 0;
        total += (sensedEdges[EAST]  == TerrainTile.TerrainType.OFF_MAP) ? EAST  : 0;
        total += (sensedEdges[SOUTH] == TerrainTile.TerrainType.OFF_MAP) ? SOUTH : 0;
        total += (sensedEdges[WEST]  == TerrainTile.TerrainType.OFF_MAP) ? WEST  : 0;
 
        switch(total) {
            case NORTH:         mapBoundry = Direction.NORTH;       break;
            case EAST:          mapBoundry = Direction.EAST;        break;
            case SOUTH:         mapBoundry = Direction.SOUTH;       break;
            case WEST:          mapBoundry = Direction.WEST;        break;
            case NORTH+EAST:    mapBoundry = Direction.NORTH_EAST;  break;
            case NORTH+WEST:    mapBoundry = Direction.NORTH_WEST;  break;
            case SOUTH+EAST:    mapBoundry = Direction.SOUTH_EAST;  break;
            case SOUTH+WEST:    mapBoundry = Direction.SOUTH_WEST;  break;
            //default:            mapBoundry = null;
        }
    }

    public void yield() {
        rc.yield();
        rc.setIndicatorString(0, "AR: " + enemyArchonsKilled);
        //rc.setIndicatorString(0, "Dir: " + rc.getDirection());
    }
}
