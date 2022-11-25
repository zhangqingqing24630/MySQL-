package backend.parser.statement;

public class Update {
    public String tableName;//要更新的表名
    public String fieldName;//字段名
    public String value;//新值
    public Where where;//范围
}
