package com.izylife.izykube.model;

import com.izylife.izykube.enums.AssetType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Data
@EqualsAndHashCode(callSuper = true)
@Document(collection = "assets")
public class Asset extends BaseEntity implements Persistable<String> {

    private String name;
    @Field("type")
    private AssetType type;
    private String yaml;
    private String version;
    private String description;
    private String image;
    private int port;

    @Transient
    private boolean persisted = false;

    @Override
    public boolean isNew() {
        return !persisted;
    }

    @Override
    public void setId(String id) {
        super.setId(id);
        this.persisted = true;
    }


}
