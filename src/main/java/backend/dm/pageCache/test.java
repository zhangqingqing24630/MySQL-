package backend.dm.pageCache;

import backend.dm.page.Page;
import backend.dm.page.PageOne;
import backend.dm.page.PageX;

public class test {
    public static void main(String[] args) throws Exception {
//        //初始化给了16页
        PageCacheImpl pc=PageCache.open("cun/page",1 << 17);
//        //新建一页，写数据,返回页号
//        byte a[]=new byte[10];
//        int pago=pc.newPage(a);
//        //截断该页
//        pc.truncateByBgno(pago);
//        System.out.println(pc.getPageNumber());
//        //从缓存中拿第2页
//        Page pg=pc.getPage(2);
//        pg.setDirty(true);
//        //缓存中引用-1，引用为0时，调用releaseForCache（）回源
//        pg.release();
//        //从数据库中拿第2页
//        pg=pc.getForCache(2);
//        //不管引用是否为0，强制回源，且关闭dm文件
//        pc.close();


        //设置第一页校验页
//        Page pagOne=pc.getForCache(1);
//        PageOne.setVcOpen(pagOne);
//        PageOne.setVcClose(pagOne);
//        boolean flag=PageOne.checkVc(pagOne);
//        System.out.println("第一页校验结果"+flag);

        byte a[]=new byte[10];
        int pagX1=pc.newPage(a);
        System.out.println("写入该数据的页号"+pagX1);
        //从数据库中拿取一页普通页，继续填写
        Page pgx=pc.getPage(pagX1);
        byte[]b=new byte[128];
//
//        long offset=PageX.insert(pgx,b);
//        System.out.println("该页写入时的偏移量"+offset);
//        System.out.println("该页写完时的偏移量"+PageX.getFSO(pgx));
//

        //恢复插入
//        short offset=PageX.insert(pgx,b);//插入前的偏移量
//        System.out.println("该页恢复插入前的偏移量"+PageX.getFSO(pgx));
//        byte[]c=new byte[256];
//        PageX.recoverInsert(pgx,c,offset);
//        System.out.println("该页恢复插入后的偏移量"+PageX.getFSO(pgx));

        //恢复更新
        short offset=PageX.insert(pgx,b);//插入前的偏移量
        System.out.println("该页恢复更新前的偏移量"+PageX.getFSO(pgx));
        byte[]c=new byte[256];
        PageX.recoverUpdate(pgx,c,PageX.getFSO(pgx));
        System.out.println("该页恢复更新后的偏移量"+PageX.getFSO(pgx));
    }
}
