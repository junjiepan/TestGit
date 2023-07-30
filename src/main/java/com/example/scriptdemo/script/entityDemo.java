package com.example.scriptdemo.script;

import com.example.scriptdemo.properties.PropertiesInit;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @Description: 自动生成实体固化脚本文件
 * @Author: panjj
 * @Date: 2023/03/02
 */
@Component
public class entityDemo {

    private final PropertiesInit Values;

    // 每个实体对应一个Map，实体 sql 语句映射对应的 INSERT 语句（一对多关系）
    private Map<String, String> entityInsertMap;

    public entityDemo(PropertiesInit initializer) {
        this.Values = initializer;
        this.entityInsertMap = new LinkedHashMap<>();
    }

    /**
     * 自动生成资产下的全部实体固化 INSERT 脚本文件
     *
     */
    public void generateInsertScript() throws SQLException, IOException {
        // 获取数据库连接
        Connection conn = getDatabaseConnection();

        // 资产编号
        String assetId = Values.getAssetId();

        // 获取所有实体的PHY_ID
        List<String> phyIds = getPhyIds(conn, assetId);

        // 创建 资产名 文件夹
        File assetFile = new File(Values.getOutputFilePath(), getAssetName(conn, assetId));
        if(assetFile.mkdirs()) {
            System.out.println("资产名 文件夹创建成功！");
        } else {
            System.out.println("资产名 文件夹已存在！");
        }

        // 在资产名文件夹下 创建 entity 文件夹
        File file = new File(Values.getOutputFilePath() +  getAssetName(conn, assetId) + File.separator, "entity");
        if (file.mkdirs()) {
            System.out.println("entity 文件夹创建成功！");
        } else {
            System.out.println("entity 文件夹已存在！");
        }

        // 遍历每个实体编号，生成对应的 INSERT 语句, 并写入对应的 sql 文件
        for (String phyId : phyIds) {
            // 判断是否是新建状态的实体，新建状态的实体不需要导出
            if (isNewStatus(conn, phyId) == false) {
                // 遍历 entity 配置的每个 SQL 语句，生成对应的 INSERT 语句
                for (String entityQuery : Values.getEntityQueries()) {
                    // 处理源语句，生成待执行sql语句
                    String executedSql = generateQuerySql(entityQuery, phyId, assetId);
                    // 获取待生成的 INSERT 语句中的待插入表名
                    String insertTableName = getInsertTableName(entityQuery);
                    // 生成 INSERT 语句，存入 map 中
                    String insertSql = generateInsertSql(insertTableName, executedSql, conn);
                    entityInsertMap.put(entityQuery, insertSql);
                }
                // 获取实体名称，作为输出文件的文件名
                String entityName = getEntityName(conn, phyId);
                String outputDir = Values.getOutputFilePath() + getAssetName(conn, assetId)+ File.separator + "entity";
                String outputFileName = outputDir + File.separator + entityName + ".sql";
                outputFileName = outputFileName.replace("*", "#");
                // 将 INSERT 语句写入对应的文件中
                writeInsertSqlToFile(entityInsertMap, outputFileName);
                entityInsertMap.clear();
            }
        }
        System.out.println("entityFile create successfully!");
        conn.close();
    }

    /**
     * 获取资产中文名
     *
     * @param conn   数据库连接
     * @param assetId 资产编号
     * @return
     */
    private String getAssetName(Connection conn, String assetId) throws SQLException {
        String sql = "select ASSETS_COMMENT from MDI_ASSETS_INFO where ASSETS_ID in (?) LIMIT 1";
        PreparedStatement statement = conn.prepareStatement(sql);
        statement.setString(1, assetId);
        ResultSet rs = statement.executeQuery();
        rs.next();
        return rs.getString(1);
    }

    /**
     * 获取 sql 语句对应需插入的表名
     *
     * @param entityQuery sql语句
     * @return
     */
    private String getInsertTableName(String entityQuery) {
        // 将sql语句按照空格分割
        String[] splits = entityQuery.split("\\s+");
        for (int i = 0; i < splits.length - 1; i++) {
            // "from" 后面为待插入目标表表名
            if (splits[i].equalsIgnoreCase("from")) {
                return splits[i + 1];
            }
        }
        return null;
    }

    /**
     * 将 INSERT 语句写入对应文件中
     *
     * @param entityInsertMap 实体 INSERT 语句映射集
     * @param outputFileName 输出文件路径名
     */
    private void writeInsertSqlToFile(Map<String, String> entityInsertMap, String outputFileName) throws IOException {
//        if(outputFileName.contains("*")){
//            String oldOutputFileName = outputFileName;
//            String newOutputFileName = outputFileName.replace("*", "NEW");
//            File file = new File(newOutputFileName);
//
//            if (file.createNewFile()) {
//                File newFile = new File(oldOutputFileName);
//                if (file.renameTo(newFile)) {
//                    System.out.println("文件重命名成功！");
//                } else {
//                    System.out.println("文件重命名失败！");
//                }
//            } else {
//                System.out.println("文件创建失败！");
//            }
//        }

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(outputFileName))) {
            StringBuilder sb = new StringBuilder();
            // 遍历 map
            for (Map.Entry<String, String> entry : entityInsertMap.entrySet()) {
                sb.append(entry.getValue() + "\n");
            }
            bw.write(sb.toString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 生成 INSERT 语句
     * @param insertTableName 表名
     * @param querySql sql语句
     * @param conn 数据库连接
     * @return
     */
    private String generateInsertSql(String insertTableName, String querySql, Connection conn) throws SQLException {
        // 可能会处理多条结果，并生成 INSERT 语句
        String insertSql = "INSERT INTO " + insertTableName + " (" + String.join(",", getColumns(insertTableName, conn)) + ") VALUES (";

        // 执行 SQL 语句, 获取结果集
        List<List<Object>> rows = getValues(querySql, conn);

        // 处理并生成若干 INSERT 语句
        StringBuilder sb = new StringBuilder();
        for (List<Object> row : rows) {
            sb.append(insertSql);
            // 根据列值类型分别进行处理
            for (int i = 0; i < row.size(); i++) {
                Object value = row.get(i);
                if (value == null) {
                    sb.append("null");
                } else if (value instanceof String) {
                    sb.append("'").append(value).append("'");
                } else if (value instanceof java.sql.Timestamp) {
                    // 特殊处理日期类型的列
                    DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    value = dateFormat.format(value);
                    sb.append("'").append(value).append("'");
                } else {
                    sb.append("'" + value.toString().replace("'", "''") + "'");
                }
                if (i != row.size() - 1) {
                    sb.append(",");
                }
            }
            sb.append(");\n");
        }
        return sb.toString();
    }

    /**
     * 获取该表对应的全部列名
     * @param tableName 表名
     * @param conn 数据库连接
     * @return
     */
    private List<String> getColumns(String tableName, Connection conn) throws SQLException {
        List<String> columnNames = new ArrayList();
        // 根据表名查询该表对应的所有列名
        String sql = "SELECT column_name FROM information_schema.columns WHERE table_schema = 'public_udmp' AND table_name = ?";
        PreparedStatement statement = conn.prepareStatement(sql);
        statement.setString(1, tableName);
        ResultSet rs = statement.executeQuery();
        while (rs.next()) {
            columnNames.add(rs.getString(1));
        }
        // 关闭资源
        rs.close();
        statement.close();
        return columnNames;
    }

    /**
     * 执行 SQL 语句, 返回结果集
     *
     * @param querySql 待执行sql语句
     * @param conn     数据库连接
     * @return
     */
    private List<List<Object>> getValues(String querySql, Connection conn) throws SQLException {
        // 保存结果集至 List
        List<List<Object>> results = new ArrayList();

        // 解析查询语句，获取查询结果
        PreparedStatement statement = conn.prepareStatement(querySql);
        ResultSet rs = statement.executeQuery();

        // 获取列数
        ResultSetMetaData rsmd = rs.getMetaData();
        int numColumns = rsmd.getColumnCount();

        while (rs.next()) {
            List<Object> result = new ArrayList();
            for (int i = 1; i <= numColumns; i++) {
                // 获取指定列名及该列名对应的值
                String columnName = rsmd.getColumnName(i);
                result.add(rs.getObject(columnName));
            }
            // 添加到结果集
            results.add(result);
        }

        // 关闭资源
        rs.close();
        statement.close();

        return results;
    }

    /**
     * 处理SQL语句，拼接实体ID
     *
     * @param querySql 原始sql语句
     * @param phyId    实体物理ID
     * @param assetId  资产编号
     * @return
     */
    private String generateQuerySql(String querySql, String phyId, String assetId) {
        // 获取原始 sql 语句中 MDI_ENTITY_REPO 这张表的别名，然后进行拼接操作
        String executedSql = null;
        String aliasRegex = "(?i)\\bMDI_ENTITY_REPO\\b\\s+([A-Za-z0-9_]+)";
        Pattern pattern = Pattern.compile(aliasRegex);
        Matcher matcher = pattern.matcher(querySql);
        if (matcher.find()) {
            String alias = matcher.group(1);
            executedSql = querySql.replace("(:assetsId);", "('" + assetId + "')") + " AND " + alias + ".PHY_ID = '" + phyId + "'";
        }
        return executedSql;
    }

    /**
     * 获取实体英文名
     *
     * @param conn  数据库连接
     * @param phyId 实体物理编号
     * @return
     */
    private String getEntityName(Connection conn, String phyId) throws SQLException {
        String entityName = null;
        String sql = "select ENTITY_NAME from MDI_ENTITY_INFO where ENTITY_IMAGE_ID " +
                "IN (select ENTITY_IMAGE_ID from MDI_ENTITY_REPO where PHY_ID = ?) LIMIT 1;";
        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setString(1, phyId);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    entityName = rs.getString("ENTITY_NAME");
                }
            }
        } catch (SQLException e) {
            System.err.println("获取 entityName 出现异常: " + e.getMessage());
            throw e;
        }
        return entityName;
    }

    /**
     * 获取所有实体的物理 ID
     *
     * @param conn     数据库连接
     * @param assetsId 资产编号
     * @return
     */
    private static List<String> getPhyIds(Connection conn, String assetsId) throws SQLException {
        List<String> phyIds = new ArrayList<>();
        String sql = "SELECT phy_id FROM mdi_assets_entity_rel WHERE assets_id = ?";
        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setString(1, assetsId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    phyIds.add(rs.getString("phy_id"));
                }
            }
        } catch (SQLException e) {
            System.err.println("获取 phy_id 出现异常: " + e.getMessage());
            throw e;
        }
        return phyIds;
    }

    /**
     * 判断实体是否是新建状态
     *
     * @param conn  数据库连接
     * @param phyId 实体物理编号
     * @return
     */
    private boolean isNewStatus(Connection conn, String phyId) throws SQLException {
        boolean flag = false;
        String sql = "select ENTITY_STATUS  from MDI_ENTITY_INFO WHERE ENTITY_IMAGE_ID IN " +
                "(select ENTITY_IMAGE_ID from MDI_ENTITY_REPO where PHY_ID = ?) LIMIT 1";
        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setString(1, phyId);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    // 实体状态为"新建"
                    if ("1".equals(rs.getString("ENTITY_STATUS"))) {
                        flag = true;
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("获取实体的状态信息出现异常: " + e.getMessage());
            throw e;
        }
        return flag;
    }

    /**
     * 获取数据库连接
     *
     * @return
     */
    private Connection getDatabaseConnection() throws SQLException {
        String url = Values.getDbUrl();
        String username = Values.getDbUser();
        String password = Values.getDbPassword();
        return DriverManager.getConnection(url, username, password);
    }

}
