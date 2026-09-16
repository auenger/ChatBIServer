package com.meper.chatbi.spi;

/** SQL 语句类别（分类器输出，用于 preview 展示与历史记录）。 */
public enum SqlCategory {

    /** 查询。 */
    SELECT,

    /** 数据变更：INSERT / UPDATE / DELETE / MERGE 等。 */
    DML,

    /** 对象定义：CREATE / ALTER / DROP / TRUNCATE 等。 */
    DDL,

    /** 事务控制：BEGIN / COMMIT / ROLLBACK / SAVEPOINT。 */
    TCL,

    /** 无法归类（USE、SHOW、SET、存储过程调用等方言语句）。 */
    OTHER
}
