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

package com.izylife.izykube.dto.ai;

import java.util.List;

public class AiChatResponse {

    private final List<AiChatMessage> messages;
    private final String model;
    private final String task;

    public AiChatResponse(List<AiChatMessage> messages, String model, String task) {
        this.messages = messages;
        this.model = model;
        this.task = task;
    }

    public List<AiChatMessage> getMessages() {
        return messages;
    }

    public String getModel() {
        return model;
    }

    public String getTask() {
        return task;
    }
}
