package backend.dm.page;

import backend.dm.pageCache.PageCache;
import utils.RandomUtil;

import java.util.Arrays;
//数据库文件的第一页，通常用作一些特殊用途，比如存储一些元数据，用来启动检查什么的。
// MYDB 的第一页，只是用来做启动检查。具体的原理是，
// 在每次数据库启动时，会生成一串随机字节，存储在 100 ~ 107 字节。
// 在数据库正常关闭时，会将这串字节，拷贝到第一页的 108 ~ 115 字节。
//这样数据库在每次启动时，就会检查第一页两处的字节是否相同，
// 以此来判断上一次是否正常关闭。如果是异常关闭，就需要执行数据的恢复流程。
/**
 * 特殊管理第一页
 * ValidCheck
 * db启动时给100~107字节处填入一个随机字节，db关闭时将其拷贝到108~115字节
 * 用于判断上一次数据库是否正常关闭
 */
public class PageOne {
    private static final int OF_VC = 100;
    private static final int LEN_VC = 8;

    public static byte[] InitRaw() {
        byte[] raw = new byte[PageCache.PAGE_SIZE];
        setVcOpen(raw);
        return raw;
    }
//启动时设置初始字节：
    public static void setVcOpen(Page pg) {
        pg.setDirty(true);//对页面有修改时就设置为脏数据，然后flush到磁盘中
        setVcOpen(pg.getData());
    }
    //数据库启动时，在100-108设置随机字节
    private static void setVcOpen(byte[] raw) {
        //LEN_VC 8  OF_VC 100
        System.arraycopy(RandomUtil.randomBytes(LEN_VC), 0, raw, OF_VC, LEN_VC);
    }

//关闭时拷贝字节
    public static void setVcClose(Page pg) {
        pg.setDirty(true);
        setVcClose(pg.getData());
    }
    private static void setVcClose(byte[] raw) {
        System.arraycopy(raw, OF_VC, raw, OF_VC+LEN_VC, LEN_VC);
    }
//校验字节：
    public static boolean checkVc(Page pg) {
        return checkVc(pg.getData());
    }

    private static boolean checkVc(byte[] raw) {
//        System.out.println(Arrays.copyOfRange(raw, OF_VC, OF_VC+LEN_VC));
//        System.out.println(Arrays.copyOfRange(raw, OF_VC+LEN_VC, OF_VC+2*LEN_VC));
//        int a[]={1,2};
//        int b[]={1};
//        System.out.println(Arrays.equals(Arrays.copyOfRange(a,0,1),b));
        return Arrays.equals(Arrays.copyOfRange(raw, OF_VC, OF_VC+LEN_VC), Arrays.copyOfRange(raw, OF_VC+LEN_VC, OF_VC+2*LEN_VC));
    }
}

