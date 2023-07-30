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
import java.util.List;

/**
 * @Description: 自动生成资产固化脚本文件
 * @Author: panjj
 * @Date: 2023/02/24 16:10
 */
@Component
public class assetDemo {
    private final PropertiesInit Values;

    public assetDemo(PropertiesInit initializer) {
        this.Values = initializer;
    }

    /**
     * 自动生成资产固化 INSERT 脚本文件
     */
    public void generateInsertScript() throws SQLException, IOException {
        // 资产编号
        String assetId = Values.getAssetId();

        // 建立数据库连接, 操作的库为 public_udmp
        Connection conn = DriverManager.getConnection(Values.getDbUrl(), Values.getDbUser(), Values.getDbPassword());
        conn.setAutoCommit(false);

        // 读取配置文件自定义 asset 查询语句
        List<String> queries = Values.getAssetQueries();

        // 执行所有语句, 分别存入语句对应结果集到 List 中 => finResult 的格式为: [[[val...], [val...]], [[val...], [val...]]]
        List<List<List<Object>>> finResult = new ArrayList<>();
        for (String query : queries) {
            List<List<Object>> results = executeQuery(query, assetId, conn);
            finResult.add(results);
        }

        // 将语句对应的结果集，生成对应的 INSERT 语句
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < queries.size(); i++) {
            String query = queries.get(i);
            List<List<Object>> rows = finResult.get(i);
            String tableName = getTableName(query);
            List<String> columnNames = getColumnNames(tableName, conn);
            // 根据表名、列名、数据, 自动拼接生成 INSERT 语句
            String insertStatement = generateInsertStatement(tableName, columnNames, rows);
            sb.append(insertStatement + "\n");
        }

        // 将该资产对应的全部 INSERT 语句写入目标文件

        // 创建 资产名 文件夹
        File assetFile = new File(Values.getOutputFilePath(), getAssetName(conn, assetId));
        if(assetFile.mkdirs()) {
            System.out.println("资产名 文件夹创建成功！");
        } else {
            System.out.println("资产名 文件夹已存在！");
        }

        // 在资产名文件夹下 创建 asset 文件夹
        File file = new File(Values.getOutputFilePath() +  getAssetName(conn, assetId)+ File.separator, "asset");
        if(file.mkdirs()) {
            System.out.println("asset 文件夹创建成功！");
        } else {
            System.out.println("asset 文件夹已存在！");
        }

        // 获取资产名
        String assetName = getAssetName(conn, assetId);
        String outputDir = Values.getOutputFilePath() + getAssetName(conn, assetId)+ File.separator +  "asset";
        String outputFileName = outputDir + File.separator + assetName + ".sql";
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(outputFileName))) {
            bw.write(sb.toString());
            System.out.println("assetFile create successfully!!");
        }

        // 关闭数据库连接
        conn.commit();
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
     * 执行 sql 语句, 返回结果集
     *
     * @param query   sql 语句
     * @param assetId 资产编号
     * @param conn    数据库连接
     * @return
     */
    private List<List<Object>> executeQuery(String query, String assetId, Connection conn) throws SQLException {
        // 保存结果集
        List<List<Object>> results = new ArrayList();

        // 处理并执行 sql 语句
        String sql = query.replaceAll(":assetsId", "?");
        PreparedStatement statement = conn.prepareStatement(sql);
        statement.setString(1, assetId);
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

    /**
     * 获取表名
     *
     * @param query sql 语句
     * @return
     */
    private String getTableName(String query) {
        // 将sql语句按照空格分割
        String[] splits = query.split("\\s+");
        for (int i = 0; i < splits.length - 1; i++) {
            // "from" 后面为目标表名
            if (splits[i].equalsIgnoreCase("from")) {
                return splits[i + 1];
            }
        }
        return null;
    }

    /**
     * 获取列名
     *
     * @param tableName 表名
     * @param conn      数据库连接
     * @return
     */
    private List<String> getColumnNames(String tableName, Connection conn) throws SQLException {
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
     * 根据表名列名与数据组拼接对应 INSERT 语句
     *
     * @param tableName   表名
     * @param columnNames 列名
     * @param rows        数据组
     * @return
     */
    private String generateInsertStatement(String tableName, List<String> columnNames, List<List<Object>> rows) {
        StringBuilder sb = new StringBuilder();

        // 拼接单条 SELECT 语句对应结果集中每一行数据的 INSERT 语句
        for (List<Object> row : rows) {
            sb.append("INSERT INTO public_udmp.")
                    .append(tableName)
                    .append(" (")
                    .append(String.join(",", columnNames))
                    .append(") VALUES (");
            // 根据列值类型分别处理
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
}