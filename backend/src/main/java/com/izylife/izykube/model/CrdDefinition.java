package com.izylife.izykube.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@Document(collection = "crds")
public class CrdDefinition extends BaseEntity implements Persistable<String> {

    /**
     * spec.group
     */
    private String group;

    /**
     * spec.names.singular (input field)
     */
    private String singularName;

    /**
     * spec.scope (Namespaced|Cluster)
     */
    private String scope;

    /**
     * spec.versions[0].name
     */
    private String version = "v1";

    /**
     * Schema fields under spec (openAPIV3Schema.properties.spec.properties)
     */
    private List<CrdSchemaField> schemaFields = new ArrayList<>();

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
