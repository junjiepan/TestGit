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
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class test01 {

    private final PropertiesInit Values;

    // 每个实体对应一个Map，实体 sql 语句映射对应的 INSERT 语句（一对多关系）
    private Map<String, String> entityInsertMap;

    public test01(PropertiesInit initializer) {
        this.Values = initializer;
        this.entityInsertMap = new LinkedHashMap<>();
    }

    // 自动生成资产下实体的固化文件
    public void generateInsertScript() throws SQLException {
        // 获取数据库连接
        Connection conn = getDatabaseConnection();

        // 资产编号
        String assetId = Values.getAssetId();

        // 获取所有实体的PHY_ID
        List<String> phyIds = getPhyIds(conn, assetId);

        // 创建 asset 文件夹
        File file = new File(Values.getOutputFilePath(), "entity");
        if (file.mkdirs()) {
            System.out.println("entity 文件夹创建成功！");
        } else {
            System.out.println("entity 文件夹创建失败！");
        }

        // 遍历每个实体编号，生成对应的 INSERT 语句, 并写入对应的 sql 文件
        for (String phyId : phyIds) {
            // 判断是否是新建状态的实体，如果是新建状态，不需要导出
            if (isNewState(conn, phyId) == false) {
                // 遍历 entity 配置的每个 SQL 语句，生成对应的 INSERT 语句
                for (String entityQuery : Values.getEntityQueries()) {
                    String querySql = generateQuerySql(entityQuery, phyId, assetId);
                    String insertTableName = getInsertTableName(entityQuery);
                    String insertSql = generateInsertSql(insertTableName, querySql, conn);
                    entityInsertMap.put(entityQuery, insertSql);
                }
                // 获取实体名称，作为输出文件名
                String entityName = getEntityName(conn, phyId);
                String outputDir = Values.getOutputFilePath() + "entity";
                String outputFileName = outputDir + File.separator + entityName + ".sql";
                // 将 INSERT 语句写入对应的文件中
                writeInsertSqlToFile(entityInsertMap, outputFileName);
                entityInsertMap.clear();
            }
        }
        System.out.println("entityFile create successfully!");
        conn.close();
    }

    // 获取 sql 语句对应生成的INSERT语句插入的表名
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

    // 将 INSERT 语句写入对应文件中
    private void writeInsertSqlToFile(Map<String, String> entityInsertMap, String outputFileName) {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(outputFileName))) {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> entry : entityInsertMap.entrySet()) {
                sb.append(entry.getValue() + "\n");
            }
            bw.write(sb.toString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // 通过 sql 语句与待插入的表名，生成 INSERT 语句
    private String generateInsertSql(String insertTableName, String querySql, Connection conn) throws SQLException {
        // 可能会处理多条结果，并生成 INSERT 语句
        String insertSql = "INSERT INTO " + insertTableName + " (" + String.join(",", getColumns(insertTableName, conn)) + ") VALUES (";
        // 获取所有的数据行
        List<List<Object>> rows = getValues(querySql, conn);

        StringBuilder sb = new StringBuilder();
        // 处理并生成若干 INSERT 语句
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

    // 获取列名
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

    // 执行语句，获取数据行
    private List<List<Object>> getValues(String querySql, Connection conn) throws SQLException {
        // 保存结果集
        List<List<Object>> results = new ArrayList();

        // 解析查询语句，获取查询结果并生成 INSERT 语句中的 VALUES 部分
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
            // 添加到结果集中
            results.add(result);
        }

        // 关闭资源
        rs.close();
        statement.close();

        return results;
    }

    // 生成拼接实体ID后的 SQL 语句（待执行的sql语句）
    private String generateQuerySql(String querySql, String phyId, String assetId) {
        // 获取 sql 语句中 MDI_ENTITY_REPO 这张表的别名，然后进行拼接操作
        String Sql = null;
        String aliasRegex = "(?i)\\bMDI_ENTITY_REPO\\b\\s+([A-Za-z0-9_]+)";
        Pattern pattern = Pattern.compile(aliasRegex);
        Matcher matcher = pattern.matcher(querySql);
        if (matcher.find()) {
            String alias = matcher.group(1);
            Sql = querySql.replace("(:assetsId);", "('" + assetId + "')") + " AND " + alias + ".PHY_ID = '" + phyId + "'";
        }
        return Sql;
    }

    /**
     * 通过 phyId 获取实体名
     */
    private String getEntityName(Connection conn, String phyId) throws SQLException {
        String sql = "select ENTITY_NAME from MDI_ENTITY_INFO where ENTITY_IMAGE_ID " +
                "IN (select ENTITY_IMAGE_ID from MDI_ENTITY_REPO where PHY_ID = ?) LIMIT 1;";
        PreparedStatement statement = conn.prepareStatement(sql);
        statement.setString(1, phyId);
        ResultSet rs = statement.executeQuery();
        if (rs.next()) {
            return rs.getString("ENTITY_NAME");
        } else {
            throw new SQLException("无法获取实体名, PHY_ID: " + phyId);
        }
    }

    /**
     * 通过 assetId 获取所有实体的 PHY_ID
     */
    private static List<String> getPhyIds(Connection conn, String assetsId) throws SQLException {
        List<String> phyIds = new ArrayList<>();
        String sql = "SELECT phy_id FROM mdi_assets_entity_rel WHERE assets_id = ?";
        PreparedStatement statement = conn.prepareStatement(sql);
        statement.setString(1, assetsId);
        ResultSet rs = statement.executeQuery();
        while (rs.next()) {
            phyIds.add(rs.getString("phy_id"));
        }
        return phyIds;
    }

    // 判断该实体，状态情况是否是新建状态
    private boolean isNewState(Connection conn, String phyId) throws SQLException {
        String sql = "select ENTITY_STATUS  from MDI_ENTITY_INFO WHERE ENTITY_IMAGE_ID IN (select ENTITY_IMAGE_ID from MDI_ENTITY_REPO where PHY_ID = ?) LIMIT 1";
        PreparedStatement statement = conn.prepareStatement(sql);
        statement.setString(1, phyId);
        ResultSet rs = statement.executeQuery();
        if (rs.next()) {
            if ("1".equals(rs.getString("ENTITY_STATUS"))) {
                return true;
            } else {
                return false;
            }
        } else {
            throw new SQLException("无法获取实体的状态信息, PHY_ID: " + phyId);
        }
    }

    // 获取数据库连接
    private Connection getDatabaseConnection() throws SQLException {
        String url = Values.getDbUrl();
        String username = Values.getDbUser();
        String password = Values.getDbPassword();
        return DriverManager.getConnection(url, username, password);
    }
}