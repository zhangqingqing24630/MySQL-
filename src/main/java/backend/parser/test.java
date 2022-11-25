package backend.parser;

import backend.parser.Tokenizer;

import java.nio.charset.StandardCharsets;
import utils.Parser;
public class test {
    public static void main(String[] args) throws Exception {
        String ss=" insert into student values 5 \"xiaohong\" 22";
        byte[]b=ss.getBytes(StandardCharsets.UTF_8);
        Tokenizer tokenizer = new Tokenizer(b);
        for(int i=0;i<b.length;i++){
            String token = tokenizer.peek();
            System.out.println(token);
            tokenizer.pop();
        }
    }
}
