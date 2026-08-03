package cn.ck.plm.base.typehandler;

import cn.ck.plm.base.entity.View;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * MyBatis TypeHandler：View 实体 &lt;--&gt; VARCHAR(code)。
 * <p>数据库中 view 列存储 View 的 code（如 Design/Manufacturing），
 * Java 侧为 View 对象，通过此 Handler 自动转换。</p>
 * <p>注意：通过 @MappedJdbcTypes(VARCHAR) 限制仅对 VARCHAR 列生效，
 * 避免干扰其他类型列的属性解析。</p>
 */
@MappedJdbcTypes(JdbcType.VARCHAR)
public class ViewTypeHandler extends BaseTypeHandler<View> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, View parameter, JdbcType jdbcType) throws SQLException {
        ps.setString(i, parameter.getCode());
    }

    @Override
    public View getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String code = rs.getString(columnName);
        return code == null ? null : newView(code);
    }

    @Override
    public View getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String code = rs.getString(columnIndex);
        return code == null ? null : newView(code);
    }

    @Override
    public View getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String code = cs.getString(columnIndex);
        return code == null ? null : newView(code);
    }

    private View newView(String code) {
        View v = new View();
        v.setCode(code);
        return v;
    }
}
