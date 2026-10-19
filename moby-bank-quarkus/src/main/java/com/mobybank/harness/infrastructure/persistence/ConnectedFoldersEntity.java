package com.mobybank.harness.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** The stored form of a user's {@code ConnectedFolders}: the folder ids in the order they were connected. */
@Entity
@Table(name = "connected_folders")
public class ConnectedFoldersEntity {

    @Id
    public String userId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    public List<String> folderIds = new ArrayList<>();

    @Column(nullable = false)
    public long version;
}
