package backend.tbm;

import backend.dm.DataManager;
import backend.parser.Parser;
import backend.parser.statement.*;
import backend.server.Executor;
import backend.tm.TransactionManager;
import backend.tm.TransactionManagerImpl;
import backend.vm.VersionManager;

import java.nio.charset.StandardCharsets;

public class test {
    public static void main(String[] args) throws Exception {
        TransactionManagerImpl tm= TransactionManager.create("cun/tm");
        DataManager dm=DataManager.create("cun/dm",1 << 20,tm);
        VersionManager vm=VersionManager.newVersionManager(tm,dm);
        TableManager tbm=TableManager.create("cun/",vm,dm);
        //开启事务
        BeginRes br=tbm.begin(new Begin());
        long xid=br.xid;
        //建立一张新表
        String ss="create table students " +
                "name string,age int32 " +
                "(index name age)";
        byte b[]=ss.getBytes(StandardCharsets.UTF_8);
        Object stat = Parser.Parse(b);
        tbm.create(xid,(Create) stat);
        System.out.println("===测试插入操作===");
        ss="insert into students values xiaohong 18";
        b=ss.getBytes(StandardCharsets.UTF_8);
        stat = Parser.Parse(b);
        tbm.insert(xid,(Insert) stat);
//        System.out.println("===测试更新操作===");
//        ss="update students set name = \"ZYJ\" where name = xiaohong";
//        b=ss.getBytes(StandardCharsets.UTF_8);
//        stat = Parser.Parse(b);
//        tbm.update(xid,(Update) stat);
//        System.out.println("===测试查询操作===");
//        ss="select name,age from students where age=18";
//        b=ss.getBytes(StandardCharsets.UTF_8);
//        stat = Parser.Parse(b);
//        byte[] output=tbm.read(xid,(Select) stat);
//        System.out.println(new String(output));
//        System.out.println("===测试删除操作===");
//        ss=" delete from students where age =18";
//        b=ss.getBytes(StandardCharsets.UTF_8);
//        stat = Parser.Parse(b);
//        tbm.delete(xid,(Delete) stat);
    }
}
