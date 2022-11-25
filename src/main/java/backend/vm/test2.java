package backend.vm;

import utils.Error;

import javax.swing.plaf.synth.SynthOptionPaneUI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class test2 {
    private static Map<Long, Integer> xidStamp;
    private static int stamp;
    private static HashMap<Long,List<Long>> x2u=new HashMap<Long, List<Long>>();
    private static HashMap<Long, Long> waitU=new HashMap<Long, Long>();
    private static HashMap<Long, Long> u2x=new HashMap<Long, Long>();
    public static void main(String[] args) throws Exception {
        LockTable lock=new LockTable();
        lock.add(1L,3L);
        lock.add(2L,4L);
        lock.add(3L,5L);
        lock.add(1L,4L);
//        lock.add(1L,4L);
        System.out.println("++++++++++++++++");
//        lock.add(3L,4L);
        lock.add(2L,5L);
        System.out.println("++++++++++++++++");
        lock.add(3L,3L);
//        lock.add(1L,3L);
//        lock.add(2L,4L);
//        lock.add(3L,3L);

        System.out.println(hasDeadLock());
    }
    private static boolean hasDeadLock() {
        xidStamp = new HashMap<>();
        stamp = 1;
        //x2u  xid已获得资源的uid列表 xid list<uid>
        for(long xid : x2u.keySet()) {
            System.out.println("xid="+xid);
            Integer s = xidStamp.get(xid);
            if(s != null && s > 0) {
                continue;
            }
            stamp ++;

            if(dfs(xid)) {
                return true;
            }
        }
        return false;
    }

    private static boolean dfs(long xid) {
        Integer stp = xidStamp.get(xid);
        if(stp != null && stp == stamp) {
            return true;
        }
        if(stp != null && stp < stamp) {
            return false;
        }
        xidStamp.put(xid, stamp);

//        System.out.println("xidStamp="+xidStamp);
//        System.out.println("waitU="+waitU);
        Long uid = waitU.get(xid);
        if(uid == null) return false;
        Long x = u2x.get(uid);
        assert x != null;
        return dfs(x);
    }
}
