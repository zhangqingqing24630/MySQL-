package backend.vm;

import backend.dm.DataManager;
import backend.tm.TransactionManager;
import backend.tm.TransactionManagerImpl;

public class skip {
    public static void main(String[] args) throws Exception {
        TransactionManagerImpl tm= TransactionManager.create("cun/tm");
        DataManager dm=DataManager.create("cun/dm",1 << 20,tm);
        VersionManager vm=VersionManager.newVersionManager(tm,dm);
        byte b[]=new byte[1122];
        byte b_update[]=new byte[1122];

        long uid1=vm.insert(0,b);
        long xid1=vm.begin(1);
        System.out.println("开启事务"+xid1);
        long xid2=vm.begin(1);
        System.out.println("开启事务"+xid2);
        //xid1将x更新到x1
        vm.delete(xid1,uid1);
        long uid2=vm.insert(xid1,b_update);
        vm.commit(xid1);
        System.out.println("======");
        //xid1将x更新到x2
        vm.delete(xid2,uid1);
        long uid3=vm.insert(xid2,b_update);
        vm.commit(xid2);
    }
}
