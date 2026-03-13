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

package com.izylife.izykube.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

@Getter
public enum CrdFieldType {
    STRING("string"),
    NUMBER("number"),
    BOOLEAN("boolean"),
    OBJECT("object"),
    ARRAY("array");

    @JsonValue
    private final String value;

    CrdFieldType(String value) {
        this.value = value;
    }

    public static CrdFieldType fromValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("fieldType is required");
        }
        for (CrdFieldType type : values()) {
            if (type.value.equalsIgnoreCase(value.trim())) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown fieldType: " + value);
    }

    @Override
    public String toString() {
        return value;
    }
}

