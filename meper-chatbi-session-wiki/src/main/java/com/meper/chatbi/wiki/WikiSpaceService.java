package com.meper.chatbi.wiki;
import com.meper.chatbi.storage.model.DatasetRecords.Space;
import com.meper.chatbi.storage.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** 默认知识绑定只改变显式指定数据库，不由数据集成员覆盖隐式修改全局设置。 */
@Service
public class WikiSpaceService {
 private final DatasetRepository repo;private final WikiService wiki;private final AuditRepository audit;
 public WikiSpaceService(DatasetRepository repo,WikiService wiki,AuditRepository audit){this.repo=repo;this.wiki=wiki;this.audit=audit;}
 public record SourceSpaces(List<Space> spaces,long defaultSpaceId,long baseSpaceId) { }
 public SourceSpaces source(String principal,long ds){long base=wiki.baseSpace(principal,ds);return new SourceSpaces(repo.spaces().stream().filter(s->Objects.equals(s.datasourceId(),ds)).toList(),wiki.defaultSpace(principal,ds),base);}
 @Transactional public Space create(String principal,long ds,String name){wiki.baseSpace(principal,ds);if(name==null||name.isBlank()||name.length()>128)throw new IllegalArgumentException("知识空间名称须为1至128字");return repo.space(repo.createSpace(name.trim(),ds,null,false,principal));}
 @Transactional public void setDefault(String principal,long ds,long space){wiki.baseSpace(principal,ds);if(!Objects.equals(repo.space(space).datasourceId(),ds))throw new IllegalArgumentException("默认知识空间必须属于当前数据库");repo.defaultSpace(ds,space);audit.insert("default",principal,"WIKI_DEFAULT_CHANGED","DATASOURCE",String.valueOf(ds),Map.of("spaceId",space));}
}
