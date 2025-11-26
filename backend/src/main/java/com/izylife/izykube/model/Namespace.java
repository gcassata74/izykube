package com.izylife.izykube.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@EqualsAndHashCode(callSuper = true)
@Document(collection = "namespaces")
public class Namespace extends BaseEntity implements Persistable<String> {

    private String name;
    private String description;

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

    public String getName() {
        return name != null ? name.trim() : null;
    }
}
