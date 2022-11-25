package backend.parser;
import utils.Error;
public class Tokenizer {
    private byte[] stat;//sql语句字节数组
    private int pos;
    private String currentToken;
    private boolean flushToken;
    private Exception err;
//parser 包的 Tokenizer 类，对语句进行逐字节解析，
// 根据空白符或者上述词法规则，将语句切割成多个 token。对外提供了 peek()、pop() 方法
// 方便取出 Token 进行解析
    public Tokenizer(byte[] stat) {
        this.stat = stat;
        this.pos = 0;
        this.currentToken = "";
        this.flushToken = true;
    }

    public String peek() throws Exception {
        if(err != null) {
            throw err;
        }
        if(flushToken) {
            String token = null;
            try {
                token = next();
            } catch(Exception e) {
                err = e;
                throw e;
            }
            currentToken = token;
            flushToken = false;
        }
        return currentToken;
    }

    public void pop() {
        flushToken = true;
    }

    public byte[] errStat() {
        byte[] res = new byte[stat.length+3];
        System.arraycopy(stat, 0, res, 0, pos);
        System.arraycopy("<< ".getBytes(), 0, res, pos, 3);
        System.arraycopy(stat, pos, res, pos+3, stat.length-pos);
        return res;
    }

    private void popByte() {
        pos ++;
        if(pos > stat.length) {
            pos = stat.length;
        }
    }

    private Byte peekByte() {
        if(pos == stat.length) {
            return null;
        }
        return stat[pos];
    }

    private String next() throws Exception {
        if(err != null) {
            throw err;
        }
        return nextMetaState();
    }

    private String nextMetaState() throws Exception {
        while(true) {
            //遍历字节数组，直至遍历完，取出
            Byte b = peekByte();
            if(b == null) {
                return "";
            }
            //遇到空格或回车，忽略，继续遍历
            if(!isBlank(b)) {
                break;
            }
            popByte();//pos++;
        }
        byte b = peekByte();
        //如果b是<>=*,这些符号，取出符号
        if(isSymbol(b)) {
            popByte();
            return new String(new byte[]{b});
        //如果b是单引号或者双引号，找到单引号双引号包起来的内容
        } else if(b == '"' || b == '\'') {
            return nextQuoteState();
        //如果b是数字或字母，找到完整的数字或字符串
        } else if(isAlphaBeta(b) || isDigit(b)) {
            return nextTokenState();
        } else {
            err = Error.InvalidCommandException;
            throw err;
        }
    }
    //如果遍历到是数字或字母，找到完整的字符串
    private String nextTokenState() throws Exception {
        StringBuilder sb = new StringBuilder();
        while(true) {
            Byte b = peekByte();
            //如果遍历结束或遍历到的不是字母或遍历到数字，或遍历到_，结束
            if(b == null || !(isAlphaBeta(b) || isDigit(b) || b == '_')) {
                //遍历到空格回车，忽略
                if(b != null && isBlank(b)) {
                    popByte();
                }
                return sb.toString();
            }
            sb.append(new String(new byte[]{b}));
            popByte();
        }
    }

    static boolean isDigit(byte b) {
        return (b >= '0' && b <= '9');
    }

    static boolean isAlphaBeta(byte b) {
        return ((b >= 'a' && b <= 'z') || (b >= 'A' && b <= 'Z'));
    }
    //找出单引号双引号包裹起来的内容
    private String nextQuoteState() throws Exception {
        byte quote = peekByte();//单引号或双引号
        popByte();
        StringBuilder sb = new StringBuilder();
        while(true) {
            Byte b = peekByte();
            if(b == null) {
                err = Error.InvalidCommandException;
                throw err;
            }
            if(b == quote) {//直到取到下一个单引号或双引号为止
                popByte();
                break;
            }
            sb.append(new String(new byte[]{b}));
            popByte();
        }
        return sb.toString();
    }

    static boolean isSymbol(byte b) {
        return (b == '>' || b == '<' || b == '=' || b == '*' ||
                b == ',' || b == '(' || b == ')');
    }

    static boolean isBlank(byte b) {
        return (b == '\n' || b == ' ' || b == '\t');
    }
}

