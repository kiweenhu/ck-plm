package cn.ck.plm.document.mapper;  
  
import cn.ck.plm.document.entity.Document;  
  
import java.util.List;  
  
/**  
 * Document 主对象数据访问接口，定义数据库无关的持久化契约。  
 */  
public interface DocumentMapper {  
  
    int insert(Document document);  
  
    int update(Document document);  
  
    int deleteByOid(String oid);  
  
    Document selectByOid(String oid);  
  
    List<Document> selectByContainerOid(String containerOid);  
  
    List<Document> selectByContainerAndStage(String containerOid, String stageOid);  
  
    List<Document> selectByFolderOid(String folderOid);  
  
    List<Document> selectAll();  
} 
