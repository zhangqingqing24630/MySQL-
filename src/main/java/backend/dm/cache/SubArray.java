package backend.dm.cache;
//共享内存数组
//将数组看作一个对象，在内存中，也是以对象的形式存储的。而 c、cpp 和 go 之类的语言，数组是用指针来实现的
//在 Java 中，当你执行类似 subArray 的操作时，只会在底层进行一个复制，无法同一片内存。
//
//于是，我写了一个 SubArray 类，来（松散地）规定这个数组的可使用范围：
public class SubArray {
    public byte[] raw;//有效位 data大小 data
    public int start;
    public int end;

    public SubArray(byte[] raw, int start, int end) {
        this.raw = raw;
        this.start = start;
        this.end = end;
    }
}
