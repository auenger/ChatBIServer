-- DataWiki 的结构镜像与业务语义只保存在控制库；发布快照不可变。
CREATE TABLE meper_metadata_object (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 datasource_id BIGINT NOT NULL,
 namespace_name VARCHAR(255) COLLATE utf8mb4_bin NOT NULL,
 object_name VARCHAR(255) COLLATE utf8mb4_bin NOT NULL,
 object_type VARCHAR(32) NOT NULL,
 original_remarks TEXT NULL,
 structure_json MEDIUMTEXT NOT NULL,
 fingerprint VARCHAR(64) NOT NULL,
 semantics_json MEDIUMTEXT NOT NULL,
 state VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
 synced_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE KEY uq_wiki_object (datasource_id, namespace_name, object_name),
 CONSTRAINT fk_wiki_object_ds FOREIGN KEY (datasource_id) REFERENCES meper_datasource_profile(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE meper_metadata_column (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 object_id BIGINT NOT NULL,
 column_name VARCHAR(255) COLLATE utf8mb4_bin NOT NULL,
 ordinal_position INT NOT NULL,
 structure_json TEXT NOT NULL,
 semantics_json MEDIUMTEXT NOT NULL,
 state VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
 UNIQUE KEY uq_wiki_column(object_id,column_name),
 CONSTRAINT fk_wiki_column_object FOREIGN KEY (object_id) REFERENCES meper_metadata_object(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE meper_wiki_page (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 datasource_id BIGINT NOT NULL,
 object_id BIGINT NULL,
 page_type VARCHAR(32) NOT NULL,
 path VARCHAR(700) COLLATE utf8mb4_bin NOT NULL,
 title VARCHAR(255) NOT NULL,
 draft_content MEDIUMTEXT NOT NULL,
 draft_version BIGINT NOT NULL DEFAULT 1,
 published_revision BIGINT NOT NULL DEFAULT 0,
 dirty TINYINT(1) NOT NULL DEFAULT 1,
 needs_review TINYINT(1) NOT NULL DEFAULT 0,
 disabled TINYINT(1) NOT NULL DEFAULT 0,
 updated_by VARCHAR(128) NOT NULL,
 updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 UNIQUE KEY uq_wiki_path(datasource_id,path),
 UNIQUE KEY uq_wiki_table(object_id),
 CONSTRAINT fk_wiki_page_ds FOREIGN KEY(datasource_id) REFERENCES meper_datasource_profile(id) ON DELETE CASCADE,
 CONSTRAINT fk_wiki_page_object FOREIGN KEY(object_id) REFERENCES meper_metadata_object(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE meper_wiki_revision (
 page_id BIGINT NOT NULL,
 revision BIGINT NOT NULL,
 title VARCHAR(255) NOT NULL,
 content MEDIUMTEXT NOT NULL,
 bindings_json MEDIUMTEXT NOT NULL,
 edit_state_json MEDIUMTEXT NOT NULL,
 published_by VARCHAR(128) NOT NULL,
 published_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 PRIMARY KEY(page_id,revision),
 CONSTRAINT fk_wiki_revision_page FOREIGN KEY(page_id) REFERENCES meper_wiki_page(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE meper_wiki_resource_binding (
 page_id BIGINT NOT NULL,
 object_id BIGINT NOT NULL,
 column_name VARCHAR(255) COLLATE utf8mb4_bin NOT NULL DEFAULT '',
 PRIMARY KEY(page_id,object_id,column_name),
 CONSTRAINT fk_wiki_binding_page FOREIGN KEY(page_id) REFERENCES meper_wiki_page(id) ON DELETE CASCADE,
 CONSTRAINT fk_wiki_binding_object FOREIGN KEY(object_id) REFERENCES meper_metadata_object(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE meper_metadata_sync_run (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 datasource_id BIGINT NOT NULL,
 namespace_name VARCHAR(255) NOT NULL,
 target_table VARCHAR(255) NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'RUNNING',
 detail MEDIUMTEXT NULL,
 created_by VARCHAR(128) NOT NULL,
 started_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 finished_at DATETIME(6) NULL,
 CONSTRAINT fk_wiki_sync_ds FOREIGN KEY(datasource_id) REFERENCES meper_datasource_profile(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
