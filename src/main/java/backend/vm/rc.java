package backend.vm;

import backend.dm.DataManager;
import backend.tm.TransactionManager;
import backend.tm.TransactionManagerImpl;

public class rc {
    public static void main(String[] args) throws Exception {
        TransactionManagerImpl tm= TransactionManager.create("cun/tm");
        DataManager dm=DataManager.create("cun/dm",1 << 20,tm);
        VersionManager vm=VersionManager.newVersionManager(tm,dm);
        byte b[]=new byte[1122];
        long xid2=vm.begin(0);
        long xid1=vm.begin(0);
        long uid1=vm.insert(xid1,b);
        //vm.commit(xid1);
        byte[] output=vm.read(xid2,uid1);
        System.out.println(output);
    }
}
