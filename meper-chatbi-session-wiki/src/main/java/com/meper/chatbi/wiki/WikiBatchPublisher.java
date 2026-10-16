package com.meper.chatbi.wiki;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import com.meper.chatbi.wiki.WikiModels.PublishItem;
/** 批量发布以每项为独立原子单元，不吞掉失败结果。 */
@Service
public class WikiBatchPublisher {
 private final WikiService wiki;
 public WikiBatchPublisher(@Lazy WikiService wiki){this.wiki=wiki;}
 public void publish(String principal,long space,PublishItem item){wiki.publish(principal,space,item.pageId(),item.expectedVersion());}
}
