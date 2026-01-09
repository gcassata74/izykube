package com.izylife.izykube.services;

import com.izylife.izykube.enums.CrdFieldType;
import com.izylife.izykube.model.CrdDefinition;
import com.izylife.izykube.model.CrdSchemaField;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CrdYamlGeneratorTest {

    @Test
    void shouldGenerateRequiredPaths() {
        CrdDerivationService derivationService = new CrdDerivationService();
        CrdYamlGenerator generator = new CrdYamlGenerator(derivationService);

        CrdSchemaField replicas = new CrdSchemaField();
        replicas.setFieldName("replicas");
        replicas.setFieldType(CrdFieldType.NUMBER);

        CrdSchemaField image = new CrdSchemaField();
        image.setFieldName("image");
        image.setFieldType(CrdFieldType.STRING);

        CrdDefinition def = new CrdDefinition();
        def.setGroup("example.com");
        def.setSingularName("widget");
        def.setScope("Namespaced");
        def.setVersion("v1");
        def.setSchemaFields(List.of(replicas, image));

        String yaml = generator.generate(def);

        assertThat(yaml).contains("apiVersion: apiextensions.k8s.io/v1");
        assertThat(yaml).contains("kind: CustomResourceDefinition");
        assertThat(yaml).contains("metadata:");
        assertThat(yaml).contains("name: widgets.example.com");
        assertThat(yaml).contains("spec:");
        assertThat(yaml).contains("group: example.com");
        assertThat(yaml).contains("scope: Namespaced");
        assertThat(yaml).contains("versions:");
        assertThat(yaml).contains("name: v1");
        assertThat(yaml).contains("openAPIV3Schema:");
        assertThat(yaml).contains("properties:");
        assertThat(yaml).contains("spec:");
        assertThat(yaml).contains("replicas:");
        assertThat(yaml).contains("type: number");
        assertThat(yaml).contains("image:");
        assertThat(yaml).contains("type: string");
    }
}

