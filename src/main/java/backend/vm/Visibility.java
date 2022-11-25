package backend.vm;

import backend.tm.TransactionManager;

public class Visibility {
//读提交是允许版本跳跃的，而可重复读则是不允许版本跳跃的。
// 解决版本跳跃的思路也很简单：如果 Ti 需要修改 X，而 X 已经被 Ti 不可见的事务 Tj 修改了，
// 那么要求 Ti 回滚。
    //上一节中就总结了，Ti 不可见的 Tj，有两种情况：
    //  XID(Tj) > XID(Ti)
    //  Tj in SP(Ti)
//版本跳跃的检查也就很简单了，取出要修改的数据 e 的最新提交版本，
// 并检查该最新版本的创建者对当前事务t是否可见
    public static boolean isVersionSkip(TransactionManager tm, Transaction t, Entry e) {
        long xmax = e.getXmax();
        if(t.level == 0) {
            return false;
        } else {
            return tm.isCommitted(xmax) && (xmax > t.xid || t.isInSnapshot(xmax));
        }
    }
    public static boolean isVisible(TransactionManager tm, Transaction t, Entry e) {
        if(t.level == 0) {
            return readCommitted(tm, t, e);
        } else {
            return repeatableRead(tm, t, e);
        }
    }
//判断某个记录对事务 t 是否可见
    //读已提交 判断是否对事务xid可见
    private static boolean readCommitted(TransactionManager tm, Transaction t, Entry e) {
        long xid = t.xid;
        long xmin = e.getXmin();
        long xmax = e.getXmax();
        if(xmin == xid && xmax == 0) return true;//事务由xid发起，且未结束

        if(tm.isCommitted(xmin)) {//事务已提交，但不是xid发起的
            if(xmax == 0) return true;//事务未结束
            if(xmax != xid) {
                if(!tm.isCommitted(xmax)) {
                    return true;
                }
            }
        }
        return false;
    }
//构造方法中的 active，保存着当前所有 active 的事务。
// 于是，可重复读的隔离级别下，一个版本是否对事务可见的判断如下：
    private static boolean repeatableRead(TransactionManager tm, Transaction t, Entry e) {
        long xid = t.xid;
        long xmin = e.getXmin();
        long xmax = e.getXmax();
        if(xmin == xid && xmax == 0) return true;

        if(tm.isCommitted(xmin) && xmin < xid && !t.isInSnapshot(xmin)) {
            if(xmax == 0) return true;
            if(xmax != xid) {
                if(!tm.isCommitted(xmax) || xmax > xid || t.isInSnapshot(xmax)) {
                    return true;
                }
            }
        }
        return false;
    }

}

