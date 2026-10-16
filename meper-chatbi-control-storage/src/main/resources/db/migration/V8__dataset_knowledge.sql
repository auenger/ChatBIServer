-- 数据集、知识空间与默认模型；既有 V7 镜像/发布数据保留。
CREATE TABLE meper_dataset (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 name VARCHAR(128) NOT NULL UNIQUE,
 description TEXT NULL,
 revision BIGINT NOT NULL DEFAULT 1,
 enabled TINYINT(1) NOT NULL DEFAULT 1,
 updated_by VARCHAR(128) NOT NULL,
 updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE meper_wiki_space (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 name VARCHAR(128) NOT NULL,
 datasource_id BIGINT NULL,
 dataset_id BIGINT NULL,
 is_base TINYINT(1) NOT NULL DEFAULT 0,
 created_by VARCHAR(128) NOT NULL,
 CONSTRAINT fk_space_ds FOREIGN KEY(datasource_id) REFERENCES meper_datasource_profile(id) ON DELETE CASCADE,
 CONSTRAINT fk_space_dataset FOREIGN KEY(dataset_id) REFERENCES meper_dataset(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO meper_wiki_space(name,datasource_id,is_base,created_by)
 SELECT CONCAT(LEFT(name,100),' Wiki'),id,1,created_by FROM meper_datasource_profile;
CREATE TABLE meper_datasource_wiki_default (
 datasource_id BIGINT NOT NULL PRIMARY KEY,
 space_id BIGINT NOT NULL,
 CONSTRAINT fk_default_wiki_ds FOREIGN KEY(datasource_id) REFERENCES meper_datasource_profile(id) ON DELETE CASCADE,
 CONSTRAINT fk_default_wiki_space FOREIGN KEY(space_id) REFERENCES meper_wiki_space(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO meper_datasource_wiki_default(datasource_id,space_id) SELECT datasource_id,id FROM meper_wiki_space WHERE datasource_id IS NOT NULL AND is_base=1;
CREATE TABLE meper_dataset_member (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 dataset_id BIGINT NOT NULL,
 datasource_id BIGINT NOT NULL,
 alias_name VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
 resources_json MEDIUMTEXT NOT NULL COMMENT '固定的 namespace/table 清单，不随同步静默扩大',
 wiki_space_id BIGINT NULL COMMENT 'NULL 继承该数据库默认知识空间',
 UNIQUE KEY uq_dataset_alias(dataset_id,alias_name),
 UNIQUE KEY uq_dataset_ds(dataset_id,datasource_id),
 CONSTRAINT fk_member_dataset FOREIGN KEY(dataset_id) REFERENCES meper_dataset(id) ON DELETE CASCADE,
 CONSTRAINT fk_member_ds FOREIGN KEY(datasource_id) REFERENCES meper_datasource_profile(id) ON DELETE CASCADE,
 CONSTRAINT fk_member_wiki FOREIGN KEY(wiki_space_id) REFERENCES meper_wiki_space(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
ALTER TABLE meper_wiki_page ADD COLUMN space_id BIGINT NULL;
UPDATE meper_wiki_page p JOIN meper_wiki_space s ON s.datasource_id=p.datasource_id AND s.is_base=1 SET p.space_id=s.id;
ALTER TABLE meper_wiki_page ADD INDEX idx_wiki_page_datasource(datasource_id), ADD INDEX idx_wiki_page_object(object_id);
ALTER TABLE meper_wiki_page MODIFY datasource_id BIGINT NULL, MODIFY space_id BIGINT NOT NULL,
 DROP INDEX uq_wiki_path, DROP INDEX uq_wiki_table,
 ADD UNIQUE KEY uq_wiki_space_path(space_id,path),
 ADD UNIQUE KEY uq_wiki_space_object(space_id,object_id,page_type),
 ADD CONSTRAINT fk_page_space FOREIGN KEY(space_id) REFERENCES meper_wiki_space(id) ON DELETE CASCADE;
CREATE TABLE meper_model_default (
 singleton_id INT NOT NULL PRIMARY KEY,
 provider_id BIGINT NULL,
 CONSTRAINT fk_model_default FOREIGN KEY(provider_id) REFERENCES meper_model_provider(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO meper_model_default(singleton_id,provider_id) VALUES(1,NULL);
CREATE TABLE meper_chat_scope (
 session_id VARCHAR(64) NOT NULL PRIMARY KEY,
 subject VARCHAR(128) NOT NULL,
 scope_type VARCHAR(16) NOT NULL,
 scope_id BIGINT NOT NULL,
 scope_version VARCHAR(64) NOT NULL,
 provider_id BIGINT NOT NULL,
 namespace_name VARCHAR(255) NULL,
 mode VARCHAR(16) NOT NULL DEFAULT 'ANALYSIS',
 space_id BIGINT NULL,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE meper_wiki_edit_origin (
 page_id BIGINT NOT NULL,
 column_name VARCHAR(255) COLLATE utf8mb4_bin NOT NULL DEFAULT '',
 semantic_key VARCHAR(64) NOT NULL,
 origin VARCHAR(32) NOT NULL COMMENT 'USER_CONFIRMED / AI_SUGGESTED',
 evidence TEXT NULL,
 session_id VARCHAR(64) NOT NULL,
 updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 PRIMARY KEY(page_id,column_name,semantic_key),
 CONSTRAINT fk_wiki_origin FOREIGN KEY(page_id) REFERENCES meper_wiki_page(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 无人工语义的旧表页无需等待业务发布；结构版本由下一次同步自动生成。
UPDATE meper_wiki_page p JOIN meper_metadata_object o ON o.id=p.object_id
 SET p.dirty=0,p.needs_review=0 WHERE p.page_type='TABLE' AND p.published_revision=0 AND p.draft_content='' AND o.semantics_json='{}'
 AND NOT EXISTS (SELECT 1 FROM meper_metadata_column c WHERE c.object_id=o.id AND c.semantics_json<>'{}');
