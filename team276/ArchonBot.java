package team276;

import battlecode.common.*;

public class ArchonBot extends Bot {
    private static int[][] fullScanOffs = {
        {  0,  0 }, { -6,  0 }, {  0,  6 }, {  6,  0 }, {  0, -6 }, { -3,  5 }, { -2,  5 }, { -1,  5 }, {  0,  5 }, {  1,  5 },
        {  2,  5 }, {  3,  5 }, {  5,  3 }, {  5,  2 }, {  5,  1 }, {  5,  0 }, {  5, -1 }, {  5, -2 }, {  5, -3 }, {  3, -5 },
        {  2, -5 }, {  1, -5 }, {  0, -5 }, { -1, -5 }, { -2, -5 }, { -3, -5 }, { -5, -3 }, { -5, -2 }, { -5, -1 }, { -5,  0 },
        { -5,  1 }, { -5,  2 }, { -5,  3 }, { -4,  4 }, { -3,  4 }, { -2,  4 }, { -1,  4 }, {  0,  4 }, {  1,  4 }, {  2,  4 },
        {  3,  4 }, {  4,  4 }, {  4,  3 }, {  4,  2 }, {  4,  1 }, {  4,  0 }, {  4, -1 }, {  4, -2 }, {  4, -3 }, {  4, -4 },
        {  3, -4 }, {  2, -4 }, {  1, -4 }, {  0, -4 }, { -1, -4 }, { -2, -4 }, { -3, -4 }, { -4, -4 }, { -4, -3 }, { -4, -2 },
        { -4, -1 }, { -4,  0 }, { -4,  1 }, { -4,  2 }, { -4,  3 }, { -3,  3 }, { -2,  3 }, { -1,  3 }, {  0,  3 }, {  1,  3 },
        {  2,  3 }, {  3,  3 }, {  3,  2 }, {  3,  1 }, {  3,  0 }, {  3, -1 }, {  3, -2 }, {  3, -3 }, {  2, -3 }, {  1, -3 },
        {  0, -3 }, { -1, -3 }, { -2, -3 }, { -3, -3 }, { -3, -2 }, { -3, -1 }, { -3,  0 }, { -3,  1 }, { -3,  2 }, { -2,  2 },
        { -1,  2 }, {  0,  2 }, {  1,  2 }, {  2,  2 }, {  2,  1 }, {  2,  0 }, {  2, -1 }, {  2, -2 }, {  1, -2 }, {  0, -2 },
        { -1, -2 }, { -2, -2 }, { -2, -1 }, { -2,  0 }, { -2,  1 }, { -1,  1 }, {  0,  1 }, {  1,  1 }, {  1,  0 }, {  1, -1 },
        {  0, -1 }, { -1, -1 }, { -1,  0 }
    };

    public ArchonBot(RobotController rc, Team t) {
        super(rc,t);
    }

    public void AI() throws Exception{
        sensorScan(fullScanOffs);
        Debugger.debug_print_total_bc_used();
        yield();

        while(true){
            Debugger.debug_set_counter(this);
            Debugger.debug_print("I'm an Archon!");
            Debugger.debug_print_energon(this.rc);
            Debugger.debug_print_counter(this);
            //Handle communication here.
            Debugger.debug_print_total_bc_used();
            rc.yield();
        }
    }
}
