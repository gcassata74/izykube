/*
 * IzyKube
 * Copyright (c) 2026-present Izylife Solutions s.r.l.
 * Author: Giuseppe Cassata
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

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

