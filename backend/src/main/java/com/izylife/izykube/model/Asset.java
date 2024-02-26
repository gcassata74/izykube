package com.izylife.izykube.model;

import lombok.Data;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "assets")
public class Asset extends BaseEntity implements Persistable<String> {

    private String name;
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
