package team276;

import battlecode.common.*;

public class ArchonBot extends Bot {
    public ArchonBot(RobotController rc, Team t) {
        super(rc,t);
    }

    private void breakout() throws Exception {
        int dx = 0;
        int dy = 0;

        MapLocation[] alliedArchonLocs = rc.senseAlliedArchons();
        Direction dTmp;

        for (MapLocation i: alliedArchonLocs) {
            Direction tmp = currentLocation.directionTo(i);
            dx -= tmp.dx;
            dy -= tmp.dy;
        }

        dTmp = Util.coordToDirection(dx, dy);
        rc.setDirection(dTmp);

        if (dTmp == Direction.NORTH_EAST) {
            Message msg = new Message();
            msg.locations = new MapLocation[] { currentLocation };
            rc.broadcast(msg);
        }
    }

    public void AI() throws Exception {
        breakout();
        rc.yield();

        while (true) {
            Debugger.debug_print_total_bc_used();
            rc.yield();
        }
    }
}
